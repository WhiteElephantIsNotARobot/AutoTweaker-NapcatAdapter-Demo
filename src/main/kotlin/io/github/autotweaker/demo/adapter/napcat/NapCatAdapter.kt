package io.github.autotweaker.demo.adapter.napcat

import com.google.auto.service.AutoService
import io.github.autotweaker.api.adapter.Adapter
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.adapter.AdapterInfo
import io.github.autotweaker.demo.adapter.napcat.api.NapCatApi
import io.github.autotweaker.demo.adapter.napcat.bridge.MessageBridge
import io.github.autotweaker.demo.adapter.napcat.bridge.SessionManager
import io.github.autotweaker.demo.adapter.napcat.command.CommandRegistry
import io.github.autotweaker.demo.adapter.napcat.command.commands.*
import io.github.autotweaker.demo.adapter.napcat.config.NapCatSettings
import io.github.autotweaker.demo.adapter.napcat.permission.PermissionManager
import io.github.autotweaker.demo.adapter.napcat.ws.NapCatWsClient
import io.github.autotweaker.demo.adapter.napcat.ws.NapCatWsClientImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.slf4j.LoggerFactory

/**
 * NapCat 适配器
 *
 * 将 NapCat QQ 机器人与 AutoTweaker CoreAPI 桥接。
 * 通过 WebSocket 连接 NapCat，接收消息事件，
 * 经过命令系统和会话管理后转发给 LLM 处理。
 *
 * 启动前会检查密钥库是否已解锁，未解锁时等待 StateFlow 通知。
 */
@AutoService(Adapter::class)
class NapCatAdapter : Adapter {

    companion object {
        @Volatile private var _core: CoreAPI? = null
        @Volatile private var _napCatApi: NapCatApi? = null
        @Volatile var initializationState: String = "NOT_STARTED"
            private set

        /** CoreAPI 实例，适配器启动后可用 */
        val core: CoreAPI get() = _core ?: throw IllegalStateException("CoreAPI not initialized (state=$initializationState)")

        /** NapCat API 实例，WebSocket 连接后可用 */
        val napCatApi: NapCatApi get() = _napCatApi ?: throw IllegalStateException("NapCatAPI not initialized (state=$initializationState)")
    }

    private val logger = LoggerFactory.getLogger(NapCatAdapter::class.java)
    private var wsClient: NapCatWsClient? = null
    private var messageBridge: MessageBridge? = null
    private var adapterScope: CoroutineScope? = null

    override suspend fun load(coreVersion: SemVer): AdapterInfo {
        logger.info("Loading NapCat adapter")
        return AdapterInfo(
            name = "napcat",
            description = "NapCat adapter for AutoTweaker",
            version = SemVer(0, 1, 0, listOf("alpha")),
            source = Url("https://github.com/WhiteElephant-abc/AutoTweaker-NapcatAdapter-Demo")
        )
    }

    override suspend fun start(core: CoreAPI) {
        initializationState = "STARTING"
        _core = core
        logger.info("NapCat adapter starting...")

        // 创建适配器级协程作用域
        adapterScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        // 检查密钥库是否已解锁
        if (!core.secret.isUnlocked.value) {
            logger.info("Secret is locked, waiting for unlock...")
            adapterScope?.launch {
                waitForUnlockAndInitialize(core)
            }
            return
        }

        // 已解锁，直接初始化
        initializationState = "INITIALIZING"
        initializeComponents(core)
        initializationState = "READY"
    }

    private suspend fun waitForUnlockAndInitialize(core: CoreAPI) {
        try {
            core.secret.isUnlocked.first { it }
            logger.info("Secret unlocked, initializing components...")
            initializationState = "INITIALIZING"
            initializeComponents(core)
            initializationState = "READY"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to initialize after unlock", e)
        }
    }

    private suspend fun initializeComponents(core: CoreAPI) {
        val settings = core.config.settingService
        val host = settings.get(NapCatSettings.Host()).value
        val port = settings.get(NapCatSettings.Port()).value
        val token = settings.get(NapCatSettings.Token()).value.let { it.ifEmpty { null } }

        // 初始化权限管理
        val permissionManager = PermissionManager(core)

        // 初始化会话管理
        val sessionManager = SessionManager(core, permissionManager)

        // 初始化命令系统
        val commandRegistry = CommandRegistry().apply {
            register(HelpCommand(this))
            register(NewCommand())
            register(EnterCommand())
            register(ExitCommand())
            register(SessionCommand())
            register(ApproveCommand())
            register(RejectCommand())
            register(OperatorCommand())
            register(ModelCommand())
            register(WorkspaceCommand())
            register(UserCommand())
            register(ThinkingCommand())
            register(HistoryCommand())
        }

        // 初始化 WebSocket 客户端
        val client = NapCatWsClientImpl()

        // 初始化消息桥接，传入适配器级协程作用域
        val scope = adapterScope ?: throw IllegalStateException("adapterScope not initialized")
        val bridge = MessageBridge(core, client, sessionManager, commandRegistry, permissionManager, scope)

        // 注册事件处理器
        client.onGroupMessageEvent { event ->
            bridge.handleMessage(event)
        }
        client.onPrivateMessageEvent { event ->
            bridge.handleMessage(event)
        }
        client.onConnect {
            logger.info("Connected to NapCat at {}:{}", host, port)
        }
        client.onDisconnect { reason ->
            logger.warn("Disconnected from NapCat: {}", reason)
        }
        client.onError { error ->
            logger.error("NapCat WebSocket error", error)
        }

        // 连接
        try {
            client.connect(host, port, token)
            _napCatApi = client
            logger.debug("_napCatApi set, classloader={}", NapCatAdapter::class.java.classLoader)
            logger.info("NapCat adapter started, connecting to {}:{}", host, port)
        } catch (e: Exception) {
            _napCatApi = null
            logger.error("Failed to connect to NapCat", e)
        }

        this.wsClient = client
        this.messageBridge = bridge
    }

    override suspend fun stop() {
        initializationState = "STOPPED"
        logger.info("Stopping NapCat adapter...")
        // 取消所有协程（包括输出监听器）
        adapterScope?.cancel()
        try {
            wsClient?.disconnect()
        } catch (e: Exception) {
            logger.error("Error disconnecting from NapCat", e)
        }
        wsClient = null
        messageBridge = null
        adapterScope = null
        logger.info("NapCat adapter stopped")
    }
}
