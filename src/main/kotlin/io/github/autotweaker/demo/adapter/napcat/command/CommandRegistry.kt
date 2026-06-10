package io.github.autotweaker.demo.adapter.napcat.command

import org.slf4j.LoggerFactory

/**
 * 命令注册表
 *
 * 负责注册和分发斜杠命令。消息解析逻辑：
 * - 群聊：检测 @bot → 提取文本 → 检查是否以 / 开头
 * - 私聊：检查是否以 / 开头
 */
class CommandRegistry {

    companion object {
        private val WHITESPACE_REGEX = "\\s+".toRegex()
    }

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val commands = mutableMapOf<String, Command>()

    /**
     * 注册命令
     *
     * @param command 要注册的命令
     * @throws IllegalArgumentException 如果命令名已存在
     */
    fun register(command: Command) {
        val name = command.name.lowercase()
        require(name !in commands) { "Command already registered: $name" }
        commands[name] = command
        logger.debug("Command registered  command={}", name)
    }

    /**
     * 获取命令
     *
     * @param name 命令名（不含斜杠）
     * @return 命令实例，不存在返回 null
     */
    fun getCommand(name: String): Command? = commands[name.lowercase()]

    /**
     * 列出所有已注册命令
     *
     * @return 命令列表
     */
    fun listCommands(): List<Command> = commands.values.toList()

    /**
     * 解析消息文本，提取命令名和参数
     *
     * @param text 消息文本（已去除 @bot 前缀）
     * @return Pair(命令名, 参数列表)，如果不是命令返回 null
     */
    fun parseCommand(text: String): Pair<String, List<String>>? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("/")) return null

        val parts = trimmed.split(WHITESPACE_REGEX)
        val name = parts[0].removePrefix("/").lowercase()
        val args = parts.drop(1)

        return if (name.isNotEmpty()) name to args else null
    }

    /**
     * 分发并执行命令
     *
     * @param text 消息文本（已去除 @bot 前缀）
     * @param context 命令上下文
     * @return 命令执行结果，如果不是命令返回 null
     */
    suspend fun dispatch(text: String, context: CommandContext): String? {
        val (name, args) = parseCommand(text) ?: return null

        val command = commands[name]
        if (command == null) {
            logger.warn("Unknown command  command={}", name)
            return "未知命令: /$name\n输入 /help 查看可用命令"
        }

        val role = context.role
        if (role == null || role.level < command.requiredRole.level) {
            return "权限不足，需要 ${command.requiredRole.name} 角色"
        }

        return try {
            command.execute(context.copy(args = args))
        } catch (e: Exception) {
            logger.error("Failed to execute command  command={}", name, e)
            "命令执行失败，请稍后重试"
        }
    }
}
