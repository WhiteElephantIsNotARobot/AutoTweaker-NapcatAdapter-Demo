package io.github.autotweaker.demo.adapter.napcat.command.commands

import io.github.autotweaker.api.types.session.WorkspaceMeta
import io.github.autotweaker.demo.adapter.napcat.command.Command
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.permission.Role
import java.nio.file.Paths
import java.util.UUID

/**
 * 工作区管理命令
 *
 * 用户权限：
 *   /workspace - 列出可用工作区
 *   /workspace select <名称|序号> - 选择工作区
 *
 * 操作员权限：
 *   /workspace create <名称> <路径> [container] - 创建工作区
 *   /workspace delete <名称|序号> - 删除工作区
 */
class WorkspaceCommand : Command {

    override val name = "workspace"
    override val description = "管理工作区"
    override val usage = "/workspace [select|create|delete] [参数]"
    override val requiredRole = Role.USER

    override suspend fun execute(context: CommandContext): String {
        if (context.args.isEmpty()) {
            return listWorkspaces(context)
        }

        return when (context.args[0].lowercase()) {
            "select" -> selectWorkspace(context)
            "create" -> createWorkspace(context)
            "delete" -> deleteWorkspace(context)
            else -> "未知子命令: ${context.args[0]}\n用法: $usage"
        }
    }

    private fun listWorkspaces(context: CommandContext): String {
        val allWorkspaces = context.core.session.listWorkspaces()
        if (allWorkspaces.isEmpty()) return "没有工作区"

        val hasNonContainer = context.permissionManager.hasNonContainerPermission(context.userId)
        val workspaces = if (hasNonContainer) {
            allWorkspaces
        } else {
            allWorkspaces.filter { it.meta.inContainer }
        }

        if (workspaces.isEmpty()) {
            return "没有可用的工作区"
        }

        val selectedId = context.sessionManager.getUserWorkspace(context.userId)

        return buildString {
            appendLine("可用工作区:")
            workspaces.forEachIndexed { index, ws ->
                val container = if (ws.meta.inContainer) " [容器]" else " [非容器]"
                val selected = if (ws.id == selectedId) " ← 当前" else ""
                appendLine("  ${index + 1}. ${ws.meta.name}$container$selected")
            }
        }
    }

    private fun selectWorkspace(context: CommandContext): String {
        if (context.args.size < 2) return "用法: /workspace select <名称|序号>"

        val input = context.args[1]
        val allWorkspaces = context.core.session.listWorkspaces()
        if (allWorkspaces.isEmpty()) return "没有工作区"

        val hasNonContainer = context.permissionManager.hasNonContainerPermission(context.userId)
        val workspaces = if (hasNonContainer) {
            allWorkspaces
        } else {
            allWorkspaces.filter { it.meta.inContainer }
        }

        if (workspaces.isEmpty()) return "没有可用的工作区"

        // 按序号查找
        val index = input.toIntOrNull()
        val workspace = if (index != null && index in 1..workspaces.size) {
            workspaces[index - 1]
        } else {
            // 按名称查找
            workspaces.find { it.meta.name.lowercase().contains(input.lowercase()) }
        }

        if (workspace == null) return "未找到工作区: $input"

        context.sessionManager.setUserWorkspace(context.userId, workspace.id)
        return "已选择工作区: ${workspace.meta.name}"
    }

    private fun createWorkspace(context: CommandContext): String {
        // 创建工作区需要操作员权限
        val role = context.role
        if (role == null || role.ordinal > Role.OPERATOR.ordinal) {
            return "权限不足，需要操作员角色"
        }

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
        // 删除工作区需要操作员权限
        val role = context.role
        if (role == null || role.ordinal > Role.OPERATOR.ordinal) {
            return "权限不足，需要操作员角色"
        }

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
