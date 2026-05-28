package io.github.autotweaker.demo.adapter.napcat.bridge

import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.types.session.SessionContext
import io.github.autotweaker.api.types.session.SessionContextIndex
import io.github.autotweaker.api.types.session.SessionMessage
import io.github.autotweaker.api.types.session.SessionOutput
import io.github.autotweaker.demo.adapter.napcat.api.NapCatApi
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.command.CommandRegistry
import io.github.autotweaker.demo.adapter.napcat.model.data.ForwardMessage
import io.github.autotweaker.demo.adapter.napcat.model.event.GroupMessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.MessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.PrivateMessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.message.MessageSegment
import io.github.autotweaker.demo.adapter.napcat.permission.PermissionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 消息桥接器
 *
 * 负责 NapCat 消息与 AutoTweaker CoreAPI 之间的桥接：
 * - 接收群聊/私聊消息，解析 @bot 触发
 * - 分发命令到 CommandRegistry
 * - 转发普通消息到活跃会话
 * - 监听会话输出，发送回复到 NapCat
 * - 监听会话上下文变化，发送新增 AI 消息
 *
 * @property core CoreAPI 实例
 * @property napCat NapCat API 实例
 * @property sessionManager 会话管理器
 * @property commandRegistry 命令注册表
 * @property permissionManager 权限管理器
 * @property scope 协程作用域，生命周期由适配器管理
 */
