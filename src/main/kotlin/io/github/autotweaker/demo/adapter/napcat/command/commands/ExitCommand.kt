package io.github.autotweaker.demo.adapter.napcat.command.commands

import io.github.autotweaker.api.trace.TraceRecorder
import io.github.autotweaker.demo.adapter.napcat.command.Command
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.permission.Role

/**
 * 退出当前会话
 *
 * 用法: /exit
 * 退出后消息不再转发给 LLM，但会话不删除。
 */
class ExitCommand : Command {

    private lateinit var trace: TraceRecorder

    override val name = "exit"
    override val description = "退出当前会话"
    override val usage = "/exit"
    override val requiredRole = Role.USER

    override suspend fun execute(context: CommandContext): String {
        if (!::trace.isInitialized) trace = context.core.trace(this::class)
        val handle = context.sessionManager.getActiveSessionHandle(context.userId)
            ?: return "当前没有活跃会话"

        return try {
            try {
                context.core.session.stop(handle.id)
            } finally {
                context.sessionManager.exitSession(context.userId)
            }
            "已停止并退出会话"
        } catch (e: Exception) {
            trace.add("e", e.toString())
            "退出失败，请稍后重试"
        }
    }
}
