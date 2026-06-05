package io.github.autotweaker.demo.adapter.napcat.bridge

import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.types.agent.CompactOutput
import io.github.autotweaker.api.types.agent.ToolApprove
import io.github.autotweaker.api.types.session.SessionContext
import io.github.autotweaker.api.types.session.SessionContextIndex
import io.github.autotweaker.api.types.session.SessionMessage
import io.github.autotweaker.api.types.session.SessionOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 会话输出和上下文监听器
 *
 * 负责监听 AutoTweaker 会话的输出流和上下文变化：
 * - 监听 ToolRequest，维护待审批工具调用列表
 * - 监听上下文变化，检测新增 AI 消息并发送
 * - 处理 LLM 错误和上下文压缩通知
 *
 * @property core CoreAPI 实例
 * @property scope 协程作用域，生命周期由适配器管理
 * @property sendToSession 将消息发送回用户的回调
 */
class SessionListener(
    private val core: CoreAPI,
    private val scope: CoroutineScope,
    private val sendToSession: suspend (sessionId: UUID, text: String) -> Unit
) {

    private val logger = LoggerFactory.getLogger(SessionListener::class.java)
    private val trace = core.trace(this::class)

    /** 正在监听输出的会话集合，防止重复监听 */
    private val listeningSessions = ConcurrentHashMap.newKeySet<UUID>()

    /** 正在监听上下文的会话集合，防止重复监听 */
    private val listeningContexts = ConcurrentHashMap.newKeySet<UUID>()

    /** 会话 → 上次上下文消息 ID 集合，用于检测新增消息 */
    private val lastMessageIds = ConcurrentHashMap<UUID, Set<UUID>>()

    /** 会话 → 待审批的工具调用 ID 列表（用于序号反查） */
    private val pendingToolCalls = ConcurrentHashMap<UUID, List<String>>()

    /**
     * 清除指定会话的所有待审批工具调用记录
     *
     * @param sessionId 会话 ID
     */
    fun clearPendingCalls(sessionId: UUID) {
        pendingToolCalls.remove(sessionId)
    }

    /**
     * 确保会话的输出和上下文监听器已启动
     */
    suspend fun ensureListeners(sessionId: UUID) {
        val handle = try {
            core.session.getHandle(sessionId)
        } catch (e: Exception) {
            logger.warn("Failed to get handle for session {}", sessionId, e)
            trace.add("e", e.toString())
            return
        }

        // 启动输出监听
        if (listeningSessions.add(sessionId)) {
            // 仅在首次监听时用当前消息 ID 初始化，避免重启后重发已有消息
            lastMessageIds.putIfAbsent(sessionId, getAllMessageIds(handle.context.value.index))
            scope.launch {
                try {
                    listenToOutput(sessionId, handle.output)
                } finally {
                    listeningSessions.remove(sessionId)
                }
            }
        }

        // 启动上下文监听
        if (listeningContexts.add(sessionId)) {
            scope.launch {
                try {
                    listenToContext(sessionId, handle.context)
                } finally {
                    listeningContexts.remove(sessionId)
                }
            }
        }
    }

    /**
     * 获取待审批工具调用数量
     */
    fun getPendingCount(sessionId: UUID): Int {
        return pendingToolCalls[sessionId]?.size ?: 0
    }

    /**
     * 获取待审批工具调用的 callId
     *
     * @param sessionId 会话 ID
     * @param index 序号（1-based）
     * @return callId，无效序号返回 null
     */
    fun getPendingCallId(sessionId: UUID, index: Int): String? {
        val callIds = pendingToolCalls[sessionId] ?: return null
        if (index < 1 || index > callIds.size) return null
        return callIds[index - 1]
    }

    /**
     * 移除已审批的工具调用记录
     */
    fun removePendingCall(sessionId: UUID, callId: String) {
        pendingToolCalls.computeIfPresent(sessionId) { _, ids ->
            ids.filter { it != callId }.ifEmpty { null }
        }
    }

    /**
     * 尝试审批用户活跃会话的所有待审批工具调用
     *
     * @param handleId 会话 handle ID
     * @return 回复文本，无待审批时返回 null
     */
    suspend fun tryApproveAll(handleId: UUID): String? {
        val callIds = pendingToolCalls[handleId] ?: return null
        if (callIds.isEmpty()) return null
        return try {
            val approvals = callIds.map { ToolApprove(callId = it, approved = true) }
            core.session.approveToolCall(handleId, approvals)
            trace.add("session_approve", "session=$handleId, approvals=$approvals")
            pendingToolCalls.remove(handleId)
            "已审批全部 ${callIds.size} 个工具调用"
        } catch (e: Exception) {
            trace.add("e", e.toString())
            "审批失败: ${e.message}"
        }
    }

    private suspend fun listenToOutput(sessionId: UUID, output: SharedFlow<SessionOutput>) {
        output.collect { sessionOutput ->
            when (sessionOutput) {
                // 流式输出全部丢弃，消息通过 context 监听发送
                is SessionOutput.LlmDelta -> { /* 丢弃 */ }
                is SessionOutput.Tool -> { /* 丢弃 */ }

                is SessionOutput.ToolRequest -> {
                    // 追加到当前待审批的 callId 列表
                    val callIds = sessionOutput.requests.map { it.callId }
                    pendingToolCalls.merge(sessionId, callIds) { old, new -> old + new }

                    val prompt = buildString {
                        appendLine("工具调用请求:")
                        sessionOutput.requests.forEachIndexed { index, req ->
                            appendLine("  ${index + 1}. ${req.name}(${req.arguments})")
                            req.reason?.let { appendLine("     原因: $it") }
                        }
                        appendLine()
                        appendLine("使用 /approve <序号> 审批")
                    }
                    sendToSession(sessionId, prompt)
                }
                is SessionOutput.LlmError -> {
                    sendToSession(sessionId, "LLM 错误: ${sessionOutput.content}")
                }
                is SessionOutput.Error -> {
                    sendToSession(sessionId, "错误: ${sessionOutput.error.message ?: "未知错误"}")
                }
                is SessionOutput.Compact -> {
                    when (sessionOutput.output.status) {
                        CompactOutput.Status.FINISHED -> {
                            val messageCount = getMessageCount(sessionId)
                            if (messageCount != null) {
                                sendToSession(sessionId, "上下文已压缩，剩余 $messageCount 条消息")
                            } else {
                                sendToSession(sessionId, "上下文已压缩")
                            }
                        }
                        CompactOutput.Status.FAILED -> {
                            sendToSession(sessionId, "上下文压缩失败")
                        }
                        // OUTPUTTING 丢弃
                        else -> {}
                    }
                }
            }
        }
    }

    /**
     * 监听会话上下文变化
     *
     * 当上下文更新时，检测新增的 AI 消息并发送。
     * 先更新状态再加载消息，避免竞态条件导致重复发送。
     */
    private suspend fun listenToContext(sessionId: UUID, context: StateFlow<SessionContext>) {
        context.collect { sessionContext ->
            val currentIds = getAllMessageIds(sessionContext.index)
            val newIds = synchronized(lastMessageIds) {
                val previousIds = lastMessageIds[sessionId] ?: emptySet()
                lastMessageIds[sessionId] = currentIds
                currentIds - previousIds
            }
            if (newIds.isNotEmpty()) {
                // 加载新增消息
                val messages = core.session.loadMessages(newIds.toList())
                // 只发送 AI/Assistant 消息，过滤掉用户消息和工具消息
                val aiMessages = messages.filterIsInstance<SessionMessage.Assistant>()
                for (msg in aiMessages) {
                    msg.content?.let { sendToSession(sessionId, it) }
                }
            }
        }
    }

    /**
     * 从 SessionContextIndex 提取所有消息 ID
     */
    private fun getAllMessageIds(index: SessionContextIndex): Set<UUID> {
        val ids = mutableSetOf<UUID>()

        // historyRounds 中的消息
        index.historyRounds?.forEach { round ->
            ids.add(round.userMessage)
            round.turns?.forEach { turn ->
                ids.add(turn.assistantMessage)
                turn.tools.forEach { tool ->
                    ids.add(tool.call)
                    ids.add(tool.result)
                }
            }
            round.finalAssistantMessage?.let { ids.add(it) }
        }

        // currentRound 中的消息
        index.currentRound?.let { round ->
            ids.add(round.userMessage)
            round.turns?.forEach { turn ->
                ids.add(turn.assistantMessage)
                turn.tools.forEach { tool ->
                    ids.add(tool.call)
                    ids.add(tool.result)
                }
            }
            round.assistantMessage?.let { ids.add(it) }
            round.pendingToolCalls?.let { ids.addAll(it) }
        }

        // summarizedMessage
        index.summarizedMessage?.let { ids.add(it) }

        return ids
    }

    /**
     * 获取会话中的消息总数
     */
    private suspend fun getMessageCount(sessionId: UUID): Int? {
        val handle = try {
            core.session.getHandle(sessionId)
        } catch (e: Exception) {
            trace.add("e", e.toString())
            return null
        }
        val context = handle.context.value
        return getMessageCountFromIndex(context.index)
    }

    /**
     * 从 SessionContextIndex 计算消息总数
     */
    private fun getMessageCountFromIndex(index: SessionContextIndex): Int {
        var count = 0

        index.historyRounds?.forEach { round ->
            count++ // userMessage
            round.turns?.forEach { turn ->
                count++ // assistantMessage
                count += turn.tools.size * 2 // call + result
            }
            if (round.finalAssistantMessage != null) count++
        }

        index.currentRound?.let { round ->
            count++ // userMessage
            round.turns?.forEach { turn ->
                count++ // assistantMessage
                count += turn.tools.size * 2 // call + result
            }
            if (round.assistantMessage != null) count++
            count += round.pendingToolCalls?.size ?: 0
        }

        return count
    }
}
