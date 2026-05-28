package io.github.autotweaker.demo.adapter.napcat.command

import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.demo.adapter.napcat.api.NapCatApi
import io.github.autotweaker.demo.adapter.napcat.bridge.SessionManager
import io.github.autotweaker.demo.adapter.napcat.permission.PermissionManager
import io.github.autotweaker.demo.adapter.napcat.permission.Role

/**
 * 命令执行上下文
 *
 * 包含命令执行所需的全部信息。
 *
 * @property userId 发送者 QQ 号
 * @property groupId 群号，null 表示私聊
 * @property role 发送者角色，null 表示未授权
 * @property args 命令参数（不含命令名本身）
 * @property core CoreAPI 实例
 * @property napCat NapCat API 实例
 * @property permissionManager 权限管理器
 * @property sessionManager 会话管理器
 */
data class CommandContext(
    val userId: Long,
    val groupId: Long?,
    val role: Role?,
    val args: List<String>,
    val core: CoreAPI,
    val napCat: NapCatApi,
    val permissionManager: PermissionManager,
    val sessionManager: SessionManager
)
