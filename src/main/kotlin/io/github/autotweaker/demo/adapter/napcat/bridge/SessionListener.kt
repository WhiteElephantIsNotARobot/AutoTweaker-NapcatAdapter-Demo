package io.github.autotweaker.demo.adapter.napcat.bridge

import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.trace.TraceRecorder
import io.github.autotweaker.api.trace.catching
import io.github.autotweaker.api.types.agent.CompactOutput
import io.github.autotweaker.api.types.agent.ToolApprove
import io.github.autotweaker.api.types.session.SessionContext
import io.github.autotweaker.api.types.session.SessionContextIndex
import io.github.autotweaker.api.types.session.SessionMessage
import io.github.autotweaker.api.types.session.SessionOutput
import io.github.autotweaker.api.types.session.ToolCallRequest
import kotlinx.serialization.json.Json
import io.github.autotweaker.api.types.tool.args.BashArgs
import io.github.autotweaker.api.types.tool.args.ReadArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions
import kotlinx.serialization.json.JsonElement
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

    private val logger = LoggerFactory.getLogger(this::class.java)
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
     * 拒绝指定会话的所有待审批工具调用
     *
     * @param sessionId 会话 ID
     * @param reason 拒绝原因（用户发送的消息内容）
     * @return true 表示有待审批调用并已拒绝，false 表示无待审批
     */
    suspend fun rejectPendingCalls(sessionId: UUID, reason: String): Boolean {
        val callIds = pendingToolCalls[sessionId] ?: return false
        if (callIds.isEmpty()) return false
        return trace.catching {
            val approvals = callIds.map { ToolApprove(callId = it, approved = false, reason = reason) }
            core.session.approveToolCall(sessionId, approvals)
            trace.add("session_reject_pending", "session=$sessionId, count=${callIds.size}")
            pendingToolCalls.remove(sessionId)
            true
        }.getOrElse { e ->
            logger.warn("Failed to reject pending tool calls  sessionId={}", sessionId, e)
            false
        }
    }

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
        val handle = trace.catching {
            core.session.getHandle(sessionId)
        }.getOrElse { e ->
            logger.warn("Failed to get session handle  sessionId={}", sessionId, e)
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
        return trace.catching {
            val approvals = callIds.map { ToolApprove(callId = it, approved = true) }
            core.session.approveToolCall(handleId, approvals)
            trace.add("session_approve", "session=$handleId, approvals=$approvals")
            pendingToolCalls.remove(handleId)
            "已审批全部 ${callIds.size} 个工具调用"
        }.getOrElse { e ->
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

                    sessionOutput.requests.forEach { req ->
                        sendToSession(sessionId, formatToolRequest(req))
                    }
                }
                is SessionOutput.LlmError -> {
                    sendToSession(sessionId, "LLM 错误: ${sessionOutput.content}")
                }
                is SessionOutput.Error -> {
                    sendToSession(sessionId, "错误: ${sessionOutput.error.message}")
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
        val handle = trace.catching {
            core.session.getHandle(sessionId)
        }.getOrElse { return null }
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



    private fun formatToolRequest(req: ToolCallRequest): String {
        val toolName = req.toolName
        val args = req.validatedArgs
        val reason = req.reason

        if (args == null) return "请求${toolName}"

        val toolKey = toolName.substringBefore("-")
        return trace.catching {
            when (toolKey) {
                "qq" -> "请求${renderQqArgs(Json.decodeFromJsonElement(QqToolFunctions.Args.serializer(), args), reason)}"
                "bash" -> "请求${renderBashArgs(Json.decodeFromJsonElement(BashArgs.serializer(), args), reason)}"
                "read" -> "请求${renderReadArgs(Json.decodeFromJsonElement(ReadArgs.serializer(), args), reason)}"
                else -> "请求${toolName}"
            }
        }.getOrElse { "请求${toolName}" }
    }

    private fun renderQqArgs(args: QqToolFunctions.Args, reason: String): String {
        val suffix = (if (reason.isNotBlank()) "（$reason）" else "")
        return when (args) {
            is QqToolFunctions.SendMessage -> "发送私聊消息给 ${args.userId}: ${args.message.take(50)}$suffix"
            is QqToolFunctions.SendGroupMessage -> "发送群消息到 ${args.groupId}: ${args.message.take(50)}$suffix"
            is QqToolFunctions.DeleteMessage -> "撤回消息 ${args.messageId}$suffix"
            is QqToolFunctions.GetMessage -> "获取消息详情 ${args.messageId}$suffix"
            is QqToolFunctions.GetFriendList -> "获取好友列表$suffix"
            is QqToolFunctions.GetGroupList -> "获取群列表$suffix"
            is QqToolFunctions.GetGroupMemberList -> "获取群 ${args.groupId} 的成员列表$suffix"
            is QqToolFunctions.GetGroupMemberInfo -> "获取群 ${args.groupId} 中用户 ${args.userId} 的信息$suffix"
            is QqToolFunctions.GetGroupMsgHistory -> "获取群 ${args.groupId} 的最近 ${args.count} 条消息$suffix"
            is QqToolFunctions.GetPrivateMsgHistory -> "获取与 ${args.userId} 的最近 ${args.count} 条私聊消息$suffix"
            is QqToolFunctions.KickGroupMember -> "将用户 ${args.userId} 踢出群 ${args.groupId}$suffix"
            is QqToolFunctions.BanGroupMember -> "禁言用户 ${args.userId} ${args.duration} 秒（群 ${args.groupId}）$suffix"
            is QqToolFunctions.SetGroupCard -> "设置群 ${args.groupId} 中用户 ${args.userId} 的名片为「${args.card}」$suffix"
            is QqToolFunctions.SetGroupName -> "设置群 ${args.groupId} 的名称为「${args.groupName}」$suffix"
            is QqToolFunctions.SetGroupAdmin -> if (args.enable) "设置用户 ${args.userId} 为群 ${args.groupId} 管理员$suffix" else "取消用户 ${args.userId} 在群 ${args.groupId} 的管理员$suffix"
            is QqToolFunctions.GetLoginInfo -> "获取机器人登录信息$suffix"
            is QqToolFunctions.GetStatus -> "获取机器人状态$suffix"
            is QqToolFunctions.GetVersionInfo -> "获取版本信息$suffix"
            is QqToolFunctions.GetImage -> "获取图片 ${args.file}$suffix"
            is QqToolFunctions.GetRecord -> "获取语音 ${args.file}$suffix"
            is QqToolFunctions.GetFile -> "获取文件 ${args.file}$suffix"
            is QqToolFunctions.GetForwardMsg -> "获取合并转发消息 ${args.id}$suffix"
        }
    }

    private fun renderBashArgs(args: BashArgs, reason: String): String {
        val envPart = if (args.envIds.isNotEmpty()) "并注入环境变量${args.envIds.joinToString("、")}" else ""
        val reasonPart = if (reason.isNotBlank()) "（$reason）" else ""
        return "运行以下命令${envPart}${reasonPart}\n${args.command}"
    }

    private fun renderReadArgs(args: ReadArgs, reason: String): String {
        val suffix = (if (reason.isNotBlank()) "（$reason）" else "")
        return when (args) {
            is ReadArgs.File -> "读取文件 ${args.filePath} 的第 ${args.startLine}-${args.endLine} 行$suffix"
            is ReadArgs.Summarize -> "总结文件 ${args.filePath} 的第 ${args.startLine}-${args.endLine} 行$suffix"
            is ReadArgs.Unicode -> "读取文件 ${args.filePath} 的前 ${args.maxChars} 字符$suffix"
        }
    }
}
