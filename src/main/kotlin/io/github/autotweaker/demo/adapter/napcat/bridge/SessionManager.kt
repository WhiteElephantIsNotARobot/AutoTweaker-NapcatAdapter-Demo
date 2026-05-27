package io.github.autotweaker.demo.adapter.napcat.bridge

import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.types.session.SessionConfig
import io.github.autotweaker.api.types.session.SessionHandle
import io.github.autotweaker.demo.adapter.napcat.permission.PermissionManager
import io.github.autotweaker.demo.adapter.napcat.permission.Role
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 会话映射管理器
 *
 * 维护 userId → activeSessionId 的映射，支持持久化。
 * 负责会话的创建、切换、退出和自动创建。
 *
 * @property core CoreAPI 实例
 * @property permissionManager 权限管理器，用于判断用户是否有权使用默认工作区
 */
class SessionManager(
    private val core: CoreAPI,
    private val permissionManager: PermissionManager
) {

    private val logger = LoggerFactory.getLogger(SessionManager::class.java)

    companion object {
        private const val SESSION_MAP_KEY = "session_map"
    }

    private val sessionStore by lazy {
        core.config.jsonStore(SessionManager::class)
    }

    /** 内存缓存：userId → sessionId */
    private val activeSessions = ConcurrentHashMap<Long, UUID>()

    init {
        loadFromStore()
    }

    /**
     * 获取用户的活跃会话 ID
     *
     * @param userId QQ 号
     * @return 活跃会话 ID，无则返回 null
     */
    fun getActiveSession(userId: Long): UUID? = activeSessions[userId]

    /**
     * 设置用户的活跃会话
     *
     * @param userId QQ 号
     * @param sessionId 会话 ID
     */
    fun setActiveSession(userId: Long, sessionId: UUID) {
        activeSessions[userId] = sessionId
        saveToStore()
        logger.info("User {} active session set to {}", userId, sessionId)
    }

    /**
     * 清除用户的活跃会话（不删除会话本身）
     *
     * @param userId QQ 号
     */
    fun clearActiveSession(userId: Long) {
        activeSessions.remove(userId)
        saveToStore()
        logger.info("User {} active session cleared", userId)
    }

    /**
     * 获取用户的活跃会话 Handle
     *
     * @param userId QQ 号
     * @return 会话句柄，无活跃会话返回 null
     */
    fun getActiveSessionHandle(userId: Long): SessionHandle? {
        val sessionId = activeSessions[userId] ?: return null
        return core.session.getHandle(sessionId)
    }

    /**
     * 自动创建会话并设为活跃
     *
     * - 操作员及以上：使用默认工作区（不需要指定 workspaceId）
     * - 普通用户：需要有可用工作区，否则抛异常
     *
     * @param userId QQ 号
     * @param title 会话标题
     * @return 创建的会话句柄
     * @throws IllegalStateException 普通用户没有可用工作区
     * @throws IllegalStateException 没有可用的模型
     */
    suspend fun autoCreateSession(userId: Long, title: String = "新会话"): SessionHandle {
        val role = permissionManager.getRole(userId)
        val config = buildSessionConfig()

        val handle = if (role.ordinal <= Role.OPERATOR.ordinal) {
            core.session.create(config)
        } else {
            val workspaces = core.session.listWorkspaces()
            if (workspaces.isEmpty()) {
                throw IllegalStateException("没有可用的工作区，请联系操作员创建工作区")
            }
            core.session.create(workspaces.first().id, config)
        }

        core.session.updateTitle(handle.id, title)
        setActiveSession(userId, handle.id)
        logger.info("Auto-created session {} for user {}", handle.id, userId)
        return handle
    }

    /**
     * 进入指定会话
     *
     * @param userId QQ 号
     * @param sessionId 目标会话 ID
     * @return 会话句柄，不存在返回 null
     */
    fun enterSession(userId: Long, sessionId: UUID): SessionHandle? {
        val handle = core.session.getHandle(sessionId) ?: return null
        setActiveSession(userId, sessionId)
        return handle
    }

    /**
     * 退出当前会话
     *
     * @param userId QQ 号
     * @return true 如果有活跃会话并已退出
     */
    fun exitSession(userId: Long): Boolean {
        if (activeSessions[userId] == null) return false
        clearActiveSession(userId)
        return true
    }

    /**
     * 构建 SessionConfig，使用系统中可用的真实模型 ID
     *
     * @throws IllegalStateException 没有可用的模型
     */
    private fun buildSessionConfig(): SessionConfig {
        val modelIds = core.config.listModelIds()
        if (modelIds.isEmpty()) {
            throw IllegalStateException("没有可用的模型，请先配置模型")
        }
        val primaryModel = modelIds.first()
        val fallbackModels = if (modelIds.size > 1) modelIds.drop(1) else null
        val summarizeModel = if (modelIds.size > 1) modelIds[1] else primaryModel

        return SessionConfig(
            model = primaryModel,
            fallbackModel = fallbackModels,
            summarizeModel = summarizeModel,
            thinking = false
        )
    }

    private fun loadFromStore() {
        try {
            val element = sessionStore.get()
            if (element != null) {
                element.jsonObject.forEach { (key, value) ->
                    try {
                        val userId = key.toLong()
                        val sessionId = UUID.fromString(value.jsonPrimitive.content)
                        activeSessions[userId] = sessionId
                    } catch (e: Exception) {
                        logger.warn("Failed to parse session mapping: {} -> {}", key, value)
                    }
                }
                logger.debug("Loaded {} session mappings", activeSessions.size)
            }
        } catch (e: Exception) {
            logger.warn("Failed to load session mappings", e)
        }
    }

    private fun saveToStore() {
        try {
            val obj = buildJsonObject {
                activeSessions.forEach { (userId, sessionId) ->
                    put(userId.toString(), JsonPrimitive(sessionId.toString()))
                }
            }
            sessionStore.set(obj)
        } catch (e: Exception) {
            logger.error("Failed to save session mappings", e)
        }
    }
}
