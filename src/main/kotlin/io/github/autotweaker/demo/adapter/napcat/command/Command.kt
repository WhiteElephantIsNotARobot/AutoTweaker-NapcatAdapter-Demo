package io.github.autotweaker.demo.adapter.napcat.command

import io.github.autotweaker.demo.adapter.napcat.permission.Role

/**
 * 命令接口
 *
 * 所有斜杠命令必须实现此接口。命令通过 [CommandRegistry] 注册和分发。
 */
interface Command {

    /**
     * 命令名称（不含斜杠），如 "enter"、"new"、"approve"
     */
    val name: String

    /**
     * 命令描述，用于帮助信息
     */
    val description: String

    /**
     * 命令用法说明
     */
    val usage: String

    /**
     * 执行此命令所需的最低角色
     */
    val requiredRole: Role

    /**
     * 执行命令
     *
     * @param context 命令上下文
     * @return 命令执行结果文本，发送给用户
     */
    suspend fun execute(context: CommandContext): String
}
