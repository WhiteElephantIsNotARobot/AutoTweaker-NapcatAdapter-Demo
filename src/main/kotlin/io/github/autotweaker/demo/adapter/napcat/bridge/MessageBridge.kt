package io.github.autotweaker.demo.adapter.napcat.bridge

import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.types.session.SessionOutput
import io.github.autotweaker.demo.adapter.napcat.api.NapCatApi
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.command.CommandRegistry
import io.github.autotweaker.demo.adapter.napcat.model.event.GroupMessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.MessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.PrivateMessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.message.MessageSegment
import io.github.autotweaker.demo.adapter.napcat.permission.PermissionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharedFlow
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

    /** 会话 → 消息发送上下文的映射，用于回复路由 */
    private val sessionContexts = ConcurrentHashMap<UUID, MessageContext>()

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
        val text = event.message.filterIsInstance<MessageSegment.Text>()
            .joinToString("") { it.text }
            .trim()

        if (text.isEmpty()) return

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
     *
     * 检测规则：
     * 1. 第一个消息段必须是 At 类型
     * 2. At.qq 必须等于机器人 QQ 号
     * 3. 提取后续所有 Text 段的文本拼接
     * 4. 空文本返回 null（忽略纯 @bot 消息）
     *
     * @param message 消息链
     * @param selfId 机器人 QQ 号
     * @return 提取的文本，未 @bot 或文本为空返回 null
     */
    private fun extractAtText(message: List<MessageSegment>, selfId: Long): String? {
        if (message.isEmpty()) return null

        val first = message[0]
        if (first !is MessageSegment.At) return null
        if (first.qq != selfId.toString()) return null

        // 提取后续文本段
        val text = message.drop(1)
            .filterIsInstance<MessageSegment.Text>()
            .joinToString("") { it.text }
            .trim()

        // 空文本（纯 @bot）返回 null
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
                sendReply(groupId, userId, "无法自动创建会话: ${e.message}")
                return
            }
        }

        // 记录会话上下文，用于回复路由
        sessionContexts[handle.id] = MessageContext(userId, groupId)

        // 确保输出监听器已启动
        ensureOutputListener(handle.id)

        try {
            core.session.send(handle.id, text)
        } catch (e: Exception) {
            logger.error("Failed to send message to session {}", handle.id, e)
            sendReply(groupId, userId, "发送失败: ${e.message}")
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
     * 确保会话的输出监听器已启动
     *
     * @param sessionId 会话 ID
     */
    private fun ensureOutputListener(sessionId: UUID) {
        if (!listeningSessions.add(sessionId)) return

        val handle = core.session.getHandle(sessionId) ?: return

        scope.launch {
            try {
                listenToOutput(sessionId, handle.output)
            } finally {
                listeningSessions.remove(sessionId)
            }
        }
    }

    private suspend fun listenToOutput(sessionId: UUID, output: SharedFlow<SessionOutput>) {
        val buffer = StringBuilder()

        output.collect { sessionOutput ->
            when (sessionOutput) {
                is SessionOutput.LlmDelta -> {
                    sessionOutput.delta.content?.let { buffer.append(it) }
                }
                is SessionOutput.ToolRequest -> {
                    // 有工具请求，先发送已收集的文本
                    flushBuffer(sessionId, buffer)
                    // 发送工具审批提示
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
                is SessionOutput.Tool -> {
                    logger.debug("Tool output: {} - {}", sessionOutput.output.name, sessionOutput.output.content)
                }
                is SessionOutput.LlmError -> {
                    flushBuffer(sessionId, buffer)
                    sendToSession(sessionId, "LLM 错误: ${sessionOutput.content}")
                }
                is SessionOutput.Error -> {
                    flushBuffer(sessionId, buffer)
                    sendToSession(sessionId, "错误: ${sessionOutput.error.message}")
                }
                is SessionOutput.Compact -> {
                    logger.debug("Session {} compacted", sessionId)
                }
            }
        }
    }

    private suspend fun flushBuffer(sessionId: UUID, buffer: StringBuilder) {
        if (buffer.isNotEmpty()) {
            sendToSession(sessionId, buffer.toString())
            buffer.clear()
        }
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
            sessionManager = sessionManager
        )
    }
}
