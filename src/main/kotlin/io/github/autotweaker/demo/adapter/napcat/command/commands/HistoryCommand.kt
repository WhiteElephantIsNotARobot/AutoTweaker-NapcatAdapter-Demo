package io.github.autotweaker.demo.adapter.napcat.command.commands

import io.github.autotweaker.demo.adapter.napcat.command.Command
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.permission.Role

/**
 * 消息历史注入开关命令
 *
 * 用户权限：
 *   /history - 切换消息历史注入状态
 *   /history on - 开启消息历史注入
 *   /history off - 关闭消息历史注入
 *
 * 消息历史注入状态跟随用户，持久化存储。
 * 关闭后，发送给 LLM 的消息将不再包含最近的群/私聊消息历史。
 */
class HistoryCommand : Command {

    override val name = "history"
    override val description = "开关消息历史注入"
    override val usage = "/history [on|off]"
    override val requiredRole = Role.USER

    override suspend fun execute(context: CommandContext): String {
        val newEnabled = if (context.args.isEmpty()) {
            // 无参数：切换状态
            !context.sessionManager.getUserHistoryInjection(context.userId)
        } else {
            when (context.args[0].lowercase()) {
                "on", "enable", "true", "1" -> true
                "off", "disable", "false", "0" -> false
                else -> return "未知参数: ${context.args[0]}\n用法: $usage"
            }
        }

        return setHistoryInjection(context, newEnabled)
    }

    private fun setHistoryInjection(context: CommandContext, enabled: Boolean): String {
        context.sessionManager.setUserHistoryInjection(context.userId, enabled)
        val state = if (enabled) "开启" else "关闭"
        return "消息历史注入已$state"
    }
}
