package io.github.autotweaker.demo.adapter.napcat.command.commands

import io.github.autotweaker.api.types.agent.ToolApprove
import io.github.autotweaker.demo.adapter.napcat.command.Command
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.permission.Role

/**
 * 审批工具调用请求
 *
 * 用法: /approve <序号>
 * 当 LLM 请求调用工具时，用户通过此命令批准执行。
 * 序号对应工具调用请求列表中的编号（1-based）。
 */
class ApproveCommand : Command {

    override val name = "approve"
    override val description = "审批工具调用请求"
    override val usage = "/approve <序号>"
    override val requiredRole = Role.USER

    override suspend fun execute(context: CommandContext): String {
        if (context.args.isEmpty()) {
            return "用法: $usage"
        }

        val index = context.args[0].toIntOrNull()
            ?: return "无效的序号: ${context.args[0]}"

        // 获取用户的活跃会话
        val handle = context.sessionManager.getActiveSessionHandle(context.userId)
            ?: return "当前没有活跃会话，请先 /new 或 /enter 一个会话"

        // 从 MessageBridge 获取 callId
        val callId = context.messageBridge.getPendingCallId(handle.id, index)
            ?: return "无效的序号: $index，没有待审批的工具调用"

        return try {
            val approvals = listOf(ToolApprove(callId = callId, approved = true))
            context.core.session.approveToolCall(handle.id, approvals)
            "已审批工具调用: $callId"
        } catch (e: Exception) {
            "审批失败: ${e.message}"
        }
    }
}
