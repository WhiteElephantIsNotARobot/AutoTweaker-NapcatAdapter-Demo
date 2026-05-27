package io.github.autotweaker.demo.adapter.napcat.ws

import io.github.autotweaker.demo.adapter.napcat.model.data.*
import io.github.autotweaker.demo.adapter.napcat.model.event.*
import io.github.autotweaker.demo.adapter.napcat.model.message.MessageChain
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class NapCatWsClientImpl(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) : NapCatWsClient {

    private val logger = LoggerFactory.getLogger(NapCatWsClient::class.java)

    private var wsSession: WebSocketSession? = null
    private var client: HttpClient? = null
    private var connectJob: Job? = null
    private val connected = AtomicBoolean(false)
    private val echoCounter = AtomicLong(0)
    private val pendingRequests = ConcurrentHashMap<String, Channel<JsonObject>>()

    // 事件处理器 - 使用 CopyOnWriteArrayList 保证线程安全
    private val eventHandlers = java.util.concurrent.CopyOnWriteArrayList<suspend (OneBotEvent) -> Unit>()
    private val messageHandlers = java.util.concurrent.CopyOnWriteArrayList<suspend (MessageEvent) -> Unit>()
    private val groupMessageHandlers = java.util.concurrent.CopyOnWriteArrayList<suspend (GroupMessageEvent) -> Unit>()
    private val privateMessageHandlers = java.util.concurrent.CopyOnWriteArrayList<suspend (PrivateMessageEvent) -> Unit>()
    private val noticeHandlers = java.util.concurrent.CopyOnWriteArrayList<suspend (NoticeEvent) -> Unit>()
    private val requestHandlers = java.util.concurrent.CopyOnWriteArrayList<suspend (RequestEvent) -> Unit>()
    private val metaHandlers = java.util.concurrent.CopyOnWriteArrayList<suspend (MetaEvent) -> Unit>()

    // 生命周期处理器
    @Volatile private var connectHandler: (() -> Unit)? = null
    @Volatile private var disconnectHandler: ((CloseReason?) -> Unit)? = null
    @Volatile private var errorHandler: ((Throwable) -> Unit)? = null

    override val isConnected: Boolean get() = connected.get()

    override suspend fun connect(host: String, port: Int, token: String?) {
        if (connected.get()) return

        val url = buildString {
            append("ws://$host:$port")
            if (token != null) append("?access_token=$token")
        }

        // 关闭旧的 HttpClient
        client?.close()
        client = HttpClient {
            install(WebSockets)
        }

        connectJob = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                client!!.webSocket(url) {
                    wsSession = this
                    connected.set(true)
                    logger.info("Connected to NapCat WS at $host:$port")
                    connectHandler?.invoke()

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                launch {
                                    handleMessage(text)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("WS error", e)
                        errorHandler?.invoke(e)
                    } finally {
                        connected.set(false)
                        wsSession = null
                        // 清理所有待处理的请求
                        pendingRequests.forEach { (_, channel) ->
                            channel.close()
                        }
                        pendingRequests.clear()
                        disconnectHandler?.invoke(null)
                    }
                }
            } catch (e: Exception) {
                logger.error("Connection failed", e)
                connected.set(false)
                errorHandler?.invoke(e)
            }
        }
    }

    override suspend fun disconnect() {
        connected.set(false)
        wsSession?.close()
        connectJob?.cancelAndJoin()
        client?.close()
        client = null
    }

    override fun close() {
        runBlocking { disconnect() }
    }

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
            logger.error("Failed to parse message: $text", e)
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
            logger.error("Failed to parse event", e)
        }
    }

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

    private suspend fun dispatchEvent(event: OneBotEvent) {
        eventHandlers.forEach { handler ->
            try { handler(event) } catch (e: Exception) { logger.error("Event handler error", e) }
        }

        when (event) {
            is GroupMessageEvent -> {
                messageHandlers.forEach { try { it(event) } catch (e: Exception) { logger.error("Handler error", e) } }
                groupMessageHandlers.forEach { try { it(event) } catch (e: Exception) { logger.error("Handler error", e) } }
            }
            is PrivateMessageEvent -> {
                messageHandlers.forEach { try { it(event) } catch (e: Exception) { logger.error("Handler error", e) } }
                privateMessageHandlers.forEach { try { it(event) } catch (e: Exception) { logger.error("Handler error", e) } }
            }
            is NoticeEvent -> noticeHandlers.forEach { try { it(event) } catch (e: Exception) { logger.error("Handler error", e) } }
            is RequestEvent -> requestHandlers.forEach { try { it(event) } catch (e: Exception) { logger.error("Handler error", e) } }
            is MetaEvent -> metaHandlers.forEach { try { it(event) } catch (e: Exception) { logger.error("Handler error", e) } }
        }
    }

    // API 调用
    private suspend fun callApi(
        action: String,
        params: Map<String, JsonElement>? = null
    ): JsonObject {
        val echo = echoCounter.incrementAndGet().toString()
        val request = buildJsonObject {
            put("action", action)
            putJsonObject("params") {
                params?.forEach { (k, v) -> put(k, v) }
            }
            put("echo", echo)
        }

        val channel = Channel<JsonObject>(1)
        pendingRequests[echo] = channel

        val session = wsSession ?: throw NapCatApiException(-1, "Not connected")
        session.send(Frame.Text(request.toString()))

        return withTimeout(30000) {
            try {
                channel.receive()
            } finally {
                // 超时或正常接收后都移除 channel
                pendingRequests.remove(echo)
            }
        }
    }

    private suspend fun <T> callApiAndDecode(
        action: String,
        params: Map<String, JsonElement>? = null,
        deserializer: DeserializationStrategy<T>
    ): T {
        val response = callApi(action, params)
        val status = response["status"]?.jsonPrimitive?.content
        val retcode = response["retcode"]?.jsonPrimitive?.int
        val message = response["message"]?.jsonPrimitive?.content

        if (status != "ok" || retcode != 0) {
            throw NapCatApiException(retcode ?: -1, message ?: "Unknown error")
        }

        val data = response["data"]
        if (data == null || data is JsonNull) {
            throw NapCatApiException(-1, "Response data is null")
        }

        return json.decodeFromJsonElement(deserializer, data)
    }

    // NapCatApi 实现
    override suspend fun getLoginInfo(): LoginInfo =
        callApiAndDecode("get_login_info", deserializer = LoginInfo.serializer())

    override suspend fun getStatus(): BotStatus =
        callApiAndDecode("get_status", deserializer = BotStatus.serializer())

    override suspend fun getVersionInfo(): VersionInfo =
        callApiAndDecode("get_version_info", deserializer = VersionInfo.serializer())

    override suspend fun sendPrivateMessage(userId: Long, message: MessageChain): MessageResult {
        val params = buildMap {
            put("user_id", json.encodeToJsonElement(userId.toString()))
            put("message", json.encodeToJsonElement(message))
        }
        return callApiAndDecode("send_private_msg", params, MessageResult.serializer())
    }

    override suspend fun sendGroupMessage(groupId: Long, message: MessageChain): MessageResult {
        val params = buildMap {
            put("group_id", json.encodeToJsonElement(groupId.toString()))
            put("message", json.encodeToJsonElement(message))
        }
        return callApiAndDecode("send_group_msg", params, MessageResult.serializer())
    }

    override suspend fun deleteMessage(messageId: Int) {
        val params = buildMap {
            put("message_id", json.encodeToJsonElement(messageId))
        }
        callApiAndDecode("delete_msg", params, JsonObject.serializer())
    }

    override suspend fun getMessage(messageId: Int): MessageDetail {
        val params = buildMap {
            put("message_id", json.encodeToJsonElement(messageId))
        }
        return callApiAndDecode("get_msg", params, MessageDetail.serializer())
    }

    override suspend fun getFriendList(): List<FriendInfo> =
        callApiAndDecode("get_friend_list", deserializer = serializer<List<FriendInfo>>())

    override suspend fun getGroupList(noCache: Boolean): List<GroupInfo> {
        val params = buildMap {
            put("no_cache", json.encodeToJsonElement(noCache))
        }
        return callApiAndDecode("get_group_list", params, serializer<List<GroupInfo>>())
    }

    override suspend fun getGroupMemberList(groupId: Long): List<GroupMemberInfo> {
        val params = buildMap {
            put("group_id", json.encodeToJsonElement(groupId.toString()))
        }
        return callApiAndDecode("get_group_member_list", params, serializer<List<GroupMemberInfo>>())
    }

    override suspend fun getGroupMemberInfo(groupId: Long, userId: Long): GroupMemberInfo {
        val params = buildMap {
            put("group_id", json.encodeToJsonElement(groupId.toString()))
            put("user_id", json.encodeToJsonElement(userId.toString()))
        }
        return callApiAndDecode("get_group_member_info", params, GroupMemberInfo.serializer())
    }

    override suspend fun setGroupKick(groupId: Long, userId: Long, rejectAddRequest: Boolean) {
        val params = buildMap {
            put("group_id", json.encodeToJsonElement(groupId.toString()))
            put("user_id", json.encodeToJsonElement(userId.toString()))
            put("reject_add_request", json.encodeToJsonElement(rejectAddRequest))
        }
        callApiAndDecode("set_group_kick", params, JsonObject.serializer())
    }

    override suspend fun setGroupBan(groupId: Long, userId: Long, duration: Int) {
        val params = buildMap {
            put("group_id", json.encodeToJsonElement(groupId.toString()))
            put("user_id", json.encodeToJsonElement(userId.toString()))
            put("duration", json.encodeToJsonElement(duration))
        }
        callApiAndDecode("set_group_ban", params, JsonObject.serializer())
    }

    override suspend fun setGroupCard(groupId: Long, userId: Long, card: String) {
        val params = buildMap {
            put("group_id", json.encodeToJsonElement(groupId.toString()))
            put("user_id", json.encodeToJsonElement(userId.toString()))
            put("card", json.encodeToJsonElement(card))
        }
        callApiAndDecode("set_group_card", params, JsonObject.serializer())
    }

    override suspend fun setGroupName(groupId: Long, groupName: String) {
        val params = buildMap {
            put("group_id", json.encodeToJsonElement(groupId.toString()))
            put("group_name", json.encodeToJsonElement(groupName))
        }
        callApiAndDecode("set_group_name", params, JsonObject.serializer())
    }

    override suspend fun setGroupAdmin(groupId: Long, userId: Long, enable: Boolean) {
        val params = buildMap {
            put("group_id", json.encodeToJsonElement(groupId.toString()))
            put("user_id", json.encodeToJsonElement(userId.toString()))
            put("enable", json.encodeToJsonElement(enable))
        }
        callApiAndDecode("set_group_admin", params, JsonObject.serializer())
    }

    override suspend fun getImage(file: String): FileInfo {
        val params = buildMap {
            put("file", json.encodeToJsonElement(file))
        }
        return callApiAndDecode("get_image", params, FileInfo.serializer())
    }

    override suspend fun getRecord(file: String, outFormat: String?): FileInfo {
        val params = buildMap {
            put("file", json.encodeToJsonElement(file))
            if (outFormat != null) put("out_format", json.encodeToJsonElement(outFormat))
        }
        return callApiAndDecode("get_record", params, FileInfo.serializer())
    }

    override suspend fun getFile(file: String): FileInfo {
        val params = buildMap {
            put("file", json.encodeToJsonElement(file))
        }
        return callApiAndDecode("get_file", params, FileInfo.serializer())
    }

    // 事件订阅
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
}

class NapCatApiException(val code: Int, override val message: String) : Exception(message)
