package io.github.autotweaker.demo.adapter.napcat.ws

import io.github.autotweaker.demo.adapter.napcat.NapCatAdapter
import io.github.autotweaker.demo.adapter.napcat.model.event.*
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * NapCat WebSocket 客户端实现
 *
 * 使用 Ktor WebSocket 连接到 NapCat 服务器，提供：
 * - OneBot 11 API 调用（类型安全）
 * - 事件订阅与分发
 * - 自动心跳和重连支持
 *
 * @property json JSON 序列化配置，默认忽略未知字段
 */
class NapCatWsClientImpl(
    json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) : NapCatApiImpl(json), NapCatWsClient {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val trace = NapCatAdapter.core.trace(this::class)

    @Volatile private var wsSession: WebSocketSession? = null
    @Volatile private var client: HttpClient? = null
    @Volatile private var connectJob: Job? = null
    private val connected = AtomicBoolean(false)
    private val echoCounter = AtomicLong(0)
    private val pendingRequests = ConcurrentHashMap<String, Channel<JsonObject>>()

    // 事件处理器 - 使用 CopyOnWriteArrayList 保证线程安全
    private val eventHandlers = CopyOnWriteArrayList<suspend (OneBotEvent) -> Unit>()
    private val messageHandlers = CopyOnWriteArrayList<suspend (MessageEvent) -> Unit>()
    private val groupMessageHandlers = CopyOnWriteArrayList<suspend (GroupMessageEvent) -> Unit>()
    private val privateMessageHandlers = CopyOnWriteArrayList<suspend (PrivateMessageEvent) -> Unit>()
    private val noticeHandlers = CopyOnWriteArrayList<suspend (NoticeEvent) -> Unit>()
    private val requestHandlers = CopyOnWriteArrayList<suspend (RequestEvent) -> Unit>()
    private val metaHandlers = CopyOnWriteArrayList<suspend (MetaEvent) -> Unit>()

    // 生命周期处理器
    @Volatile private var connectHandler: (() -> Unit)? = null
    @Volatile private var disconnectHandler: ((CloseReason?) -> Unit)? = null
    @Volatile private var errorHandler: ((Throwable) -> Unit)? = null

    override val isConnected: Boolean get() = connected.get()

    // ==================== 连接管理 ====================

    override suspend fun connect(host: String, port: Int, token: String?) {
        if (!connected.compareAndSet(false, true)) return

        val hostPart = if (host.contains(":")) "[$host]" else host
        val url = buildString {
            append("ws://$hostPart:$port/")
            if (token != null) append("?access_token=$token")
        }

        client?.close()
        client = HttpClient {
            install(WebSockets)
        }

        connectJob = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            var retryDelay = INITIAL_RETRY_DELAY_MS
            var wasEverConnected = false
            while (isActive) {
                try {
                    client!!.webSocket(url) {
                        wsSession = this
                        logger.info("WebSocket connected  host={}  port={}", host, port)
                        retryDelay = INITIAL_RETRY_DELAY_MS
                        wasEverConnected = true
                        connectHandler?.invoke()

                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    val text = frame.readText()
                                    trace.add("response", text)
                                    launch {
                                        handleMessage(text)
                                    }
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logger.error("WebSocket error  host={}  port={}", host, port, e)
                            errorHandler?.invoke(e)
                        } finally {
                            val reason = withTimeoutOrNull(500) { closeReason.await() }
                            logger.warn("WebSocket closed  host={}  port={}  closeReason={}", host, port, reason)
                            wsSession = null
                            pendingRequests.forEach { (_, channel) ->
                                channel.close()
                            }
                            pendingRequests.clear()
                        }
                    }
                } catch (e: CancellationException) {
                    connected.set(false)
                    disconnectHandler?.invoke(null)
                    break
                } catch (e: Exception) {
                    if (wasEverConnected) {
                        logger.warn("Connection lost, retrying in {}ms  host={}  port={}", retryDelay, host, port, e)
                    } else {
                        logger.warn("Failed to connect, retrying in {}ms  host={}  port={}", retryDelay, host, port, e)
                        errorHandler?.invoke(e)
                    }
                }

                if (!isActive) break
                delay(retryDelay)
                retryDelay = (retryDelay * RETRY_BACKOFF_MULTIPLIER).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
    }

    override suspend fun disconnect() {
        connected.set(false)
        val session = wsSession
        wsSession = null
        session?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnect"))
        connectJob?.cancelAndJoin()
        client?.close()
        client = null
    }

    /** 关闭连接，会阻塞当前线程。协程环境中请使用 [disconnect]。 */
    override fun close() {
        runBlocking { disconnect() }
    }

    // ==================== 消息解析 ====================

    private suspend fun handleMessage(text: String) {
        try {
            val jsonElement = json.parseToJsonElement(text)
            val obj = jsonElement.jsonObject

            // 判断是 API 响应还是事件
            if (obj.containsKey("echo")) {
                handleApiResponse(obj)
            } else if (obj.containsKey("post_type")) {
                handleEvent(obj)
            }
        } catch (e: Exception) {
            logger.error("Failed to parse message  length={}", text.length, e)
        }
    }

    private suspend fun handleApiResponse(obj: JsonObject) {
        val echo = obj["echo"]?.jsonPrimitive?.content ?: return
        val channel = pendingRequests.remove(echo)
        channel?.send(obj)
    }

    private suspend fun handleEvent(obj: JsonObject) {
        try {
            val event = parseEvent(obj)
            if (event != null) {
                dispatchEvent(event)
            }
        } catch (e: Exception) {
            logger.error("Failed to parse event  postType={}", obj["post_type"]?.jsonPrimitive?.content, e)
        }
    }

    // ==================== 事件解析 ====================

    private fun parseEvent(obj: JsonObject): OneBotEvent? {
        val postType = obj["post_type"]?.jsonPrimitive?.content ?: return null

        return when (postType) {
            "message" -> parseMessageEvent(obj)
            "notice" -> parseNoticeEvent(obj)
            "request" -> parseRequestEvent(obj)
            "meta_event" -> parseMetaEvent(obj)
            else -> null
        }
    }

    private fun parseMessageEvent(obj: JsonObject): MessageEvent? {
        val messageType = obj["message_type"]?.jsonPrimitive?.content
        return when (messageType) {
            "private" -> json.decodeFromJsonElement(PrivateMessageEvent.serializer(), obj)
            "group" -> json.decodeFromJsonElement(GroupMessageEvent.serializer(), obj)
            else -> null
        }
    }

    private fun parseNoticeEvent(obj: JsonObject): NoticeEvent? {
        val noticeType = obj["notice_type"]?.jsonPrimitive?.content ?: return null

        return when (noticeType) {
            "group_increase" -> json.decodeFromJsonElement(GroupIncreaseNoticeEvent.serializer(), obj)
            "group_decrease" -> json.decodeFromJsonElement(GroupDecreaseNoticeEvent.serializer(), obj)
            "group_ban" -> json.decodeFromJsonElement(GroupBanNoticeEvent.serializer(), obj)
            "group_recall" -> json.decodeFromJsonElement(GroupRecallNoticeEvent.serializer(), obj)
            "group_admin" -> json.decodeFromJsonElement(GroupAdminNoticeEvent.serializer(), obj)
            "group_upload" -> json.decodeFromJsonElement(GroupUploadNoticeEvent.serializer(), obj)
            "group_card" -> json.decodeFromJsonElement(GroupCardNoticeEvent.serializer(), obj)
            "friend_add" -> json.decodeFromJsonElement(FriendAddNoticeEvent.serializer(), obj)
            "friend_recall" -> json.decodeFromJsonElement(FriendRecallNoticeEvent.serializer(), obj)
            "notify" -> json.decodeFromJsonElement(NotifyNoticeEvent.serializer(), obj)
            else -> null
        }
    }

    private fun parseRequestEvent(obj: JsonObject): RequestEvent? {
        val requestType = obj["request_type"]?.jsonPrimitive?.content

        return when (requestType) {
            "friend" -> json.decodeFromJsonElement(FriendRequestEvent.serializer(), obj)
            "group" -> json.decodeFromJsonElement(GroupRequestEvent.serializer(), obj)
            else -> null
        }
    }

    private fun parseMetaEvent(obj: JsonObject): MetaEvent? {
        val metaEventType = obj["meta_event_type"]?.jsonPrimitive?.content

        return when (metaEventType) {
            "heartbeat" -> json.decodeFromJsonElement(HeartbeatMetaEvent.serializer(), obj)
            "lifecycle" -> json.decodeFromJsonElement(LifecycleMetaEvent.serializer(), obj)
            else -> null
        }
    }

    // ==================== 事件分发 ====================

    private suspend fun dispatchEvent(event: OneBotEvent) {
        eventHandlers.forEach { handler ->
            try { handler(event) } catch (e: CancellationException) { throw e } catch (e: Exception) { logger.error("Failed to dispatch event  postType={}", event.javaClass.simpleName, e) }
        }

        when (event) {
            is GroupMessageEvent -> {
                messageHandlers.forEach { try { it(event) } catch (e: CancellationException) { throw e } catch (e: Exception) { logger.error("Failed to handle group message  groupId={}  userId={}", event.groupId, event.userId, e) } }
                groupMessageHandlers.forEach { try { it(event) } catch (e: CancellationException) { throw e } catch (e: Exception) { logger.error("Failed to handle group message  groupId={}  userId={}", event.groupId, event.userId, e) } }
            }
            is PrivateMessageEvent -> {
                messageHandlers.forEach { try { it(event) } catch (e: CancellationException) { throw e } catch (e: Exception) { logger.error("Failed to handle private message  userId={}", event.userId, e) } }
                privateMessageHandlers.forEach { try { it(event) } catch (e: CancellationException) { throw e } catch (e: Exception) { logger.error("Failed to handle private message  userId={}", event.userId, e) } }
            }
            is NoticeEvent -> noticeHandlers.forEach { try { it(event) } catch (e: CancellationException) { throw e } catch (e: Exception) { logger.error("Failed to handle notice event  noticeType={}", event.javaClass.simpleName, e) } }
            is RequestEvent -> requestHandlers.forEach { try { it(event) } catch (e: CancellationException) { throw e } catch (e: Exception) { logger.error("Failed to handle request event  requestType={}", event.javaClass.simpleName, e) } }
            is MetaEvent -> metaHandlers.forEach { try { it(event) } catch (e: CancellationException) { throw e } catch (e: Exception) { logger.error("Failed to handle meta event  metaType={}", event.javaClass.simpleName, e) } }
        }
    }

    // ==================== API 调用基础设施 ====================

    override suspend fun callApi(
        action: String,
        params: Map<String, JsonElement>?
    ): JsonObject {
        val echo = echoCounter.incrementAndGet().toString()
        val request = buildJsonObject {
            put("action", action)
            putJsonObject("params") {
                params?.forEach { (k, v) -> put(k, v) }
            }
            put("echo", echo)
        }

        trace.add("request", "action=$action, echo=$echo, params=$params")
        val channel = Channel<JsonObject>(1)
        pendingRequests[echo] = channel

        val session = wsSession ?: throw NapCatApiException(-1, "Not connected")
        session.send(Frame.Text(request.toString()))

        return withTimeout(CALL_API_TIMEOUT_MS) {
            try {
                channel.receive()
            } finally {
                // 超时或正常接收后都移除 channel
                pendingRequests.remove(echo)
            }
        }
    }

    // ==================== 事件订阅 ====================

    override fun onEvent(handler: suspend (OneBotEvent) -> Unit) {
        eventHandlers.add(handler)
    }

    override fun onMessageEvent(handler: suspend (MessageEvent) -> Unit) {
        messageHandlers.add(handler)
    }

    override fun onGroupMessageEvent(handler: suspend (GroupMessageEvent) -> Unit) {
        groupMessageHandlers.add(handler)
    }

    override fun onPrivateMessageEvent(handler: suspend (PrivateMessageEvent) -> Unit) {
        privateMessageHandlers.add(handler)
    }

    override fun onNoticeEvent(handler: suspend (NoticeEvent) -> Unit) {
        noticeHandlers.add(handler)
    }

    override fun onRequestEvent(handler: suspend (RequestEvent) -> Unit) {
        requestHandlers.add(handler)
    }

    override fun onMetaEvent(handler: suspend (MetaEvent) -> Unit) {
        metaHandlers.add(handler)
    }

    override fun onConnect(handler: () -> Unit) {
        connectHandler = handler
    }

    override fun onDisconnect(handler: (CloseReason?) -> Unit) {
        disconnectHandler = handler
    }

    override fun onError(handler: (Throwable) -> Unit) {
        errorHandler = handler
    }

    companion object {
        private const val CALL_API_TIMEOUT_MS = 30_000L
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 60_000L
        private const val RETRY_BACKOFF_MULTIPLIER = 2L
    }
}

/**
 * NapCat API 异常
 *
 * 当 API 调用失败时抛出。
 *
 * @property code 错误码（来自 OneBot 响应的 retcode）
 * @property message 错误信息
 */
class NapCatApiException(val code: Int, override val message: String) : Exception(message)
