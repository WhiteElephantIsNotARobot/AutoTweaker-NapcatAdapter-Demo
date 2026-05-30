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
 * 若当前有活跃会话，会同时更新会话配置使其即时生效。
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

    private suspend fun setThinking(context: CommandContext, enabled: Boolean): String {
        context.sessionManager.setUserThinking(context.userId, enabled)
        val state = if (enabled) "开启" else "关闭"

        // 若有活跃会话，同步更新配置
        val handle = context.sessionManager.getActiveSessionHandle(context.userId)
        if (handle != null) {
            val config = handle.data.value.config
            context.core.session.updateConfig(handle.id, config.copy(thinking = enabled))
            return "思考模式已$state，当前会话已生效"
        }

        return "思考模式已$state"
    }

}
