package io.github.autotweaker.demo.adapter.napcat.bridge

import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.types.session.SessionConfig
import io.github.autotweaker.api.types.session.SessionHandle
import io.github.autotweaker.demo.adapter.napcat.command.commands.WorkspaceCommand
import io.github.autotweaker.demo.adapter.napcat.permission.PermissionManager
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 会话映射管理器
 *
 * 管理会话映射和模型配置：
 * - 会话映射：userId → activeSessionId（每用户）
 * - 主模型：每用户隔离，用户可自行设置
 * - 工作区：每用户隔离，用户可自行选择
 * - Thinking：每用户隔离，用户可自行开关
 * - 消息历史注入：每用户隔离，用户可自行开关
 * - 备选/压缩模型：全局共享，操作员管理
 *
 * @property core CoreAPI 实例
 * @property permissionManager 权限管理器
 */
class SessionManager(
    private val core: CoreAPI,
    private val permissionManager: PermissionManager
) {

    private val logger = LoggerFactory.getLogger(SessionManager::class.java)
    private val trace = core.trace(this::class)

    /** userId → sessionId */
    private val activeSessions = ConcurrentHashMap<Long, UUID>()

    /** userId → 主模型 ID（每用户隔离） */
    private val userPrimaryModels = ConcurrentHashMap<Long, UUID>()

    /** userId → 选择的工作区 ID（每用户隔离） */
    private val userSelectedWorkspaces = ConcurrentHashMap<Long, UUID>()

    /** userId → 是否启用 thinking（每用户隔离） */
    private val userThinking = ConcurrentHashMap<Long, Boolean>()

    /** userId → 是否注入消息历史（每用户隔离，默认 true） */
    private val userHistoryInjection = ConcurrentHashMap<Long, Boolean>()

    /** 全局备选模型列表（操作员管理），线程安全 */
    private val globalFallbackModels = CopyOnWriteArrayList<UUID>()

    /** 全局压缩模型（操作员管理） */
    @Volatile
    private var globalSummarizeModel: UUID? = null

    private var persistenceDirty = false
    private var persistenceJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val persistence = SessionPersistence(
        store = core.config.jsonStore(SessionManager::class),
        activeSessions = activeSessions,
        userPrimaryModels = userPrimaryModels,
        userSelectedWorkspaces = userSelectedWorkspaces,
        userThinking = userThinking,
        userHistoryInjection = userHistoryInjection,
        globalFallbackModels = globalFallbackModels,
        globalSummarizeModel = { globalSummarizeModel }
    )

    init {
        persistence.load()
        globalSummarizeModel = persistence.loadSummarizeModel()
    }

    // ==================== 会话管理 ====================

    fun getActiveSession(userId: Long): UUID? = activeSessions[userId]

    fun setActiveSession(userId: Long, sessionId: UUID) {
        activeSessions[userId] = sessionId
        scheduleSave()
    }

    fun clearActiveSession(userId: Long) {
        activeSessions.remove(userId)
        scheduleSave()
    }

    suspend fun getActiveSessionHandle(userId: Long): SessionHandle? {
        val sessionId = activeSessions[userId] ?: return null
        return try {
            core.session.getHandle(sessionId)
        } catch (e: Exception) {
            logger.warn("Failed to get handle for session {}", sessionId, e)
            trace.add("e", e.toString())
            null
        }
    }

    /**
     * 获取所有活跃会话
     *
     * @return Map<userId, sessionId> 活跃会话映射
     */
    fun getAllActiveSessions(): Map<Long, UUID> = HashMap(activeSessions)

    // ==================== 用户主模型（每用户隔离） ====================

    /**
     * 获取用户的主模型 ID
     *
     * @param userId QQ 号
     * @return 主模型 ID，未设置返回 null
     */
    fun getUserPrimaryModel(userId: Long): UUID? = userPrimaryModels[userId]

    /**
     * 设置用户的主模型
     *
     * @param userId QQ 号
     * @param modelId 模型 ID
     */
    fun setUserPrimaryModel(userId: Long, modelId: UUID) {
        userPrimaryModels[userId] = modelId
        scheduleSave()
        logger.info("User {} primary model set to {}", userId, modelId)
    }

    // ==================== 用户工作区选择（每用户隔离） ====================

    /**
     * 获取用户选择的工作区 ID
     *
     * @param userId QQ 号
     * @return 工作区 ID，未选择返回 null
     */
    fun getUserWorkspace(userId: Long): UUID? = userSelectedWorkspaces[userId]

    /**
     * 设置用户选择的工作区
     *
     * @param userId QQ 号
     * @param workspaceId 工作区 ID
     */
    fun setUserWorkspace(userId: Long, workspaceId: UUID) {
        userSelectedWorkspaces[userId] = workspaceId
        scheduleSave()
        logger.info("User {} selected workspace {}", userId, workspaceId)
    }

    // ==================== 用户 Thinking 设置（每用户隔离） ====================

    /**
     * 获取用户的 thinking 设置
     *
     * @param userId QQ 号
     * @return 是否启用 thinking，未设置返回 false
     */
    fun getUserThinking(userId: Long): Boolean = userThinking[userId] ?: false

    /**
     * 设置用户的 thinking 开关
     *
     * @param userId QQ 号
     * @param enabled 是否启用
     */
    fun setUserThinking(userId: Long, enabled: Boolean) {
        userThinking[userId] = enabled
        scheduleSave()
        logger.info("User {} thinking set to {}", userId, enabled)
    }

    // ==================== 用户消息历史注入设置（每用户隔离） ====================

    /**
     * 获取用户的消息历史注入设置
     *
     * @param userId QQ 号
     * @return 是否注入消息历史，未设置返回 true（默认开启）
     */
    fun getUserHistoryInjection(userId: Long): Boolean = userHistoryInjection[userId] ?: true

    /**
     * 设置用户的消息历史注入开关
     *
     * @param userId QQ 号
     * @param enabled 是否启用
     */
    fun setUserHistoryInjection(userId: Long, enabled: Boolean) {
        userHistoryInjection[userId] = enabled
        scheduleSave()
        logger.info("User {} history injection set to {}", userId, enabled)
    }

    // ==================== 全局模型配置（操作员管理） ====================

    /**
     * 获取全局备选模型列表
     */
    fun getGlobalFallbackModels(): List<UUID> = globalFallbackModels.toList()

    /**
     * 获取全局压缩模型
     */
    fun getGlobalSummarizeModel(): UUID? = globalSummarizeModel

    /**
     * 添加备选模型
     *
     * @param modelId 模型 ID
     * @return true 如果添加成功，false 如果已存在
     */
    fun addFallbackModel(modelId: UUID): Boolean {
        synchronized(globalFallbackModels) {
            if (modelId in globalFallbackModels) return false
            globalFallbackModels.add(modelId)
        }
        scheduleSave()
        logger.info("Added fallback model: {}", modelId)
        return true
    }

    /**
     * 移除备选模型
     *
     * @param modelId 模型 ID
     * @return true 如果移除成功
     */
    fun removeFallbackModel(modelId: UUID): Boolean {
        if (!globalFallbackModels.remove(modelId)) return false
        scheduleSave()
        logger.info("Removed fallback model: {}", modelId)
        return true
    }

    /**
     * 设置压缩模型
     *
     * @param modelId 模型 ID
     */
    fun setSummarizeModel(modelId: UUID) {
        globalSummarizeModel = modelId
        scheduleSave()
        logger.info("Summarize model set to {}", modelId)
    }

    // ==================== 会话创建 ====================

    suspend fun autoCreateSession(userId: Long, title: String = "新会话"): SessionHandle {
        val config = buildSessionConfig(userId)

        // 获取用户选择的工作区
        val selectedWorkspaceId = userSelectedWorkspaces[userId]

        val sessionId = if (permissionManager.hasNonContainerPermission(userId)) {
            // 有非容器权限：优先使用用户选择的工作区，否则用默认工作区
            if (selectedWorkspaceId != null) {
                val workspace = core.session.listWorkspaces()
                    .find { it.meta.id == selectedWorkspaceId }
                if (workspace != null) {
                    try {
                        core.session.create(workspace.meta.id, config)
                    } catch (e: Exception) {
                        logger.warn("Failed to create session with workspace {}, falling back to default", selectedWorkspaceId, e)
                        trace.add("e", e.toString())
                        core.session.create(config)
                    }
                } else {
                    core.session.create(config)
                }
            } else {
                core.session.create(config)
            }
        } else {
            // 无非容器权限：只能用容器内工作区
            val containerWorkspaces = core.session.listWorkspaces()
                .filter { WorkspaceCommand.isContainerWorkspace(it.meta.path) }
            if (containerWorkspaces.isEmpty()) {
                throw IllegalStateException("没有可用的容器工作区，请联系操作员创建")
            }
            val workspace = if (selectedWorkspaceId != null) {
                containerWorkspaces.find { it.meta.id == selectedWorkspaceId }
                    ?: containerWorkspaces.first()
            } else {
                containerWorkspaces.first()
            }
            try {
                core.session.create(workspace.meta.id, config)
            } catch (e: IllegalStateException) {
                trace.add("e", e.toString())
                if (e.message?.contains("directory does not exist") == true) {
                    throw IllegalStateException("容器工作区目录不存在: ${workspace.meta.path}，请联系操作员检查")
                }
                throw e
            }
        }

        trace.add("session_create", config.toString())
        val handle = core.session.getHandle(sessionId)
        core.session.updateTitle(sessionId, title)
        setActiveSession(userId, sessionId)
        logger.info("Auto-created session {} for user {}", sessionId, userId)
        return handle
    }

    suspend fun enterSession(userId: Long, sessionId: UUID): SessionHandle {
        val handle = core.session.getHandle(sessionId)
        setActiveSession(userId, sessionId)
        return handle
    }

    fun exitSession(userId: Long): Boolean {
        if (activeSessions[userId] == null) return false
        clearActiveSession(userId)
        return true
    }

    // ==================== 防抖持久化 ====================

    private fun scheduleSave() {
        persistenceDirty = true
        if (persistenceJob?.isActive != true) {
            persistenceJob = scope.launch {
                delay(500)
                if (persistenceDirty) {
                    persistenceDirty = false
                    persistence.save()
                }
            }
        }
    }

    // ==================== 内部实现 ====================

    /**
     * 构建 SessionConfig
     *
     * - 主模型：用户配置 > 系统第一个
     * - 备选模型：全局配置
     * - 压缩模型：全局配置 > 主模型
     */
    private fun buildSessionConfig(userId: Long): SessionConfig {
        val modelIds = core.config.listModelIds()
        if (modelIds.isEmpty()) {
            throw IllegalStateException("没有可用的模型，请先配置模型")
        }

        val primaryModel = userPrimaryModels[userId]?.takeIf { it in modelIds }
            ?: modelIds.first()

        val fallbackModels = globalFallbackModels.filter { it in modelIds && it != primaryModel }
            .ifEmpty { null }

        val summarizeModel = globalSummarizeModel?.takeIf { it in modelIds }
            ?: fallbackModels?.firstOrNull()
            ?: primaryModel

        return SessionConfig(
            model = primaryModel,
            fallbackModel = fallbackModels,
            summarizeModel = summarizeModel,
            thinking = getUserThinking(userId)
        )
    }
}
