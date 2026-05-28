package io.github.autotweaker.demo.adapter.napcat.command.commands

import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.demo.adapter.napcat.command.Command
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.permission.Role
import java.nio.file.Paths
import java.util.UUID

/**
 * 工作区管理命令（操作员）
 *
 * 用法:
 *   /workspace - 列出所有工作区
 *   /workspace create <名称> <路径> [container] - 创建工作区
 *   /workspace delete <名称|序号> - 删除工作区
 */
class WorkspaceCommand : Command {

    override val name = "workspace"
    override val description = "管理工作区"
    override val usage = "/workspace [create|delete] [参数]"
    override val requiredRole = Role.OPERATOR

    override suspend fun execute(context: CommandContext): String {
        if (context.args.isEmpty()) {
            return listWorkspaces(context)
        }

        return when (context.args[0].lowercase()) {
            "create" -> createWorkspace(context)
            "delete" -> deleteWorkspace(context)
            else -> "未知子命令: ${context.args[0]}\n用法: $usage"
        }
    }

    private fun listWorkspaces(context: CommandContext): String {
        val workspaces = context.core.session.listWorkspaces()
        if (workspaces.isEmpty()) return "没有工作区"

        return buildString {
            appendLine("工作区:")
            workspaces.forEachIndexed { index, ws ->
                val container = if (ws.meta.inContainer) " [容器]" else ""
                appendLine("  ${index + 1}. ${ws.meta.name} - ${ws.meta.path}$container")
            }
        }
    }

    private fun createWorkspace(context: CommandContext): String {
        if (context.args.size < 3) {
            return "用法: /workspace create <名称> <路径> [container]"
        }

        val name = context.args[1]
        val pathStr = context.args[2]
        val inContainer = context.args.getOrNull(3)?.lowercase() == "container"

        val path = try {
            Paths.get(pathStr)
        } catch (e: Exception) {
            return "无效路径: $pathStr"
        }

        // 验证路径存在且是目录
        if (!java.io.File(pathStr).isDirectory) {
            return "路径不存在或不是目录: $pathStr"
        }

        return try {
            val meta = WorkspaceMeta(
                name = name,
                inContainer = inContainer,
                path = path
            )
            val workspace = context.core.session.createWorkspace(meta)
            "工作区已创建: ${workspace.meta.name}\n路径: ${workspace.meta.path}"
        } catch (e: Exception) {
            "创建工作区失败: ${e.message}"
        }
    }

    private suspend fun deleteWorkspace(context: CommandContext): String {
        if (context.args.size < 2) {
            return "用法: /workspace delete <名称|序号>"
        }

        val input = context.args[1]
        val workspaces = context.core.session.listWorkspaces()
        if (workspaces.isEmpty()) return "没有工作区"

        // 按序号查找
        val index = input.toIntOrNull()
        val workspace = if (index != null && index in 1..workspaces.size) {
            workspaces[index - 1]
        } else {
            // 按名称查找
            workspaces.find { it.meta.name.lowercase().contains(input.lowercase()) }
        }

        if (workspace == null) return "未找到工作区: $input"

        return try {
            context.core.session.deleteWorkspace(workspace.id)
            "已删除工作区: ${workspace.meta.name}"
        } catch (e: Exception) {
            "删除工作区失败: ${e.message}"
        }
    }
}
