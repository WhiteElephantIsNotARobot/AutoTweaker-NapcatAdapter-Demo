package io.github.autotweaker.demo.adapter.napcat.command.commands

import io.github.autotweaker.api.types.agent.ToolApprove
import io.github.autotweaker.demo.adapter.napcat.command.Command
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.permission.Role

/**
 * 审批工具调用请求
 *
 * 用法: /approve <requestId>
 * 当 LLM 请求调用工具时，用户通过此命令批准执行。
 */
class ApproveCommand : Command {

    override val name = "approve"
    override val description = "审批工具调用请求"
    override val usage = "/approve <requestId>"
    override val requiredRole = Role.USER

    override suspend fun execute(context: CommandContext): String {
        if (context.args.isEmpty()) {
            return "用法: $usage"
        }

        val callId = context.args[0]

        // 获取用户的活跃会话
        val handle = context.sessionManager.getActiveSessionHandle(context.userId)
            ?: return "当前没有活跃会话，请先 /new 或 /enter 一个会话"

        return try {
            val approvals = listOf(ToolApprove(callId = callId, approved = true))
            context.core.session.approveToolCall(handle.id, approvals)
            "已审批工具调用: $callId"
        } catch (e: Exception) {
            "审批失败: ${e.message}"
        }
    }
}
