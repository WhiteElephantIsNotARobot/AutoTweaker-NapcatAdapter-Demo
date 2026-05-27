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
        val commands = registry.listCommands()
            .filter { context.role.ordinal <= it.requiredRole.ordinal }
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
