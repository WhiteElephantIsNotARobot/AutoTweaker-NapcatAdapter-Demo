package io.github.autotweaker.demo.adapter.napcat.command.commands

import io.github.autotweaker.demo.adapter.napcat.command.Command
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.permission.Role

/**
 * 列出可用工作区
 *
 * 根据用户权限过滤显示：
 * - 有非容器权限：显示所有工作区
 * - 无非容器权限：仅显示容器内工作区
 *
 * 用法: /list
 */
class ListCommand : Command {

    override val name = "list"
    override val description = "列出可用工作区"
    override val usage = "/list"
    override val requiredRole = Role.USER

    override suspend fun execute(context: CommandContext): String {
        return try {
            val allWorkspaces = context.core.session.listWorkspaces()
            if (allWorkspaces.isEmpty()) {
                return "没有可用的工作区"
            }

            val hasNonContainer = context.permissionManager.hasNonContainerPermission(context.userId)
            val workspaces = if (hasNonContainer) {
                allWorkspaces
            } else {
                allWorkspaces.filter { it.meta.inContainer }
            }

            if (workspaces.isEmpty()) {
                return if (hasNonContainer) {
                    "没有可用的工作区"
                } else {
                    "没有可用的容器工作区，请联系操作员创建"
                }
            }

            buildString {
                appendLine("可用工作区:")
                workspaces.forEachIndexed { index, ws ->
                    val container = if (ws.meta.inContainer) " [容器]" else " [非容器]"
                    appendLine("  ${index + 1}. ${ws.meta.name}$container")
                }
            }
        } catch (e: Exception) {
            "获取列表失败: ${e.message}"
        }
    }
}
