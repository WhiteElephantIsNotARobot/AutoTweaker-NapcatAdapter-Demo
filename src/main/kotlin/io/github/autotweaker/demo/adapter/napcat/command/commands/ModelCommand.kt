package io.github.autotweaker.demo.adapter.napcat.command.commands

import io.github.autotweaker.api.types.config.CoreConfig
import io.github.autotweaker.api.types.llm.ModelData
import io.github.autotweaker.api.trace.TraceRecorder
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.demo.adapter.napcat.command.Command
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.permission.Role
import java.util.UUID

/**
 * 模型管理命令
 *
 * 用户权限：
 *   /model - 查看当前配置
 *   /model list - 列出可用模型
 *   /model set <名称|序号> - 设置自己的主模型
 *
 * 操作员权限：
 *   /model create <提供商名称> <模型ID> <显示名称> - 创建模型
 *   /model delete <显示名称|序号> - 删除模型
 *   /model fallback add <名称|序号> - 添加全局备选模型
 *   /model fallback remove <名称|序号> - 移除全局备选模型
 *   /model summarize <名称|序号> - 设置全局压缩模型
 */
class ModelCommand : Command {

    private lateinit var trace: TraceRecorder

    override val name = "model"
    override val description = "管理模型配置"
    override val usage = "/model [list|create|delete|set|fallback|summarize] [参数]"
    override val requiredRole = Role.USER

    override suspend fun execute(context: CommandContext): String {
        if (!::trace.isInitialized) trace = context.core.trace(this::class)
        if (context.args.isEmpty()) {
            return showCurrentConfig(context)
        }

        return when (context.args[0].lowercase()) {
            "list", "ls" -> listModels(context)
            "create", "add" -> createModel(context)
            "delete", "rm", "remove" -> deleteModel(context)
            "set" -> setUserPrimaryModel(context)
            "fallback" -> handleFallback(context)
            "summarize" -> setSummarizeModel(context)
            else -> "未知子命令: ${context.args[0]}\n用法: $usage"
        }
    }

    private fun showCurrentConfig(context: CommandContext): String {
        val sm = context.sessionManager
        val models = context.core.config.listModels()

        fun modelName(id: UUID): String =
            models.find { it.data.id == id }?.data?.displayName ?: id.toString().take(8)

        val userPrimary = sm.getUserPrimaryModel(context.userId)
        val globalFallback = sm.getGlobalFallbackModels()
        val globalSummarize = sm.getGlobalSummarizeModel()

        return buildString {
            appendLine("当前模型配置:")
            appendLine("  我的主模型: ${if (userPrimary != null) modelName(userPrimary) else "未设置（使用系统默认）"}")
            if (globalFallback.isNotEmpty()) {
                appendLine("  备选模型: ${globalFallback.joinToString(", ") { modelName(it) }}")
            }
            if (globalSummarize != null) {
                appendLine("  压缩模型: ${modelName(globalSummarize)}")
            }
        }
    }

    private fun listModels(context: CommandContext): String {
        val models = context.core.config.listModels()
        if (models.isEmpty()) return "没有可用的模型"

        val userPrimary = context.sessionManager.getUserPrimaryModel(context.userId)

        return buildString {
            appendLine("可用模型:")
            models.forEachIndexed { index, model ->
                val marker = if (model.data.id == userPrimary) " ← 我的主模型" else ""
                val provider = model.data.providerId.let { pid ->
                    context.core.config.listProviders().find { it.id == pid }?.displayName
                } ?: "未知"
                appendLine("  ${index + 1}. [$provider] ${model.data.displayName}$marker")
            }
        }
    }

    private fun requireOperator(context: CommandContext): String? {
        val role = context.permissionManager.getRole(context.userId)
        return if (role == null || role.level < Role.OPERATOR.level) "需要操作员权限" else null
    }

    private suspend fun createModel(context: CommandContext): String {
        // 创建模型需要操作员权限
        requireOperator(context)?.let { return it }

        if (context.args.size < 4) {
            return "用法: /model create <提供商名称> <模型ID> <显示名称>"
        }

        val providerName = context.args[1]
        val modelId = context.args[2]
        val displayName = context.args[3]

        // 查找提供商
        val provider = context.core.config.listProviders()
            .find { it.displayName.equals(providerName, ignoreCase = true) }
            ?: return "未找到提供商: $providerName"

        // 检查提供商类型是否已注册
        val availableTypes = context.core.config.listAvailableProviderTypes()
        if (provider.type !in availableTypes) {
            return "提供商类型未注册: ${provider.type}"
        }

        // 检查模型名称在同一提供商下是否已存在
        val existingModels = context.core.config.listModels()
        if (existingModels.any { it.data.providerId == provider.id && it.data.displayName.equals(displayName, ignoreCase = true) }) {
            return "该提供商下模型名称已存在: $displayName"
        }

        // 获取提供商元数据
        val providerMeta = trace.catching {
            context.core.config.getProviderMeta(provider.type)
        }.getOrElse { e ->
            return "获取提供商元数据失败: ${e.message}"
        }

        // 从提供商元数据中查找模型
        val modelInfo = providerMeta.models.find { it.modelId == modelId }
            ?: return buildString {
                appendLine("提供商 $providerName 不支持模型 $modelId")
                appendLine()
                appendLine("可用模型:")
                providerMeta.models.forEach { model ->
                    appendLine("  - ${model.modelId}")
                }
            }

        // 创建模型
        return trace.catching {
            val model = CoreConfig.ProviderConfig.Model(
                data = ModelData(
                    id = UUID.randomUUID(),
                    displayName = displayName,
                    modelInfo = modelInfo,
                    providerId = provider.id
                )
            )
            context.core.config.addModel(model)
            trace.add("model_add", model.toString())
            "模型已创建: $displayName\n提供商: $providerName\n模型ID: $modelId"
        }.getOrElse { e ->
            "创建模型失败: ${e.message}"
        }
    }

