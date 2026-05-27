package io.github.autotweaker.demo.adapter.napcat.ws

import io.github.autotweaker.demo.adapter.napcat.api.NapCatApi
import io.github.autotweaker.demo.adapter.napcat.model.event.*
import io.ktor.websocket.*
import java.io.Closeable

interface NapCatWsClient : NapCatApi, Closeable {
    val isConnected: Boolean

    suspend fun connect(host: String, port: Int, token: String? = null)
    suspend fun disconnect()

    // 事件订阅
    fun onEvent(handler: suspend (OneBotEvent) -> Unit)
    fun onMessageEvent(handler: suspend (MessageEvent) -> Unit)
    fun onGroupMessageEvent(handler: suspend (GroupMessageEvent) -> Unit)
    fun onPrivateMessageEvent(handler: suspend (PrivateMessageEvent) -> Unit)
    fun onNoticeEvent(handler: suspend (NoticeEvent) -> Unit)
    fun onRequestEvent(handler: suspend (RequestEvent) -> Unit)
    fun onMetaEvent(handler: suspend (MetaEvent) -> Unit)

    // 生命周期
    fun onConnect(handler: () -> Unit)
    fun onDisconnect(handler: (CloseReason?) -> Unit)
    fun onError(handler: (Throwable) -> Unit)
}
