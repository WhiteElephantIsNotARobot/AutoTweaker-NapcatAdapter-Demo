package io.github.autotweaker.demo.adapter.napcat.bridge

import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.types.session.SessionConfig
import io.github.autotweaker.api.types.session.SessionHandle
import io.github.autotweaker.demo.adapter.napcat.permission.PermissionManager
import io.github.autotweaker.demo.adapter.napcat.permission.Role
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    companion object {
        private const val SESSION_MAP_KEY = "session_map"
        private const val USER_MODELS_KEY = "user_models"
        private const val USER_WORKSPACES_KEY = "user_workspaces"
        private const val USER_THINKING_KEY = "user_thinking"
        private const val GLOBAL_CONFIG_KEY = "global_config"
    }

    private val store by lazy {
        core.config.jsonStore(SessionManager::class)
    }

    /** userId → sessionId */
    private val activeSessions = ConcurrentHashMap<Long, UUID>()

    /** userId → 主模型 ID（每用户隔离） */
    private val userPrimaryModels = ConcurrentHashMap<Long, UUID>()

    /** userId → 选择的工作区 ID（每用户隔离） */
    private val userSelectedWorkspaces = ConcurrentHashMap<Long, UUID>()

    /** userId → 是否启用 thinking（每用户隔离） */
    private val userThinking = ConcurrentHashMap<Long, Boolean>()

    /** 全局备选模型列表（操作员管理），线程安全 */
    private val globalFallbackModels = CopyOnWriteArrayList<UUID>()

    /** 全局压缩模型（操作员管理） */
    @Volatile
    private var globalSummarizeModel: UUID? = null

    init {
        loadFromStore()
    }

    // ==================== 会话管理 ====================

    fun getActiveSession(userId: Long): UUID? = activeSessions[userId]

    fun setActiveSession(userId: Long, sessionId: UUID) {
        activeSessions[userId] = sessionId
        saveToStore()
    }

    fun clearActiveSession(userId: Long) {
        activeSessions.remove(userId)
        saveToStore()
    }

    fun getActiveSessionHandle(userId: Long): SessionHandle? {
        val sessionId = activeSessions[userId] ?: return null
        return core.session.getHandle(sessionId)
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
        saveToStore()
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
        saveToStore()
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
        saveToStore()
        logger.info("User {} thinking set to {}", userId, enabled)
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
        if (modelId in globalFallbackModels) return false
        globalFallbackModels.add(modelId)
        saveToStore()
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
        saveToStore()
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
        saveToStore()
        logger.info("Summarize model set to {}", modelId)
    }

    // ==================== 会话创建 ====================

    suspend fun autoCreateSession(userId: Long, title: String = "新会话"): SessionHandle {
        val role = permissionManager.getRole(userId)
        val config = buildSessionConfig(userId)

        // 获取用户选择的工作区
        val selectedWorkspaceId = userSelectedWorkspaces[userId]

        val handle = if (permissionManager.hasNonContainerPermission(userId)) {
            // 有非容器权限：优先使用用户选择的工作区，否则用默认工作区
            if (selectedWorkspaceId != null) {
                val workspace = core.session.listWorkspaces()
                    .find { it.meta.id == selectedWorkspaceId }
                if (workspace != null) {
                    core.session.create(workspace.meta.id, config)
                } else {
                    core.session.create(config)
                }
            } else {
                core.session.create(config)
            }
        } else {
            // 无非容器权限：只能用容器内工作区
            val containerWorkspaces = core.session.listWorkspaces()
                .filter { it.meta.inContainer }
            if (containerWorkspaces.isEmpty()) {
                throw IllegalStateException("没有可用的容器工作区，请联系操作员创建")
            }

            // 优先使用用户选择的工作区
            val workspace = if (selectedWorkspaceId != null) {
                containerWorkspaces.find { it.meta.id == selectedWorkspaceId }
                    ?: containerWorkspaces.first()
            } else {
                containerWorkspaces.first()
            }
            core.session.create(workspace.meta.id, config)
        }

        core.session.updateTitle(handle.id, title)
        setActiveSession(userId, handle.id)
        logger.info("Auto-created session {} for user {}", handle.id, userId)
        return handle
    }

    fun enterSession(userId: Long, sessionId: UUID): SessionHandle? {
        val handle = core.session.getHandle(sessionId) ?: return null
        setActiveSession(userId, sessionId)
        return handle
    }

    fun exitSession(userId: Long): Boolean {
        if (activeSessions[userId] == null) return false
        clearActiveSession(userId)
        return true
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

    // ==================== 持久化 ====================

    private fun loadFromStore() {
        try {
            val element = store.get() ?: return
            val obj = element.jsonObject

            obj[SESSION_MAP_KEY]?.let { loadSessionMap(it) }
            obj[USER_MODELS_KEY]?.let { loadUserModels(it) }
            obj[USER_WORKSPACES_KEY]?.let { loadUserWorkspaces(it) }
            obj[USER_THINKING_KEY]?.let { loadUserThinking(it) }
            obj[GLOBAL_CONFIG_KEY]?.let { loadGlobalConfig(it) }

            logger.debug("Loaded {} sessions, {} user models, {} user workspaces, {} user thinking",
                activeSessions.size, userPrimaryModels.size, userSelectedWorkspaces.size, userThinking.size)
        } catch (e: Exception) {
            logger.warn("Failed to load from store", e)
        }
    }

    private fun loadSessionMap(element: kotlinx.serialization.json.JsonElement) {
        try {
            element.jsonObject.forEach { (key, value) ->
                try {
                    activeSessions[key.toLong()] = UUID.fromString(value.jsonPrimitive.content)
                } catch (e: Exception) {
                    logger.warn("Failed to parse session: {} -> {}", key, value)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to load session map", e)
        }
    }

    private fun loadUserModels(element: kotlinx.serialization.json.JsonElement) {
        try {
            element.jsonObject.forEach { (key, value) ->
                try {
                    userPrimaryModels[key.toLong()] = UUID.fromString(value.jsonPrimitive.content)
                } catch (e: Exception) {
                    logger.warn("Failed to parse user model: {} -> {}", key, value)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to load user models", e)
        }
    }

    private fun loadUserWorkspaces(element: kotlinx.serialization.json.JsonElement) {
        try {
            element.jsonObject.forEach { (key, value) ->
                try {
                    userSelectedWorkspaces[key.toLong()] = UUID.fromString(value.jsonPrimitive.content)
                } catch (e: Exception) {
                    logger.warn("Failed to parse user workspace: {} -> {}", key, value)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to load user workspaces", e)
        }
    }

    private fun loadUserThinking(element: kotlinx.serialization.json.JsonElement) {
        try {
            element.jsonObject.forEach { (key, value) ->
                try {
                    userThinking[key.toLong()] = value.jsonPrimitive.content.toBoolean()
                } catch (e: Exception) {
                    logger.warn("Failed to parse user thinking: {} -> {}", key, value)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to load user thinking", e)
        }
    }

    private fun loadGlobalConfig(element: kotlinx.serialization.json.JsonElement) {
        try {
            val obj = element.jsonObject
            obj["fallback"]?.jsonArray?.forEach {
                try {
                    globalFallbackModels.add(UUID.fromString(it.jsonPrimitive.content))
                } catch (e: Exception) {
                    logger.warn("Failed to parse fallback model: {}", it)
                }
            }
            obj["summarize"]?.let {
                globalSummarizeModel = UUID.fromString(it.jsonPrimitive.content)
            }
        } catch (e: Exception) {
            logger.warn("Failed to load global config", e)
        }
    }

    private fun saveToStore() {
        try {
            val obj = buildJsonObject {
                put(SESSION_MAP_KEY, buildJsonObject {
                    activeSessions.forEach { (userId, sessionId) ->
                        put(userId.toString(), JsonPrimitive(sessionId.toString()))
                    }
                })
                put(USER_MODELS_KEY, buildJsonObject {
                    userPrimaryModels.forEach { (userId, modelId) ->
                        put(userId.toString(), JsonPrimitive(modelId.toString()))
                    }
                })
                put(USER_WORKSPACES_KEY, buildJsonObject {
                    userSelectedWorkspaces.forEach { (userId, workspaceId) ->
                        put(userId.toString(), JsonPrimitive(workspaceId.toString()))
                    }
                })
                put(USER_THINKING_KEY, buildJsonObject {
                    userThinking.forEach { (userId, enabled) ->
                        put(userId.toString(), JsonPrimitive(enabled))
                    }
                })
                put(GLOBAL_CONFIG_KEY, buildJsonObject {
                    put("fallback", buildJsonArray {
                        globalFallbackModels.forEach { add(JsonPrimitive(it.toString())) }
                    })
                    globalSummarizeModel?.let {
                        put("summarize", JsonPrimitive(it.toString()))
                    }
                })
            }
            store.set(obj)
        } catch (e: Exception) {
            logger.error("Failed to save to store", e)
        }
    }
}