    private suspend fun deleteModel(context: CommandContext): String {
        // 删除模型需要操作员权限
        requireOperator(context)?.let { return it }

        if (context.args.size < 2) {
            return "用法: /model delete <显示名称|序号>"
        }

        val input = context.args[1]
        val models = context.core.config.listModels()
        if (models.isEmpty()) return "没有已配置的模型"

        // 按序号查找
        val index = input.toIntOrNull()
        val model = if (index != null && index in 1..models.size) {
            models[index - 1]
        } else {
            // 按名称查找
            models.find { it.data.displayName.equals(input, ignoreCase = true) }
        }

        if (model == null) return "未找到模型: $input"

        return trace.catching {
            context.core.config.removeModel(model.data.id)
            "已删除模型: ${model.data.displayName}"
        }.getOrElse { e ->
            "删除模型失败: ${e.message}"
        }
    }

    private suspend fun setUserPrimaryModel(context: CommandContext): String {
        if (context.args.size < 2) return "用法: /model set <名称|序号>"

        val modelId = resolveModelId(context, context.args[1])
            ?: return "未找到模型: ${context.args[1]}"

        context.sessionManager.setUserPrimaryModel(context.userId, modelId)

        // 若有活跃会话，同步更新配置
        val handle = context.sessionManager.getActiveSessionHandle(context.userId)
        if (handle != null) {
            val config = handle.data.value.config
            trace.catching {
                context.core.session.updateConfig(handle.id, config.copy(model = modelId))
                trace.add("session_config_update", "session=${handle.id}, config=${config.copy(model = modelId)}")
            }.onFailure { e ->
                return "我的主模型已设置为: ${getModelDisplayName(context, modelId)}，但当前会话更新失败: ${e.message}"
            }
            return "我的主模型已设置为: ${getModelDisplayName(context, modelId)}，当前会话已生效"
        }

        return "我的主模型已设置为: ${getModelDisplayName(context, modelId)}"
    }

    private fun handleFallback(context: CommandContext): String {
        if (context.args.size < 2) {
            return "用法:\n  /model fallback add <名称|序号>\n  /model fallback remove <名称|序号>"
        }

        // fallback 操作需要操作员权限
        requireOperator(context)?.let { return it }

        return when (context.args[1].lowercase()) {
            "add" -> addFallback(context)
            "remove" -> removeFallback(context)
            else -> "未知操作: ${context.args[1]}\n用法: /model fallback <add|remove> <名称|序号>"
        }
    }

    private fun addFallback(context: CommandContext): String {
        if (context.args.size < 3) return "用法: /model fallback add <名称|序号>"

        val modelId = resolveModelId(context, context.args[2])
            ?: return "未找到模型: ${context.args[2]}"

        return if (context.sessionManager.addFallbackModel(modelId)) {
            "已添加备选模型: ${getModelDisplayName(context, modelId)}"
        } else {
            "该模型已是备选模型"
        }
    }

    private fun removeFallback(context: CommandContext): String {
        if (context.args.size < 3) return "用法: /model fallback remove <名称|序号>"

        val modelId = resolveModelId(context, context.args[2])
            ?: return "未找到模型: ${context.args[2]}"

        return if (context.sessionManager.removeFallbackModel(modelId)) {
            "已移除备选模型: ${getModelDisplayName(context, modelId)}"
        } else {
            "该模型不在备选列表中"
        }
    }

    private fun setSummarizeModel(context: CommandContext): String {
        // 压缩模型需要操作员权限
        requireOperator(context)?.let { return it }

        if (context.args.size < 2) return "用法: /model summarize <名称|序号>"

        val modelId = resolveModelId(context, context.args[1])
            ?: return "未找到模型: ${context.args[1]}"

        context.sessionManager.setSummarizeModel(modelId)
        return "压缩模型已设置为: ${getModelDisplayName(context, modelId)}"
    }

    /**
     * 通过名称或序号解析模型 ID
     *
     * @param input 用户输入（序号如 "1" 或名称如 "DeepSeek V3"）
     * @return 模型 UUID，未找到返回 null
     */
    private fun resolveModelId(context: CommandContext, input: String): UUID? {
        val models = context.core.config.listModels()
        if (models.isEmpty()) return null

        // 尝试按序号解析
        val index = input.toIntOrNull()
        if (index != null && index in 1..models.size) {
            return models[index - 1].data.id
        }

        // 按名称匹配：精确 > 前缀 > 包含
        val lower = input.lowercase()
        return models.find { it.data.displayName.equals(lower, ignoreCase = true) }?.data?.id
            ?: models.find { it.data.displayName.lowercase().startsWith(lower) }?.data?.id
            ?: models.find { it.data.displayName.lowercase().contains(lower) }?.data?.id
    }

    private fun getModelDisplayName(context: CommandContext, id: UUID): String {
        return context.core.config.listModels().find { it.data.id == id }?.data?.displayName
            ?: "${id.toString().take(8)}（已删除）"
    }
}
