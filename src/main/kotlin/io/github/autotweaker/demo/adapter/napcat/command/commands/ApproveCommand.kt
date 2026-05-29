package io.github.autotweaker.demo.adapter.napcat.command.commands

import io.github.autotweaker.api.types.agent.ToolApprove
import io.github.autotweaker.demo.adapter.napcat.command.Command
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.permission.Role

/**
 * 审批工具调用请求
 *
 * 用法: /approve [序号] [理由]
 * 无参数时审批所有待审批的工具调用。
 */
class ApproveCommand : Command {

    override val name = "approve"
    override val description = "审批工具调用请求"
    override val usage = "/approve [序号] [理由]"
    override val requiredRole = Role.USER

    override suspend fun execute(context: CommandContext): String {
        val handle = context.sessionManager.getActiveSessionHandle(context.userId)
            ?: return "当前没有活跃会话，请先 /new 或 /enter 一个会话"

        val pendingCount = context.messageBridge.getPendingCount(handle.id)
        if (pendingCount == 0) return "没有待审批的工具调用"

        // 无参数：审批所有待审批的工具调用
        if (context.args.isEmpty()) {
            val callIds = context.messageBridge.getAllPendingCallIds(handle.id)
            return try {
                val approvals = callIds.map { ToolApprove(callId = it, approved = true) }
                context.core.session.approveToolCall(handle.id, approvals)
                context.messageBridge.clearPendingCalls(handle.id)
                "已审批全部 ${callIds.size} 个工具调用"
            } catch (e: Exception) {
                "审批失败: ${e.message}"
            }
        }

        val index: Int
        val reason: String?

        if (context.args[0].toIntOrNull() != null) {
            // 第一个参数是序号
            index = context.args[0].toInt()
            reason = context.args.getOrNull(1)?.let { context.args.drop(1).joinToString(" ") }
        } else {
            // 第一个参数不是数字，当作理由（单个工具时）
            if (pendingCount > 1) return "有待审批的工具调用，请指定序号: /approve <序号>"
            index = 1
            reason = context.args.joinToString(" ")
        }

        val callId = context.messageBridge.getPendingCallId(handle.id, index)
            ?: return "无效的序号: $index"

        return try {
            val approvals = listOf(ToolApprove(callId = callId, reason = reason, approved = true))
            context.core.session.approveToolCall(handle.id, approvals)
            context.messageBridge.removePendingCall(handle.id, callId)
            "已审批工具调用: $callId"
        } catch (e: Exception) {
            "审批失败: ${e.message}"
        }
    }
}
