package io.github.autotweaker.demo.adapter.napcat.permission

import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.demo.adapter.napcat.config.NapCatSettings
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 权限管理器
 *
 * 基于三级角色体系管理用户权限：
 * - 管理员：通过 [NapCatSettings.AdminQQ] 配置，仅一个
 * - 操作员：通过 JsonStore 持久化，由管理员命令指定
 * - 用户：默认角色
 *
 * @property core CoreAPI 实例，用于访问配置服务
 */
class PermissionManager(private val core: CoreAPI) {

    private val logger = LoggerFactory.getLogger(PermissionManager::class.java)

    companion object {
        private const val OPERATOR_STORE_KEY = "operators"
    }

    private val operatorStore by lazy {
        core.config.jsonStore(PermissionManager::class)
    }

    /** 缓存的管理员 QQ 号，-1 表示未加载 */
    @Volatile
    private var cachedAdminQQ: Long = -1L

    /** 缓存的操作员列表，线程安全 */
    private val cachedOperators = CopyOnWriteArrayList<Long>()

    /** 操作员缓存是否已加载 */
    @Volatile
    private var operatorsLoaded = false

    /**
     * 获取用户角色
     *
     * @param userId QQ 号
     * @return 用户角色
     */
    fun getRole(userId: Long): Role {
        if (userId == getAdminQQ()) return Role.ADMIN
        if (isOperator(userId)) return Role.OPERATOR
        return Role.USER
    }

    /**
     * 添加操作员
     *
     * @param userId 要添加的 QQ 号
     * @return true 如果添加成功，false 如果已是操作员
     */
    fun addOperator(userId: Long): Boolean {
        ensureOperatorsLoaded()
        if (userId in cachedOperators) return false
        cachedOperators.add(userId)
        saveOperators()
        logger.info("Added operator: {}", userId)
        return true
    }

    /**
     * 移除操作员
     *
     * @param userId 要移除的 QQ 号
     * @return true 如果移除成功，false 如果不是操作员
     */
    fun removeOperator(userId: Long): Boolean {
        ensureOperatorsLoaded()
        if (!cachedOperators.remove(userId)) return false
        saveOperators()
        logger.info("Removed operator: {}", userId)
        return true
    }

    /**
     * 列出所有操作员
     *
     * @return 操作员 QQ 号列表
     */
    fun listOperators(): List<Long> {
        ensureOperatorsLoaded()
        return cachedOperators.toList()
    }

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

    private fun ensureOperatorsLoaded() {
        if (operatorsLoaded) return
        synchronized(this) {
            if (operatorsLoaded) return
            loadOperators()
            operatorsLoaded = true
        }
    }

    private fun loadOperators() {
        try {
            val element = operatorStore.get()
            if (element != null) {
                val operators = element.jsonArray.map { it.jsonPrimitive.content.toLong() }
                cachedOperators.clear()
                cachedOperators.addAll(operators)
            }
        } catch (e: Exception) {
            logger.warn("Failed to load operators", e)
        }
    }

    private fun saveOperators() {
        try {
            val array = buildJsonArray {
                cachedOperators.forEach { add(JsonPrimitive(it.toString())) }
            }
            operatorStore.set(array)
        } catch (e: Exception) {
            logger.error("Failed to save operators", e)
        }
    }
}
