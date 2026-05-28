package io.github.autotweaker.demo.adapter.napcat.command.commands

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
 *   /model fallback add <名称|序号> - 添加全局备选模型
 *   /model fallback remove <名称|序号> - 移除全局备选模型
 *   /model summarize <名称|序号> - 设置全局压缩模型
 */
class ModelCommand : Command {

    override val name = "model"
    override val description = "管理模型配置"
    override val usage = "/model [list|set|fallback|summarize] [参数]"
    override val requiredRole = Role.USER

    override suspend fun execute(context: CommandContext): String {
        if (context.args.isEmpty()) {
            return showCurrentConfig(context)
        }

        return when (context.args[0].lowercase()) {
            "list" -> listModels(context)
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
                val provider = model.data.providerId?.let { pid ->
                    context.core.config.listProviders().find { it.id == pid }?.displayName
                } ?: "未知"
                appendLine("  ${index + 1}. [$provider] ${model.data.displayName}$marker")
            }
        }
    }

    private fun setUserPrimaryModel(context: CommandContext): String {
        if (context.args.size < 2) return "用法: /model set <名称|序号>"

        val modelId = resolveModelId(context, context.args[1])
            ?: return "未找到模型: ${context.args[1]}"

        context.sessionManager.setUserPrimaryModel(context.userId, modelId)
        return "我的主模型已设置为: ${getModelDisplayName(context, modelId)}"
    }

    private fun handleFallback(context: CommandContext): String {
        if (context.args.size < 2) {
            return "用法:\n  /model fallback add <名称|序号>\n  /model fallback remove <名称|序号>"
        }

        // fallback 操作需要操作员权限
        val role = context.role
        if (role == null || role.ordinal > Role.OPERATOR.ordinal) {
            return "权限不足，需要操作员角色"
        }

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
        val role = context.role
        if (role == null || role.ordinal > Role.OPERATOR.ordinal) {
            return "权限不足，需要操作员角色"
        }

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

        // 按名称模糊匹配
        val lower = input.lowercase()
        return models.find { it.data.displayName.lowercase().contains(lower) }?.data?.id
    }

    private fun getModelDisplayName(context: CommandContext, id: UUID): String {
        return context.core.config.listModels().find { it.data.id == id }?.data?.displayName
            ?: id.toString().take(8)
    }
}
