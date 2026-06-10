package io.github.autotweaker.demo.adapter.napcat.command.commands

import io.github.autotweaker.api.types.agent.ToolApprove
import io.github.autotweaker.api.trace.TraceRecorder
import io.github.autotweaker.demo.adapter.napcat.command.Command
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.permission.Role
import org.slf4j.LoggerFactory

/**
 * 拒绝工具调用请求
 *
 * 用法: /reject [序号] [理由]
 * 只有一个待审批工具时可省略序号。
 */
class RejectCommand : Command {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private lateinit var trace: TraceRecorder

    override val name = "reject"
    override val description = "拒绝工具调用请求"
    override val usage = "/reject [序号] [理由]"
    override val requiredRole = Role.USER

    override suspend fun execute(context: CommandContext): String {
        if (!::trace.isInitialized) trace = context.core.trace(this::class)
        val handle = context.sessionManager.getActiveSessionHandle(context.userId)
            ?: return "当前没有活跃会话，请先 /new 或 /enter 一个会话"

        val pendingCount = context.messageBridge.getPendingCount(handle.id)
        if (pendingCount == 0) return "没有待审批的工具调用"

        val index: Int
        val reason: String?

        if (context.args.isEmpty()) {
            // 无参数：只有一个待审批时自动选择
            if (pendingCount > 1) return "有待审批的工具调用，请指定序号: /reject <序号>"
            index = 1
            reason = null
        } else if (context.args[0].toIntOrNull() != null) {
            // 第一个参数是序号
            index = context.args[0].toIntOrNull()!!
            reason = context.args.getOrNull(1)?.let { context.args.drop(1).joinToString(" ") }
        } else {
            // 第一个参数不是数字，当作理由（单个工具时）
            if (pendingCount > 1) return "有待审批的工具调用，请指定序号: /reject <序号>"
            index = 1
            reason = context.args.joinToString(" ")
        }

        val callId = try {
            context.messageBridge.getPendingCallId(handle.id, index)
        } catch (e: Exception) {
            logger.warn("Failed to get pending call id  sessionId={}", handle.id, e)
            null
        } ?: return "无效的序号: $index"

        return try {
            val approvals = listOf(ToolApprove(callId = callId, reason = reason, approved = false))
            context.core.session.approveToolCall(handle.id, approvals)
            context.messageBridge.removePendingCall(handle.id, callId)
            trace.add("session_approve", "session=${handle.id}, approvals=$approvals")
            "已拒绝工具调用: $callId"
        } catch (e: Exception) {
            logger.error("Failed to reject tool call  sessionId={}  callId={}", handle.id, callId, e)
            trace.add("e", e.stackTraceToString())
            "拒绝失败，请稍后重试"
        }
    }
}
