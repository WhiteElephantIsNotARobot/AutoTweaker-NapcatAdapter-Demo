package io.github.autotweaker.demo.adapter.napcat.command.commands

import io.github.autotweaker.demo.adapter.napcat.command.Command
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.permission.Role

/**
 * 操作员管理命令（仅管理员）
 *
 * 用法:
 *   /op add <userId> - 添加操作员
 *   /op remove <userId> - 移除操作员
 *   /op list - 列出操作员
 */
class OperatorCommand : Command {

    override val name = "op"
    override val description = "管理操作员"
    override val usage = "/op <add|remove|list> [userId]"
    override val requiredRole = Role.ADMIN

    override suspend fun execute(context: CommandContext): String {
        if (context.args.isEmpty()) {
            return "用法: $usage"
        }

        return when (context.args[0].lowercase()) {
            "add" -> {
                if (context.args.size < 2) return "用法: /op add <userId>"
                val userId = context.args[1].toLongOrNull()
                    ?: return "无效的 QQ 号: ${context.args[1]}"
                if (context.permissionManager.addOperator(userId)) {
                    "已添加操作员: $userId"
                } else {
                    "$userId 已经是操作员"
                }
            }
            "remove" -> {
                if (context.args.size < 2) return "用法: /op remove <userId>"
                val userId = context.args[1].toLongOrNull()
                    ?: return "无效的 QQ 号: ${context.args[1]}"
                if (context.permissionManager.removeOperator(userId)) {
                    "已移除操作员: $userId"
                } else {
                    "$userId 不是操作员"
                }
            }
            "list" -> {
                val operators = context.permissionManager.listOperators()
                if (operators.isEmpty()) {
                    "没有操作员"
                } else {
                    buildString {
                        appendLine("操作员列表:")
                        operators.forEach { appendLine("  $it") }
                    }
                }
            }
            else -> "未知子命令: ${context.args[0]}\n用法: $usage"
        }
    }
}
