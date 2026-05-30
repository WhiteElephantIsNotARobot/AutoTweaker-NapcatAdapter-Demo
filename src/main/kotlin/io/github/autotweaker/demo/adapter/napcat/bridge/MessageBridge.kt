package io.github.autotweaker.demo.adapter.napcat.bridge

import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.demo.adapter.napcat.api.NapCatApi
import io.github.autotweaker.demo.adapter.napcat.command.CommandContext
import io.github.autotweaker.demo.adapter.napcat.command.CommandRegistry
import io.github.autotweaker.demo.adapter.napcat.model.message.MessageChain
import io.github.autotweaker.demo.adapter.napcat.model.event.GroupMessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.MessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.PrivateMessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.message.MessageSegment
import io.github.autotweaker.demo.adapter.napcat.permission.PermissionManager
import kotlinx.coroutines.*
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

    private val contextBuilder = ContextBuilder(napCat)

    /** 会话 → 消息发送上下文的映射，用于回复路由 */
    private val sessionContexts = ConcurrentHashMap<UUID, MessageContext>()

    private val sessionListener = SessionListener(core, scope) { sessionId, text ->
        sendToSession(sessionId, text)
    }

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
            else -> logger.debug("Unhandled message event type")
        }
    }

    private suspend fun handleGroupMessage(event: GroupMessageEvent) {
        val userId = event.userId
        val groupId = event.groupId
        val selfId = event.selfId

        // 检测 @bot
        val text = extractAtText(event.message, selfId)
        if (text == null) {
            // 没有 @bot，检查 "a" 快捷审批（需要授权）
            if (event.rawMessage.trim() == "a") {
                if (!permissionManager.isAuthorized(userId)) return
                val reply = tryApproveAll(userId)
                if (reply != null) {
                    sendReply(groupId, userId, reply)
                }
            }
            return
        }

        // 检查授权
        if (!permissionManager.isAuthorized(userId)) {
            sendReply(groupId, userId, "未授权，请联系管理员添加白名单")
            return
        }

        logger.debug("Group message from user {} in group {}, length={}", userId, groupId, text.length)

        // 尝试命令分发
        val context = createContext(userId, groupId)
        val commandResult = commandRegistry.dispatch(text, context)
        if (commandResult != null) {
            sendReply(groupId, userId, commandResult)
            return
        }

        // 普通消息，转发到活跃会话（传递消息链用于检测合并转发）
        forwardToSession(userId, groupId, text, event.message)
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

        // 单字符 "a" 快捷审批所有待审批工具调用
        if (text == "a") {
            val reply = tryApproveAll(userId)
            if (reply != null) {
                sendReply(null, userId, reply)
                return
            }
        }

        logger.debug("Private message from user {}, length={}", userId, text.length)

        // 尝试命令分发
        val context = createContext(userId, null)
        val commandResult = commandRegistry.dispatch(text, context)
        if (commandResult != null) {
            sendReply(null, userId, commandResult)
            return
        }

        // 普通消息，转发到活跃会话（传递消息链用于检测合并转发）
        forwardToSession(userId, null, text, event.message)
    }

    /**
     * 尝试审批用户活跃会话的所有待审批工具调用
     *
     * @return 回复文本，无活跃会话或无待审批时返回 null
     */
    private suspend fun tryApproveAll(userId: Long): String? {
        val handle = sessionManager.getActiveSessionHandle(userId) ?: return null
        return sessionListener.tryApproveAll(handle.id)
    }

    /**
     * 从消息链中提取 @bot 后的文本
     */
    private fun extractAtText(message: List<MessageSegment>, selfId: Long): String? {
        if (message.isEmpty()) return null

        val atIndex = message.indexOfFirst { it is MessageSegment.At && it.qq == selfId.toString() }
        if (atIndex < 0) return null

        val text = message.drop(atIndex + 1)
            .filterIsInstance<MessageSegment.Text>()
            .joinToString("") { it.text }
            .trim()

        return text.ifEmpty { null }
    }

    private suspend fun forwardToSession(userId: Long, groupId: Long?, text: String, messageChain: MessageChain? = null) {
        var handle = sessionManager.getActiveSessionHandle(userId)

        // 无活跃会话，自动创建（清理旧会话上下文）
        if (handle == null) {
            try {
                sessionManager.getActiveSession(userId)?.let { sessionContexts.remove(it) }
                handle = sessionManager.autoCreateSession(userId)
                sendReply(groupId, userId, "已自动创建并进入会话")
            } catch (e: Exception) {
                logger.error("Failed to auto-create session for user {}", userId, e)
                sendReply(groupId, userId, "无法自动创建会话，请稍后重试")
                return
            }
        }

        // 记录会话上下文，用于回复路由
        sessionContexts[handle.id] = MessageContext(userId, groupId)

        // 确保监听器已启动
        sessionListener.ensureListeners(handle.id)

        // 新消息开始新轮次，清除旧的待审批记录
        sessionListener.clearPendingCalls(handle.id)

        try {
            // 检测消息链中的合并转发
            val forwardSegments = messageChain?.filterIsInstance<MessageSegment.Forward>() ?: emptyList()
            val processedText = if (forwardSegments.isNotEmpty()) {
                contextBuilder.processForwardSegments(text, forwardSegments)
            } else {
                text
            }

            // 构建带上下文的消息
            val messageWithContext = contextBuilder.buildMessageWithContext(groupId, userId, processedText)
            core.session.send(handle.id, messageWithContext)
        } catch (e: Exception) {
            logger.error("Failed to send message to session {}", handle.id, e)
            sendReply(groupId, userId, "消息发送失败，请稍后重试")
        }
    }

    /**
     * 获取待审批工具调用数量
     */
    fun getPendingCount(sessionId: UUID): Int {
        return sessionListener.getPendingCount(sessionId)
    }

    /**
     * 获取待审批工具调用的 callId
     *
     * @param sessionId 会话 ID
     * @param index 序号（1-based）
     * @return callId，无效序号返回 null
     */
    fun getPendingCallId(sessionId: UUID, index: Int): String? {
        return sessionListener.getPendingCallId(sessionId, index)
    }

    /**
     * 移除已审批的工具调用记录
     */
    fun removePendingCall(sessionId: UUID, callId: String) {
        sessionListener.removePendingCall(sessionId, callId)
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
