package io.github.autotweaker.demo.adapter.napcat.ws

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.api.types.KebabCase.Companion.toKebab
import io.github.autotweaker.demo.adapter.napcat.api.NapCatApiBase
import io.github.autotweaker.demo.adapter.napcat.api.NapCatApiException
import io.github.autotweaker.demo.adapter.napcat.model.event.Event
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Ktor(CIO) 实现的 [NapCatWsClient]。
 *
 * - 自动重连（指数退避）
 * - echo 匹配 API 请求/响应
 * - 事件在单协程内顺序解析后发布到 [events]
 * - 出入帧全量写入 trace（`request` / `received`），异常统一经 trace.catching 记录
 */
class NapCatWsClientImpl(
) : NapCatWsClient, NapCatApiBase(NAPCAT_JSON), Loggable, Traceable {

	@Volatile
	private var wsSession: WebSocketSession? = null

	@Volatile
	private var client: HttpClient? = null

	@Volatile
	private var connectJob: Job? = null

	private val connectedFlag = AtomicBoolean(false)
	private val echoCounter = AtomicLong(0)
	private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()

	private val eventFlow = MutableSharedFlow<Event>(
		replay = 0,
		extraBufferCapacity = 512,
	)
	override val events: SharedFlow<Event> = eventFlow.asSharedFlow()

	override val isConnected: Boolean
		get() = connectedFlag.get()

	// ==================== 连接管理 ====================

	override suspend fun connect(host: String, port: Int, token: String?) {
		if (!connectedFlag.compareAndSet(false, true)) return
		val hostPart = if (host.contains(":")) "[$host]" else host
		val url = buildString {
			append("ws://$hostPart:$port/")
			if (token != null) append("?access_token=$token")
		}

		client?.close()
		client = HttpClient(CIO) {
			install(WebSockets) {
				pingIntervalMillis = 20_000
			}
		}

		connectJob = scope().launch {
			var retryDelayMs = INITIAL_RETRY_DELAY_MS
			var wasEverConnected = false
			while (isActive) {
				trace.catching {
					establishConnection(
						url,
						onConnected = {
							wasEverConnected = true
							retryDelayMs = INITIAL_RETRY_DELAY_MS
						},
					)
				}.onFailure { e ->
					if (isActive) {
						if (wasEverConnected) log.warn("Connection lost  reason={}", e.message)
						else log.warn("Failed to connect  reason={}", e.message)
					}
				}

				if (!isActive) break
				delay(retryDelayMs)
				retryDelayMs = (retryDelayMs * RETRY_BACKOFF).toLong().coerceAtMost(MAX_RETRY_DELAY_MS)
			}
		}
	}

	/** 建立一次连接并持续消费帧直到断开。连接成功后回调 [onConnected]。 */
	private suspend fun establishConnection(url: String, onConnected: () -> Unit) {
		client!!.webSocket(url) {
			wsSession = this
			onConnected()
			log.info("Connected to NapCat  url={}", url)

			try {
				for (frame in incoming) {
					if (frame is Frame.Text) {
						handleFrame(frame.readText())
					}
				}
			} finally {
				wsSession = null
				pendingRequests.forEach { (_, deferred) -> deferred.cancel() }
				pendingRequests.clear()
			}
		}
	}

	override suspend fun disconnect() {
		connectedFlag.set(false)
		connectJob?.cancelAndJoin()
		connectJob = null
		val session = wsSession
		wsSession = null
		session?.close(CloseReason(CloseReason.Codes.NORMAL, "Adapter stopped"))
		client?.close()
		client = null
		pendingRequests.forEach { (_, deferred) -> deferred.cancel() }
		pendingRequests.clear()
	}

	// ==================== 帧处理 ====================

	private suspend fun handleFrame(text: String) {
		trace.add(RECEIVED_NS, text)
		trace.catching {
			val obj = json.parseToJsonElement(text).jsonObject
			val echo = obj["echo"]?.jsonPrimitive?.contentOrNull
			if (echo != null) {
				pendingRequests.remove(echo)?.complete(obj)
			} else {
				EventParser.parse(json, obj)?.let { eventFlow.emit(it) }
			}
		}.onFailure { e ->
			log.error("Failed to handle frame  length={}", text.length, e)
		}
	}

	// ==================== API 调用 ====================

	override suspend fun sendAction(action: String, params: JsonObject): JsonObject {
		val echo = echoCounter.incrementAndGet().toString()
		val session = wsSession ?: throw NapCatApiException(-1, null, "Not connected to NapCat")
		val request = buildJsonObject {
			put("action", action)
			put("params", params)
			put("echo", echo)
		}
		trace.add(REQUEST_NS, request.toString())

		val deferred = CompletableDeferred<JsonObject>()
		pendingRequests[echo] = deferred
		try {
			return trace.catching {
				session.send(Frame.Text(request.toString()))
				val response = withTimeoutOrNull(API_TIMEOUT_MS) { deferred.await() }
					?: throw NapCatApiException(-1, null, "NapCat request timed out  action=$action")
				checkResponse(action, response)
				response
			}.getOrThrow()
		} finally {
			pendingRequests.remove(echo)
		}
	}

	private fun checkResponse(action: String, response: JsonObject) {
		val status = response["status"]?.jsonPrimitive?.contentOrNull
		val retcode = response["retcode"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
		if (status != null && status != "ok") {
			throw NapCatApiException(retcode ?: -1, status, messageOf(response))
		}
		if (retcode != null && retcode > 1) {
			throw NapCatApiException(retcode, status, messageOf(response))
		}
		log.debug("NapCat action succeeded  action={}  retcode={}", action, retcode)
	}

	private fun messageOf(response: JsonObject): String {
		val msg = response["message"] ?: return "Unknown error"
		return if (msg is JsonPrimitive) msg.content else msg.toString()
	}

	companion object {
		const val API_TIMEOUT_MS = 30_000L
		const val INITIAL_RETRY_DELAY_MS = 1_000L
		const val MAX_RETRY_DELAY_MS = 60_000L
		const val RETRY_BACKOFF = 2.0

		val NAPCAT_JSON: Json = Json {
			ignoreUnknownKeys = true
			isLenient = true
		}

		private val REQUEST_NS = "request".toKebab()
		private val RECEIVED_NS = "received".toKebab()
	}
}
