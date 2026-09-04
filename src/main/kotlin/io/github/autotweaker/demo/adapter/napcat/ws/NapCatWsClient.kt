package io.github.autotweaker.demo.adapter.napcat.ws

import io.github.autotweaker.demo.adapter.napcat.api.NapCatApi
import io.github.autotweaker.demo.adapter.napcat.model.event.Event
import kotlinx.coroutines.flow.SharedFlow

/**
 * NapCat 正向 WebSocket 客户端。
 *
 * 事件在 [events] 上按到达顺序发布；API 动作通过 echo 匹配请求/响应。
 */
interface NapCatWsClient : NapCatApi {
	val isConnected: Boolean

	/**
	 * 已解析的事件流。无订阅者时事件会被丢弃（不可重放）。
	 */
	val events: SharedFlow<Event>

	/**
	 * 建立连接并持续重连，直到 [disconnect]。重复调用幂等。
	 */
	suspend fun connect(host: String, port: Int, token: String? = null)

	suspend fun disconnect()
}
