package io.github.autotweaker.demo.adapter.napcat.permission

import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.demo.adapter.napcat.config.NapCatSettings
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 权限管理器
 *
 * 四级角色体系：
 * - 管理员：通过 [NapCatSettings.AdminQQ] 配置，仅一个
 * - 操作员：由管理员命令指定，持久化
 * - 用户：由操作员命令指定，持久化（白名单）
 * - 未授权：默认状态，无法使用 bot
 *
 * @property core CoreAPI 实例
 */
class PermissionManager(private val core: CoreAPI) {

    private val logger = LoggerFactory.getLogger(PermissionManager::class.java)

    private val store by lazy {
        core.config.jsonStore(PermissionManager::class)
    }

    @Volatile
    private var cachedAdminQQ: Long = -1L

    private val cachedOperators = CopyOnWriteArrayList<Long>()
    private val cachedUsers = CopyOnWriteArrayList<Long>()

    /** 用户非容器权限：有此权限的用户可使用容器外工作区 */
    private val userNonContainerPermissions = ConcurrentHashMap.newKeySet<Long>()

    @Volatile
    private var operatorsLoaded = false
    @Volatile
    private var usersLoaded = false

    /**
     * 获取用户角色
     *
     * @param userId QQ 号
     * @return 用户角色，未授权返回 null
     */
    fun getRole(userId: Long): Role? {
        if (userId == getAdminQQ()) return Role.ADMIN
        if (isOperator(userId)) return Role.OPERATOR
        if (isUser(userId)) return Role.USER
        return null  // 未授权
    }

    /**
     * 检查用户是否已授权（至少是 USER 角色）
     */
    fun isAuthorized(userId: Long): Boolean = getRole(userId) != null

    // ==================== 操作员管理 ====================

    fun addOperator(userId: Long): Boolean {
        ensureOperatorsLoaded()
        if (userId in cachedOperators) return false
        cachedOperators.add(userId)
        saveOperators()
        logger.info("Added operator: {}", userId)
        return true
    }

    fun removeOperator(userId: Long): Boolean {
        ensureOperatorsLoaded()
        if (!cachedOperators.remove(userId)) return false
        saveOperators()
        logger.info("Removed operator: {}", userId)
        return true
    }

    fun listOperators(): List<Long> {
        ensureOperatorsLoaded()
        return cachedOperators.toList()
    }

    // ==================== 用户管理（白名单） ====================

    fun addUser(userId: Long): Boolean {
        ensureUsersLoaded()
        if (userId in cachedUsers) return false
        cachedUsers.add(userId)
        saveUsers()
        logger.info("Added user: {}", userId)
        return true
    }

    fun removeUser(userId: Long): Boolean {
        ensureUsersLoaded()
        if (!cachedUsers.remove(userId)) return false
        saveUsers()
        logger.info("Removed user: {}", userId)
        return true
    }

    fun listUsers(): List<Long> {
        ensureUsersLoaded()
        return cachedUsers.toList()
    }

    // ==================== 非容器权限管理 ====================

    /**
     * 检查用户是否有非容器权限（可使用容器外工作区）
     *
     * ADMIN/OPERATOR 自动有此权限，USER 需要单独配置。
     *
     * @param userId QQ 号
     * @return true 如果有非容器权限
     */
    fun hasNonContainerPermission(userId: Long): Boolean {
        val role = getRole(userId)
        if (role == null) return false
        if (role.ordinal <= Role.OPERATOR.ordinal) return true
        return userId in userNonContainerPermissions
    }

    /**
     * 授予用户非容器权限
     *
     * @param userId QQ 号
     * @return true 如果授予成功，false 如果已有权限
     */
    fun grantNonContainerPermission(userId: Long): Boolean {
        if (userId in userNonContainerPermissions) return false
        userNonContainerPermissions.add(userId)
        saveNonContainerPermissions()
        logger.info("Granted non-container permission to user {}", userId)
        return true
    }

    /**
     * 撤销用户非容器权限
     *
     * @param userId QQ 号
     * @return true 如果撤销成功
     */
    fun revokeNonContainerPermission(userId: Long): Boolean {
        if (!userNonContainerPermissions.remove(userId)) return false
        saveNonContainerPermissions()
        logger.info("Revoked non-container permission from user {}", userId)
        return true
    }

    /**
     * 列出有非容器权限的用户
     *
     * @return 有非容器权限的用户 QQ 号列表
     */
    fun listNonContainerUsers(): List<Long> {
        return userNonContainerPermissions.toList()
    }

    // ==================== 内部实现 ====================

    private fun getAdminQQ(): Long {
        if (cachedAdminQQ == -1L) {
            cachedAdminQQ = core.config.settingService.get(NapCatSettings.AdminQQ()).value
        }
        return cachedAdminQQ
    }

    private fun isOperator(userId: Long): Boolean {
        ensureOperatorsLoaded()
        return userId in cachedOperators
    }

    private fun isUser(userId: Long): Boolean {
        ensureUsersLoaded()
        return userId in cachedUsers
    }

    private fun ensureOperatorsLoaded() {
        if (operatorsLoaded) return
        synchronized(this) {
            if (operatorsLoaded) return
            loadList("operators") { cachedOperators.addAll(it) }
            operatorsLoaded = true
        }
    }

    private fun ensureUsersLoaded() {
        if (usersLoaded) return
        synchronized(this) {
            if (usersLoaded) return
            loadList("users") { cachedUsers.addAll(it) }
            loadList("non_container") { userNonContainerPermissions.addAll(it) }
            usersLoaded = true
        }
    }

    private fun loadList(key: String, addTo: (List<Long>) -> Unit) {
        try {
            val element = store.get() ?: return
            val arr = element.jsonObject[key]?.jsonArray ?: return
            val ids = arr.map { it.jsonPrimitive.content.toLong() }
            addTo(ids)
        } catch (e: Exception) {
            logger.warn("Failed to load list: {}", key, e)
        }
    }

    private fun saveOperators() = saveList("operators", cachedOperators)
    private fun saveUsers() = saveList("users", cachedUsers)
    private fun saveNonContainerPermissions() = saveList("non_container", userNonContainerPermissions.toList())

    private fun saveList(key: String, list: List<Long>) {
        try {
            // 读取现有数据，合并后写回
            val existing = try {
                store.get()?.jsonObject
            } catch (e: Exception) {
                null
            }

            val obj = buildJsonObject {
                // 保留其他 key
                existing?.forEach { (k, v) ->
                    if (k != key) put(k, v)
                }
                // 更新当前 key
                put(key, buildJsonArray {
                    list.forEach { add(JsonPrimitive(it.toString())) }
                })
            }
            store.set(obj)
        } catch (e: Exception) {
            logger.error("Failed to save list: {}", key, e)
        }
    }
}
