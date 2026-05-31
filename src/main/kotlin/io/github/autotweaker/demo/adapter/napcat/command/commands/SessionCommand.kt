package io.github.autotweaker.demo.adapter.napcat.command.commands

import io.github.autotweaker.demo.adapter.napcat.command.Command
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.permission.Role

/**
 * 会话管理命令
 *
 * 用户权限：
 *   /session - 列出当前用户的会话
 *   /session list - 列出所有会话
 */
class SessionCommand : Command {

    override val name = "session"
    override val description = "管理会话"
    override val usage = "/session [list]"
    override val requiredRole = Role.USER

    override suspend fun execute(context: CommandContext): String {
        if (context.args.isEmpty()) {
            return showMySession(context)
        }

        return when (context.args[0].lowercase()) {
            "list", "ls" -> listAllSessions(context)
            else -> "未知子命令: ${context.args[0]}\n用法: $usage"
        }
    }

    private suspend fun showMySession(context: CommandContext): String {
        val sessionId = context.sessionManager.getActiveSession(context.userId)
        if (sessionId == null) {
            return "你没有活跃的会话"
        }

        val handle = try {
            context.core.session.getHandle(sessionId)
        } catch (e: Exception) {
            context.sessionManager.clearActiveSession(context.userId)
            return "你没有活跃的会话"
        }

        val data = handle.data.value
        val workspace = context.core.session.listWorkspaces()
            .find { it.meta.id == data.workspaceId }

        return buildString {
            appendLine("当前会话:")
            appendLine("  ID: ${data.id}")
            appendLine("  标题: ${data.title ?: "未设置"}")
            appendLine("  工作区: ${workspace?.meta?.displayName ?: "未知"}")
        }
    }

    private suspend fun listAllSessions(context: CommandContext): String {
        val role = context.permissionManager.getRole(context.userId)
        if (role == null || role == Role.USER) {
            // USER 只能查看自己的会话
            val mySession = context.sessionManager.getActiveSession(context.userId)
            return if (mySession != null) "当前会话: $mySession" else "没有活跃会话"
        }

        val workspaces = context.core.session.listWorkspaces()
        val allSessionIds = workspaces.flatMap { it.sessionIds.orEmpty() }
        if (allSessionIds.isEmpty()) {
            return "没有会话"
        }

        val sessions = context.core.session.loadData(allSessionIds)
        if (sessions.isEmpty()) {
            return "没有会话"
        }

        val activeSessionId = context.sessionManager.getActiveSession(context.userId)

        return buildString {
            appendLine("所有会话:")
            sessions.forEach { data ->
                val workspace = workspaces.find { it.meta.id == data.workspaceId }
                val active = if (data.id == activeSessionId) " ← 当前" else ""
                appendLine("  ${data.title ?: "未设置"}$active")
                appendLine("    ID: ${data.id}")
                appendLine("    工作区: ${workspace?.meta?.displayName ?: "未知"}")
            }
        }
    }
}
