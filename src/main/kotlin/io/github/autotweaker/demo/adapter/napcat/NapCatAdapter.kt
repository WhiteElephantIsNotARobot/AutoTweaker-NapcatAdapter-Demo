package io.github.autotweaker.demo.adapter.napcat

import com.google.auto.service.AutoService
import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.log
import io.github.autotweaker.api.scope
import io.github.autotweaker.api.adapter.Adapter
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.get
import io.github.autotweaker.api.types.KebabCase.Companion.toKebab
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.api.types.Url.Companion.toUrl
import io.github.autotweaker.api.types.adapter.AdapterInfo
import io.github.autotweaker.demo.adapter.napcat.model.event.Event
import io.github.autotweaker.demo.adapter.napcat.model.event.GroupMessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.MessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.PrivateMessageEvent
import io.github.autotweaker.demo.adapter.napcat.setting.Host
import io.github.autotweaker.demo.adapter.napcat.setting.Port
import io.github.autotweaker.demo.adapter.napcat.setting.Token
import io.github.autotweaker.demo.adapter.napcat.ws.NapCatWsClient
import io.github.autotweaker.demo.adapter.napcat.ws.NapCatWsClientImpl
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * NapCat QQ 适配器入口。
 *
 * 生命周期：init 创建 WS 客户端与核心基础设施 -> start 连接 NapCat 并订阅事件 -> stop 断开并停止。
 */
@AutoService(Adapter::class)
class NapCatAdapter : Adapter, Loggable {

	/**
	 * 插件共享 classloader，qq 工具经此获取 core 与 NapCat 客户端。
	 */
	companion object {
		@Volatile
		private var runtime: Runtime? = null

		fun runtime(): Runtime? = runtime

		class Runtime(
			val core: CoreAPI,
			val client: NapCatWsClient,
			val isRunning: () -> Boolean,
		)
	}

	private lateinit var core: CoreAPI
	private lateinit var client: NapCatWsClientImpl

	private val scope = scope()
	private var connectJob: Job? = null
	private var eventJob: Job? = null

	@Volatile
	private var running = false

	override val isRunning: Boolean
		get() = running

	override suspend fun init(core: CoreAPI): AdapterInfo {
		this.core = core
		this.client = NapCatWsClientImpl()
		runtime = Runtime(core, client) { running }
		return AdapterInfo(
			name = "napcat".toKebab(),
			description = "NapCat QQ 适配器：通过 OneBot 11 把 AutoTweaker 接入 QQ",
			version = SemVer(0, 1, 0),
			source = "https://github.com/WhiteElephantIsNotARobot/AutoTweaker-NapcatAdapter".toUrl(),
		)
	}

	override suspend fun start() {
		if (running) return
		running = true

		val host = Host().get()
		val port = Port().get()
		val token = Token().get().ifEmpty { null }

		connectJob = scope.launch {
			client.connect(host, port, token)
		}
		eventJob = scope.launch {
			client.events.collectLatest { event ->
				handleEvent(event)
			}
		}
		log.info("NapCat adapter started  host={}  port={}", host, port)
	}

	override suspend fun stop() {
		if (!running) return
		running = false
		connectJob?.cancel()
		eventJob?.cancel()
		connectJob = null
		eventJob = null
		client.disconnect()
		log.info("NapCat adapter stopped")
	}

	private fun handleEvent(event: Event) {
		when (event) {
			is MessageEvent -> log.debug(
				"Received message  type={}  from={}  id={}",
				if (event is GroupMessageEvent) "group" else "private",
				if (event is GroupMessageEvent) event.groupId else (event as PrivateMessageEvent).userId,
				event.messageId,
			)

			else -> log.debug("Received event  type={}", event::class.simpleName)
		}
	}
}
