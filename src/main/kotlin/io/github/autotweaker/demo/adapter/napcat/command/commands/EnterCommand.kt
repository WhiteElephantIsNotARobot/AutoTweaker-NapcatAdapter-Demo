package io.github.autotweaker.demo.adapter.napcat.command.commands

import io.github.autotweaker.demo.adapter.napcat.command.Command
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.permission.Role
import java.util.UUID

/**
 * 进入指定会话
 *
 * 用法: /enter <sessionId>
 */
class EnterCommand : Command {

    override val name = "enter"
    override val description = "进入指定会话"
    override val usage = "/enter <sessionId>"
    override val requiredRole = Role.USER

    override suspend fun execute(context: CommandContext): String {
        if (context.args.isEmpty()) {
            return "用法: $usage"
        }

        val sessionId = try {
            UUID.fromString(context.args[0])
        } catch (e: IllegalArgumentException) {
            return "无效的会话 ID: ${context.args[0]}"
        }

        val handle = context.sessionManager.enterSession(context.userId, sessionId)
            ?: return "会话不存在: $sessionId"

        return "已进入会话: $sessionId"
    }
}
