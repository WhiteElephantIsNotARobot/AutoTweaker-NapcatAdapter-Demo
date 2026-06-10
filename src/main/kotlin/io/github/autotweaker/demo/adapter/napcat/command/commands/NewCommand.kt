package io.github.autotweaker.demo.adapter.napcat.command.commands

import io.github.autotweaker.api.trace.TraceRecorder
import io.github.autotweaker.demo.adapter.napcat.command.Command
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.permission.Role

/**
 * 创建新会话并进入
 *
 * 用法: /new [标题]
 * 操作员及以上可使用默认工作区（容器外），普通用户需指定工作区。
 */
class NewCommand : Command {

    private lateinit var trace: TraceRecorder

    override val name = "new"
    override val description = "创建新会话并进入"
    override val usage = "/new [标题]"
    override val requiredRole = Role.USER

    override suspend fun execute(context: CommandContext): String {
        if (!::trace.isInitialized) trace = context.core.trace(this::class)
        val title = context.args.joinToString(" ").take(100).ifEmpty { "新会话" }

        return try {
            val handle = context.sessionManager.autoCreateSession(context.userId, title)
            "会话已创建: ${handle.id}\n标题: $title\n已自动进入此会话"
        } catch (e: IllegalStateException) {
            trace.exception(e)
            // 没有可用工作区或没有可用模型
            e.message ?: "创建会话失败"
        } catch (e: Exception) {
            trace.exception(e)
            "创建会话失败: ${e.message}"
        }
    }
}