class MessageBridge(
    private val core: CoreAPI,
    private val napCat: NapCatApi,
    private val sessionManager: SessionManager,
    private val commandRegistry: CommandRegistry,
    private val permissionManager: PermissionManager,
    private val scope: CoroutineScope
) {

    private val logger = LoggerFactory.getLogger(MessageBridge::class.java)

    /** 正在监听输出的会话集合，防止重复监听 */
    private val listeningSessions = ConcurrentHashMap.newKeySet<UUID>()

    /** 正在监听上下文的会话集合，防止重复监听 */
    private val listeningContexts = ConcurrentHashMap.newKeySet<UUID>()

    /** 会话 → 消息发送上下文的映射，用于回复路由 */
    private val sessionContexts = ConcurrentHashMap<UUID, MessageContext>()

    /** 会话 → 上次上下文消息 ID 集合，用于检测新增消息 */
    private val lastMessageIds = ConcurrentHashMap<UUID, Set<UUID>>()

    /** 会话 → 待审批的工具调用 ID 列表（用于序号反查） */
    private val pendingToolCalls = ConcurrentHashMap<UUID, List<String>>()

    /**
     * 消息发送上下文
     *
     * @property userId 发送者 QQ 号
     * @property groupId 群号，null 表示私聊
     */
    private data class MessageContext(val userId: Long, val groupId: Long?)

    /**
     * 处理消息事件
     *
     * @param event 消息事件（群聊或私聊）
     */
    suspend fun handleMessage(event: MessageEvent) {
        when (event) {
            is GroupMessageEvent -> handleGroupMessage(event)
            is PrivateMessageEvent -> handlePrivateMessage(event)
        }
    }

    private suspend fun handleGroupMessage(event: GroupMessageEvent) {
        val userId = event.userId
        val groupId = event.groupId
        val selfId = event.selfId

        // 检测 @bot
        val text = extractAtText(event.message, selfId)
        if (text == null) {
            // 没有 @bot，忽略
            return
        }

        // 检查授权
        if (!permissionManager.isAuthorized(userId)) {
            sendReply(groupId, userId, "未授权，请联系管理员添加白名单")
            return
        }

        logger.debug("Group message from user {} in group {}: {}", userId, groupId, text)

        // 尝试命令分发
        val context = createContext(userId, groupId)
        val commandResult = commandRegistry.dispatch(text, context)
        if (commandResult != null) {
            sendReply(groupId, userId, commandResult)
            return
        }

        // 普通消息，转发到活跃会话
        forwardToSession(userId, groupId, text)
    }

    private suspend fun handlePrivateMessage(event: PrivateMessageEvent) {
        val userId = event.userId
        val text = event.rawMessage.trim()

        if (text.isEmpty()) return

        // 检查授权
        if (!permissionManager.isAuthorized(userId)) {
            sendReply(null, userId, "未授权，请联系管理员添加白名单")
            return
        }

        logger.debug("Private message from user {}: {}", userId, text)

        // 尝试命令分发
        val context = createContext(userId, null)
        val commandResult = commandRegistry.dispatch(text, context)
        if (commandResult != null) {
            sendReply(null, userId, commandResult)
            return
        }

        // 普通消息，转发到活跃会话
        forwardToSession(userId, null, text)
    }

    /**
     * 从消息链中提取 @bot 后的文本
     */
    private fun extractAtText(message: List<MessageSegment>, selfId: Long): String? {
        if (message.isEmpty()) return null

        val first = message[0]
        if (first !is MessageSegment.At) return null
        if (first.qq != selfId.toString()) return null

        val text = message.drop(1)
            .filterIsInstance<MessageSegment.Text>()
            .joinToString("") { it.text }
            .trim()

        return text.ifEmpty { null }
    }

    private suspend fun forwardToSession(userId: Long, groupId: Long?, text: String) {
        var handle = sessionManager.getActiveSessionHandle(userId)

        // 无活跃会话，自动创建
        if (handle == null) {
            try {
                handle = sessionManager.autoCreateSession(userId)
                sendReply(groupId, userId, "已自动创建并进入会话")
            } catch (e: Exception) {
                logger.error("Failed to auto-create session for user {}", userId, e)
                sendReply(groupId, userId, "无法自动创建会话: ${e.message}")
                return
            }
        }

        // 记录会话上下文，用于回复路由
        sessionContexts[handle.id] = MessageContext(userId, groupId)

        // 确保监听器已启动
        ensureListeners(handle.id)

        // 新消息开始新轮次，清除旧的待审批记录
        pendingToolCalls.remove(handle.id)

        try {
            // 构建带上下文的消息
            val messageWithContext = buildMessageWithContext(groupId, userId, text)
            core.session.send(handle.id, messageWithContext)
        } catch (e: Exception) {
            logger.error("Failed to send message to session {}", handle.id, e)
            sendReply(groupId, userId, "发送失败: ${e.message}")
        }
    }

    /**
     * 构建带上下文的消息
     *
     * 在消息开头注入会话所在的群/私聊信息，以及最近的群消息历史和环境信息
     */
    private suspend fun buildMessageWithContext(groupId: Long?, userId: Long, text: String): String {
        return try {
            val contextBuilder = StringBuilder()

            // 注入会话所在的群/私聊信息
            contextBuilder.appendLine("<session-info>")
            if (groupId != null) {
                // 群聊：获取群名并注入群号和群名
                val groupName = try {
                    napCat.getGroupList().find { it.groupId == groupId }?.groupName
                } catch (e: Exception) {
                    logger.warn("Failed to get group list for group name: {}", e.message)
                    null
                }
                contextBuilder.appendLine("会话类型：群聊")
                contextBuilder.appendLine("群号：$groupId")
                if (groupName != null) {
                    contextBuilder.appendLine("群名：$groupName")
                }
            } else {
                // 私聊
                contextBuilder.appendLine("会话类型：私聊")
                contextBuilder.appendLine("用户 QQ 号：$userId")
            }
            contextBuilder.appendLine("</session-info>")
            contextBuilder.appendLine()

            // 群聊时获取最近的群消息历史
            if (groupId != null) {
                val history = napCat.getGroupMsgHistory(groupId, count = 20)

                // 构建上下文
                contextBuilder.appendLine("<context>")

                // 获取群成员列表用于获取昵称
                val members = try {
                    napCat.getGroupMemberList(groupId)
                } catch (e: Exception) {
                    emptyList()
                }

                // 收集合并转发消息
                val forwardMessages = mutableMapOf<String, List<ForwardMessage>>()

                // 构建消息历史
                history.reversed().forEach { msg ->
                    val nickname = members.find { it.userId == msg.userId }?.let {
                        it.card.ifEmpty { it.nickname }
                    } ?: msg.sender.nickname.ifEmpty { msg.sender.userId.toString() }
                    val time = java.time.Instant.ofEpochSecond(msg.time)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))

                    // 检测合并转发消息
                    val forwardId = extractForwardId(msg.rawMessage)
                    if (forwardId != null) {
                        try {
                            val forwardContent = napCat.getForwardMsg(forwardId)
                            forwardMessages[forwardId] = forwardContent
                            contextBuilder.appendLine("[$time] $nickname: [合并转发消息 id=$forwardId]")
                        } catch (e: Exception) {
                            logger.warn("Failed to get forward message {}: {}", forwardId, e.message)
                            contextBuilder.appendLine("[$time] $nickname: ${msg.rawMessage}")
                        }
                    } else {
                        contextBuilder.appendLine("[$time] $nickname: ${msg.rawMessage}")
                    }
                }

                // 添加合并转发消息内容
                if (forwardMessages.isNotEmpty()) {
                    contextBuilder.appendLine()
                    contextBuilder.appendLine("<forward>")
                    forwardMessages.forEach { (id, messages) ->
                        contextBuilder.appendLine("  <forward id=\"$id\">")
                        messages.forEach { msg ->
                            val nickname = members.find { it.userId == msg.sender.userId }?.let {
                                it.card.ifEmpty { it.nickname }
                            } ?: msg.sender.nickname.ifEmpty { msg.sender.userId.toString() }
                            val time = java.time.Instant.ofEpochSecond(msg.time)
                                .atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                            contextBuilder.appendLine("    [$time] $nickname: ${msg.rawMessage}")
                        }
                        contextBuilder.appendLine("  </forward>")
                    }
                    contextBuilder.appendLine("</forward>")
                }

                contextBuilder.appendLine("</context>")
                contextBuilder.appendLine()
            }

            contextBuilder.appendLine("<environment>用户所在平台：QQ，请注意输出格式</environment>")
            contextBuilder.appendLine()
            contextBuilder.append(text)

            contextBuilder.toString()
        } catch (e: Exception) {
            logger.error("Failed to build message context", e)
            // 失败时只发送原始消息
            text
        }
    }

    /**
     * 从 raw_message 中提取合并转发消息 ID
     *
     * @param rawMessage 原始消息文本
     * @return 合并转发消息 ID，如果不是合并转发消息返回 null
     */
    private fun extractForwardId(rawMessage: String): String? {
        val pattern = "\\[CQ:forward,id=(\\d+)\\]".toRegex()
        return pattern.find(rawMessage)?.groupValues?.get(1)
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

    private suspend fun sendReply(groupId: Long?, userId: Long, text: String) {
        if (text.isEmpty()) return
        try {
            val message = listOf(MessageSegment.Text(text))
            if (groupId != null) {
                napCat.sendGroupMessage(groupId, message)
            } else {
                napCat.sendPrivateMessage(userId, message)
            }
        } catch (e: Exception) {
            logger.error("Failed to send reply to user {} group {}", userId, groupId, e)
        }
    }

    /**
     * 确保会话的输出和上下文监听器已启动
     */
    private fun ensureListeners(sessionId: UUID) {
        val handle = core.session.getHandle(sessionId) ?: return

        // 启动输出监听
        if (listeningSessions.add(sessionId)) {
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

    private suspend fun listenToOutput(sessionId: UUID, output: SharedFlow<SessionOutput>) {
        output.collect { sessionOutput ->
            when (sessionOutput) {
                // 流式输出全部丢弃，消息通过 context 监听发送
                is SessionOutput.LlmDelta -> { /* 丢弃 */ }
                is SessionOutput.Tool -> { /* 丢弃 */ }

                is SessionOutput.ToolRequest -> {
                    // 覆盖为当前待审批的 callId 列表
                    val callIds = sessionOutput.requests.map { it.callId }
                    pendingToolCalls[sessionId] = callIds

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
                    sendToSession(sessionId, "错误: ${sessionOutput.error.message}")
                }
                is SessionOutput.Compact -> {
                    when (sessionOutput.output.status) {
                        io.github.autotweaker.api.types.agent.CompactOutput.Status.FINISHED -> {
                            val messageCount = getMessageCount(sessionId)
                            sendToSession(sessionId, "上下文已压缩，剩余 $messageCount 条消息")
                        }
                        io.github.autotweaker.api.types.agent.CompactOutput.Status.FAILED -> {
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
            val previousIds = lastMessageIds[sessionId] ?: emptySet()

            // 先更新状态，避免竞态条件
            lastMessageIds[sessionId] = currentIds

            // 找出新增的消息 ID
            val newIds = currentIds - previousIds
            if (newIds.isNotEmpty()) {
                // 加载新增消息
                val messages = core.session.loadMessages(newIds.toList())
                if (messages != null) {
                    // 只发送 AI/Assistant 消息，过滤掉用户消息和工具消息
                    val aiMessages = messages.filterIsInstance<SessionMessage.Assistant>()
                    for (msg in aiMessages) {
                        msg.content?.let { sendToSession(sessionId, it) }
                    }
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
    private fun getMessageCount(sessionId: UUID): Int {
        val handle = core.session.getHandle(sessionId) ?: return 0
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

    private suspend fun sendToSession(sessionId: UUID, text: String) {
        val ctx = sessionContexts[sessionId]
        if (ctx != null) {
            sendReply(ctx.groupId, ctx.userId, text)
        } else {
            logger.warn("No message context for session {}", sessionId)
        }
    }

    private fun createContext(userId: Long, groupId: Long?): CommandContext {
        return CommandContext(
            userId = userId,
            groupId = groupId,
            role = permissionManager.getRole(userId),
            args = emptyList(),
            core = core,
            napCat = napCat,
            permissionManager = permissionManager,
            sessionManager = sessionManager,
            messageBridge = this
        )
    }
}
