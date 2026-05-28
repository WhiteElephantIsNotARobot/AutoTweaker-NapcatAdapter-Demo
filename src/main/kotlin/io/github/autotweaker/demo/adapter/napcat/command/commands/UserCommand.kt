package io.github.autotweaker.demo.adapter.napcat.command.commands

import io.github.autotweaker.demo.adapter.napcat.command.Command
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.permission.Role

/**
 * 用户管理命令（操作员）
 *
 * 管理白名单用户和用户权限。
 *
 * 用法:
 *   /user add <userId> - 添加用户
 *   /user remove <userId> - 移除用户
 *   /user list - 列出用户
 *   /user noncontainer grant <userId> - 授予非容器权限
 *   /user noncontainer revoke <userId> - 撤销非容器权限
 *   /user noncontainer list - 列出有非容器权限的用户
 */
class UserCommand : Command {

    override val name = "user"
    override val description = "管理用户白名单和权限"
    override val usage = "/user <add|remove|list|noncontainer> [参数]"
    override val requiredRole = Role.OPERATOR

    override suspend fun execute(context: CommandContext): String {
        if (context.args.isEmpty()) {
            return "用法: $usage"
        }

        return when (context.args[0].lowercase()) {
            "add" -> addUser(context)
            "remove" -> removeUser(context)
            "list" -> listUsers(context)
            "noncontainer" -> handleNonContainer(context)
            else -> "未知子命令: ${context.args[0]}\n用法: $usage"
        }
    }

    private fun addUser(context: CommandContext): String {
        if (context.args.size < 2) return "用法: /user add <userId>"

        val userId = context.args[1].toLongOrNull()
            ?: return "无效的 QQ 号: ${context.args[1]}"

        return if (context.permissionManager.addUser(userId)) {
            "已添加用户: $userId"
        } else {
            "$userId 已在白名单中"
        }
    }

    private fun removeUser(context: CommandContext): String {
        if (context.args.size < 2) return "用法: /user remove <userId>"

        val userId = context.args[1].toLongOrNull()
            ?: return "无效的 QQ 号: ${context.args[1]}"

        return if (context.permissionManager.removeUser(userId)) {
            "已移除用户: $userId"
        } else {
            "$userId 不在白名单中"
        }
    }

    private fun listUsers(context: CommandContext): String {
        val users = context.permissionManager.listUsers()
        if (users.isEmpty()) return "白名单为空"

        val nonContainerUsers = context.permissionManager.listNonContainerUsers().toSet()

        return buildString {
            appendLine("用户白名单:")
            users.forEach { userId ->
                val nc = if (userId in nonContainerUsers) " [非容器]" else ""
                appendLine("  $userId$nc")
            }
        }
    }

    private fun handleNonContainer(context: CommandContext): String {
        if (context.args.size < 2) {
            return "用法:\n  /user noncontainer grant <userId>\n  /user noncontainer revoke <userId>\n  /user noncontainer list"
        }

        return when (context.args[1].lowercase()) {
            "grant" -> grantNonContainer(context)
            "revoke" -> revokeNonContainer(context)
            "list" -> listNonContainer(context)
            else -> "未知操作: ${context.args[1]}"
        }
    }

    private fun grantNonContainer(context: CommandContext): String {
        if (context.args.size < 3) return "用法: /user noncontainer grant <userId>"

        val userId = context.args[2].toLongOrNull()
            ?: return "无效的 QQ 号: ${context.args[2]}"

        return if (context.permissionManager.grantNonContainerPermission(userId)) {
            "已授予 $userId 非容器权限"
        } else {
            "$userId 已有非容器权限"
        }
    }

    private fun revokeNonContainer(context: CommandContext): String {
        if (context.args.size < 3) return "用法: /user noncontainer revoke <userId>"

        val userId = context.args[2].toLongOrNull()
            ?: return "无效的 QQ 号: ${context.args[2]}"

        return if (context.permissionManager.revokeNonContainerPermission(userId)) {
            "已撤销 $userId 的非容器权限"
        } else {
            "$userId 没有非容器权限"
        }
    }

    private fun listNonContainer(context: CommandContext): String {
        val users = context.permissionManager.listNonContainerUsers()
        if (users.isEmpty()) return "没有用户有非容器权限"

        return buildString {
            appendLine("有非容器权限的用户:")
            users.forEach { appendLine("  $it") }
        }
    }
}
