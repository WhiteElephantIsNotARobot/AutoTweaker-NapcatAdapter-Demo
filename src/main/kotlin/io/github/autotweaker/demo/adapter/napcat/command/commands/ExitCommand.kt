package io.github.autotweaker.demo.adapter.napcat.command.commands

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

    override val name = "exit"
    override val description = "退出当前会话"
    override val usage = "/exit"
    override val requiredRole = Role.USER

    override suspend fun execute(context: CommandContext): String {
        return if (context.sessionManager.exitSession(context.userId)) {
            "已退出当前会话"
        } else {
            "当前没有活跃会话"
        }
    }
}
