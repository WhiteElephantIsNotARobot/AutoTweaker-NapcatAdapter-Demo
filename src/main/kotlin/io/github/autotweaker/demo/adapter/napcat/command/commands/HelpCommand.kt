package io.github.autotweaker.demo.adapter.napcat.command.commands

import io.github.autotweaker.demo.adapter.napcat.command.Command
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.command.CommandRegistry
import io.github.autotweaker.demo.adapter.napcat.permission.Role

/**
 * 帮助命令，列出所有可用命令
 *
 * @property registry 命令注册表，用于获取命令列表
 */
class HelpCommand(private val registry: CommandRegistry) : Command {

    override val name = "help"
    override val description = "显示帮助信息"
    override val usage = "/help"
    override val requiredRole = Role.USER

    override suspend fun execute(context: CommandContext): String {
        val role = context.role ?: return "未授权"

        // 如果有参数，显示指定命令的详细用法
        if (context.args.isNotEmpty()) {
            val commandName = context.args[0].removePrefix("/")
            val cmd = registry.getCommand(commandName)
            if (cmd == null) {
                return "未知命令: $commandName"
            }
            if (role.level < cmd.requiredRole.level) {
                return "权限不足，需要 ${cmd.requiredRole.name} 角色"
            }
            return buildString {
                appendLine("/${cmd.name} - ${cmd.description}")
                appendLine()
                appendLine("用法: ${cmd.usage}")
            }
        }

        // 否则显示所有命令列表
        val commands = registry.listCommands()
            .filter { role.level >= it.requiredRole.level }
            .sortedBy { it.name }

        return buildString {
            appendLine("可用命令:")
            for (cmd in commands) {
                appendLine("  /${cmd.name} - ${cmd.description}")
            }
            appendLine()
            appendLine("使用 /help <命令名> 查看详细用法")
        }
    }
}
