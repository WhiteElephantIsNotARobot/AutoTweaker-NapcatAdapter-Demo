package io.github.autotweaker.demo.adapter.napcat.command.commands

import io.github.autotweaker.demo.adapter.napcat.command.Command
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.permission.Role

/**
 * Thinking 开关命令
 *
 * 用户权限：
 *   /thinking - 查看当前 thinking 状态
 *   /thinking on - 开启 thinking
 *   /thinking off - 关闭 thinking
 *
 * thinking 状态跟随用户，持久化存储。
 */
class ThinkingCommand : Command {

    override val name = "thinking"
    override val description = "开关思考模式"
    override val usage = "/thinking [on|off]"
    override val requiredRole = Role.USER

    override suspend fun execute(context: CommandContext): String {
        if (context.args.isEmpty()) {
            return showStatus(context)
        }

        return when (context.args[0].lowercase()) {
            "on", "enable", "true", "1" -> setThinking(context, true)
            "off", "disable", "false", "0" -> setThinking(context, false)
            else -> "未知参数: ${context.args[0]}\n用法: $usage"
        }
    }

    private fun showStatus(context: CommandContext): String {
        val enabled = context.sessionManager.getUserThinking(context.userId)
        return "思考模式: ${if (enabled) "开启" else "关闭"}\n用法: /thinking [on|off]"
    }

    private fun setThinking(context: CommandContext, enabled: Boolean): String {
        context.sessionManager.setUserThinking(context.userId, enabled)
        val state = if (enabled) "开启" else "关闭"
        return "思考模式已$state\n新会话将生效，当前会话请使用 /new 创建新会话"
    }
}
