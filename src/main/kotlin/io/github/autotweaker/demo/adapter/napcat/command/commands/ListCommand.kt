package io.github.autotweaker.demo.adapter.napcat.command.commands

import io.github.autotweaker.demo.adapter.napcat.command.Command
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.permission.Role

/**
 * 列出可用会话
 *
 * 用法: /list
 */
class ListCommand : Command {

    override val name = "list"
    override val description = "列出可用会话"
    override val usage = "/list"
    override val requiredRole = Role.USER

    override suspend fun execute(context: CommandContext): String {
        return try {
            val workspaces = context.core.session.listWorkspaces()
            if (workspaces.isEmpty()) {
                return "没有可用的工作区"
            }

            buildString {
                appendLine("可用工作区:")
                for (ws in workspaces) {
                    appendLine("  ${ws.id} - ${ws.meta.name}")
                }
            }
        } catch (e: Exception) {
            "获取列表失败: ${e.message}"
        }
    }
}
