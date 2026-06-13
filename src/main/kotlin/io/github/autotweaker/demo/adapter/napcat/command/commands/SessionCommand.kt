package io.github.autotweaker.demo.adapter.napcat.command.commands

import io.github.autotweaker.api.trace.TraceRecorder
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.demo.adapter.napcat.command.Command
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.permission.Role
import org.slf4j.LoggerFactory
import java.util.UUID

class SessionCommand : Command {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private lateinit var trace: TraceRecorder

    override val name = "session"
    override val description = "管理会话"
    override val usage = "/session [list|new|enter|exit|remove] [参数]"
    override val requiredRole = Role.USER

    override suspend fun execute(context: CommandContext): String {
        if (!::trace.isInitialized) trace = context.core.trace(this::class)
        if (context.args.isEmpty()) {
            return showMySession(context)
        }

        return when (context.args[0].lowercase()) {
            "list", "ls" -> listSessions(context)
            "new", "create" -> newSession(context)
            "enter" -> enterSession(context)
            "exit", "leave" -> exitSession(context)
            "remove", "rm", "delete" -> removeSession(context)
            else -> "未知子命令: ${context.args[0]}\n用法: $usage"
        }
    }

    private suspend fun showMySession(context: CommandContext): String {
        val sessionId = context.sessionManager.getActiveSession(context.userId)
            ?: return "你没有活跃的会话"

        val handle = trace.catching {
            context.core.session.getHandle(sessionId)
        }.getOrElse {
            context.sessionManager.clearActiveSession(context.userId)
            return "你没有活跃的会话"
        }

        val data = handle.data.value
        val workspace = context.core.session.listWorkspaces()
            .find { it.meta.id == data.workspaceId }

        return buildString {
            appendLine("当前会话:")
            appendLine("  标题: ${data.title ?: "未设置"}")
            appendLine("  工作区: ${workspace?.meta?.displayName ?: "未知"}")
        }
    }

    private suspend fun listSessions(context: CommandContext): String {
        val workspaceId = context.sessionManager.getUserWorkspace(context.userId)
            ?: return "你还没有选择工作区，请先 /workspace select"

        val workspace = context.core.session.listWorkspaces()
            .find { it.meta.id == workspaceId }
            ?: return "当前工作区不存在"

        val sessionIds = workspace.sessionIds.orEmpty()
        if (sessionIds.isEmpty()) {
            return "当前工作区（${workspace.meta.displayName}）没有会话"
        }

        val loadedSessions = context.core.session.loadData(sessionIds)
        if (loadedSessions.isEmpty()) {
            return "没有会话"
        }
        val sessionsMap = loadedSessions.associateBy { it.id }
        val sessions = sessionIds.mapNotNull { sessionsMap[it] }

        val activeSessionId = context.sessionManager.getActiveSession(context.userId)

        return buildString {
            appendLine("工作区「${workspace.meta.displayName}」的会话:")
            sessions.forEachIndexed { index, data ->
                val active = if (data.id == activeSessionId) " ← 当前" else ""
                appendLine("  ${index + 1}. ${data.title ?: "未设置"}$active")
            }
        }
    }

    private suspend fun newSession(context: CommandContext): String {
        val title = context.args.drop(1).joinToString(" ").take(100).ifEmpty { "新会话" }

        return try {
            val handle = context.sessionManager.autoCreateSession(context.userId, title)
            "会话已创建\n标题: $title\n已自动进入此会话"
        } catch (e: IllegalStateException) {
            trace.exception(e)
            e.message ?: "创建会话失败"
        } catch (e: Exception) {
            trace.exception(e)
            "创建会话失败: ${e.message}"
        }
    }

    private suspend fun enterSession(context: CommandContext): String {
        if (context.args.size < 2) {
            return "用法: /session enter <序号|标题>"
        }

        val sessionId = resolveSessionId(context, context.args[1])
            ?: return "未找到会话: ${context.args[1]}"

        return try {
            context.sessionManager.enterSession(context.userId, sessionId)
            "已进入会话"
        } catch (e: IllegalStateException) {
            trace.exception(e)
            logger.warn("Failed to enter session  sessionId={}", sessionId, e)
            "会话恢复失败: ${e.message}"
        } catch (e: Exception) {
            trace.exception(e)
            logger.warn("Failed to enter session  sessionId={}", sessionId, e)
            "会话不存在"
        }
    }

    private suspend fun exitSession(context: CommandContext): String {
        val handle = context.sessionManager.getActiveSessionHandle(context.userId)
            ?: return "当前没有活跃会话"

        return trace.catching {
            try {
                context.core.session.stop(handle.id)
            } finally {
                context.sessionManager.exitSession(context.userId)
            }
            "已停止并退出会话"
        }.getOrElse { "退出失败，请稍后重试" }
    }

    private suspend fun removeSession(context: CommandContext): String {
        if (context.args.size < 2) {
            return "用法: /session remove <序号|标题>"
        }

        val sessionId = resolveSessionId(context, context.args[1])
            ?: return "未找到会话: ${context.args[1]}"

        return try {
            context.core.session.delete(sessionId)
            val activeSessionId = context.sessionManager.getActiveSession(context.userId)
            if (sessionId == activeSessionId) {
                context.sessionManager.clearActiveSession(context.userId)
            }
            "会话已删除"
        } catch (e: Exception) {
            trace.exception(e)
            "删除失败: ${e.message}"
        }
    }

    private suspend fun resolveSessionId(context: CommandContext, input: String): UUID? {
        val workspaceId = context.sessionManager.getUserWorkspace(context.userId) ?: return null
        val workspace = context.core.session.listWorkspaces().find { it.meta.id == workspaceId } ?: return null
        val sessionIds = workspace.sessionIds.orEmpty()
        if (sessionIds.isEmpty()) return null
        val loadedSessions = context.core.session.loadData(sessionIds)
        if (loadedSessions.isEmpty()) return null
        val sessionsMap = loadedSessions.associateBy { it.id }
        val sessions = sessionIds.mapNotNull { sessionsMap[it] }

        val index = input.toIntOrNull()
        if (index != null) {
            return if (index in 1..sessions.size) sessions[index - 1].id else null
        }

        val lower = input.lowercase()
        return sessions.find { it.title?.equals(lower, ignoreCase = true) == true }?.id
            ?: sessions.find { it.title?.lowercase()?.startsWith(lower) == true }?.id
            ?: sessions.find { it.title?.lowercase()?.contains(lower) == true }?.id
    }
}
