package io.github.autotweaker.demo.adapter.napcat

import com.google.auto.service.AutoService
import io.github.autotweaker.api.adapter.Adapter
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.trace.TraceRecorder
import io.github.autotweaker.api.trace.catching
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

    override val isRunning: Boolean get() = initializationState == InitializationState.READY

    companion object {
        @Volatile private var _core: CoreAPI? = null
        @Volatile private var _napCatApi: NapCatApi? = null
        @Volatile var initializationState: InitializationState = InitializationState.NOT_STARTED
            private set

        /** CoreAPI 实例，适配器启动后可用 */
        val core: CoreAPI get() = _core ?: throw IllegalStateException("CoreAPI not initialized (state=$initializationState)")

        /** NapCat API 实例，WebSocket 连接后可用 */
        val napCatApi: NapCatApi get() = _napCatApi ?: throw IllegalStateException("NapCatAPI not initialized (state=$initializationState)")
    }

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val trace: TraceRecorder by lazy { core.trace(this::class) }
    private var wsClient: NapCatWsClient? = null
    private var messageBridge: MessageBridge? = null
    private var adapterScope: CoroutineScope? = null

    override suspend fun load(coreVersion: SemVer): AdapterInfo {
        logger.info("NapCat adapter loaded")
        return AdapterInfo(
            name = "napcat",
            description = "NapCat adapter for AutoTweaker",
            version = SemVer(0, 1, 0, listOf("alpha")),
            source = Url("https://github.com/WhiteElephant-abc/AutoTweaker-NapcatAdapter-Demo")
        )
    }

    override suspend fun start(core: CoreAPI) {
        initializationState = InitializationState.STARTING
        _core = core

        // 创建适配器级协程作用域
        adapterScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        // 检查密钥库是否已解锁
        if (!core.secret.isUnlocked.value) {
            logger.info("Secret locked")
            adapterScope?.launch {
                waitForUnlockAndInitialize(core)
            }
            return
        }

        // 已解锁，直接初始化
        initializationState = InitializationState.INITIALIZING
        initializeComponents(core)
        initializationState = InitializationState.READY
    }

    private suspend fun waitForUnlockAndInitialize(core: CoreAPI) {
        try {
            core.secret.isUnlocked.first { it }
            logger.info("Secret unlocked")
            initializationState = InitializationState.INITIALIZING
            initializeComponents(core)
            initializationState = InitializationState.READY
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            trace.exception(e)
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
            logger.info("Connected to NapCat  host={}  port={}", host, port)
        }
        client.onDisconnect { reason ->
            logger.warn("Disconnected from NapCat  reason={}", reason)
        }
        client.onError { error ->
            logger.error("NapCat WebSocket error", error)
        }

        // 连接
        trace.catching {
            client.connect(host, port, token)
            _napCatApi = client
            logger.debug("_napCatApi set, classloader={}", NapCatAdapter::class.java.classLoader)
            logger.info("NapCat adapter connected  host={}  port={}", host, port)
        }.onFailure { e ->
            _napCatApi = null
            logger.error("Failed to connect to NapCat", e)
        }

        this.wsClient = client
        this.messageBridge = bridge
    }

    override suspend fun stop() {
        initializationState = InitializationState.STOPPED
        // 取消所有协程（包括输出监听器）
        adapterScope?.cancel()
        trace.catching {
            wsClient?.disconnect()
        }.onFailure { e ->
            logger.error("Failed to disconnect from NapCat", e)
        }
        wsClient = null
        messageBridge = null
        adapterScope = null
        logger.info("NapCat adapter stopped")
    }
}

enum class InitializationState {
    NOT_STARTED,
    STARTING,
    INITIALIZING,
    READY,
    STOPPED
}

/*
 * 日志规范
 *
 * 1. 模板: 动作-对象-结果，用过去时或现在完成时
 *    正确: "Sent user message  agentId=xxx  sessionId=xxx"
 *    正确: "Has loaded 3 external adapters  adapterDir=xxx"
 *    错误: "Sending user message..."  "Send user message"
 *
 * 2. 首字母大写，不加句号
 *    正确: "Settings initialized"
 *    错误: "settings initialized."  "Settings initialized."
 *
 * 3. 变量表示: key=value，空格分隔多个字段，无逗号
 *    正确: "agentId=abc  model=deepseek-chat  statusCode=200"
 *    错误: "agentId:abc, model:deepseek-chat"
 *
 * 4. 关键标识: 每条日志必须包含所属组件的标识
 *    - agentId: Agent 核心流转、phase、llm 调用
 *    - sessionId: Session 创建/恢复/销毁
 *    - tool: 工具调用
 *    - provider / model: LLM 提供者
 *    - 标识在最前或紧跟动作之后
 *
 * 5. 级别:
 *    - INFO:   正常生命周期事件（启动、初始化、创建、完成）
 *    - DEBUG:  内部细节（参数、中间状态、进入/退出）
 *    - WARN:   可恢复异常、重试、降级（不传异常对象）
 *    - ERROR:  不可恢复异常（必须传异常对象，让框架记堆栈）
 *
 * 6. 异常: catch 时描述业务上下文，最后一个参数传异常
 *    正确: logger.error("Failed to execute tool  agentId={}  tool={}  callId={}", agentId, tool, callId, e)
 *    错误: logger.error("Error: ${e.message}")  // 丢失堆栈
 *
 * 7. 变量注入: 用 SLF4J 占位符（{}）注入变量，禁止 Kotlin 字符串模板
 *    正确: logger.info("Agent created  agentId={}  model={}", agentId, model)
 *    错误: logger.info("Agent created  agentId=${agentId}  model=${model}")
 *    异常对象放在最后: logger.error("Failed config  key={}", key, e)
 *
 * 8. 肯定/否定一致:
 *    - 成功: "Settings initialized"  "Container started"
 *    - 失败: "Failed to initialize settings"  "Failed to start container"
 */
