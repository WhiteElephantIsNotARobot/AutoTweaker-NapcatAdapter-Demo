package io.github.autotweaker.demo.adapter.napcat.tool

import kotlinx.serialization.Serializable

/**
 * QQ 工具函数定义
 *
 * 使用 sealed interface 实现多函数分派，每个子类对应一个函数。
 * Core 通过 kotlinx.serialization 反射自动从 data class 生成参数 schema。
 */
object QqToolFunctions {

    /** 所有函数的 sealed 接口 */
    @Serializable
    sealed interface Args

    // ==================== 消息 ====================

    @Serializable
    data class SendMessageArgs(
        val userId: Long,
        val message: String,
    ) : Args

    @Serializable
    data class SendGroupMessageArgs(
        val groupId: Long,
        val message: String,
    ) : Args

    @Serializable
    data class DeleteMessageArgs(
        val messageId: Int,
    ) : Args

    @Serializable
    data class GetMessageArgs(
        val messageId: Int,
    ) : Args

    // ==================== 好友 ====================

    @Serializable
    data object GetFriendListArgs : Args

    // ==================== 群组查询 ====================

    @Serializable
    data class GetGroupListArgs(
        val noCache: Boolean = false,
    ) : Args

    @Serializable
    data class GetGroupMemberListArgs(
        val groupId: Long,
    ) : Args

    @Serializable
    data class GetGroupMemberInfoArgs(
        val groupId: Long,
        val userId: Long,
    ) : Args

    @Serializable
    data class GetGroupMsgHistoryArgs(
        val groupId: Long,
        val count: Int = 20,
    ) : Args

    @Serializable
    data class GetPrivateMsgHistoryArgs(
        val userId: Long,
        val count: Int = 20,
    ) : Args

    // ==================== 群管理 ====================

    @Serializable
    data class KickGroupMemberArgs(
        val groupId: Long,
        val userId: Long,
        val reject: Boolean = false,
    ) : Args

    @Serializable
    data class BanGroupMemberArgs(
        val groupId: Long,
        val userId: Long,
        val duration: Int = 1800,
    ) : Args

    @Serializable
    data class SetGroupCardArgs(
        val groupId: Long,
        val userId: Long,
        val card: String,
    ) : Args

    @Serializable
    data class SetGroupNameArgs(
        val groupId: Long,
        val groupName: String,
    ) : Args

    @Serializable
    data class SetGroupAdminArgs(
        val groupId: Long,
        val userId: Long,
        val enable: Boolean,
    ) : Args

    // ==================== 系统 ====================

    @Serializable
    data object GetLoginInfoArgs : Args

    @Serializable
    data object GetStatusArgs : Args

    @Serializable
    data object GetVersionInfoArgs : Args

    // ==================== 文件 ====================

    @Serializable
    data class GetImageArgs(
        val file: String,
    ) : Args

    @Serializable
    data class GetRecordArgs(
        val file: String,
        val outFormat: String? = null,
    ) : Args

    @Serializable
    data class GetFileArgs(
        val file: String,
    ) : Args

    // ==================== 合并转发 ====================

    @Serializable
    data class GetForwardMsgArgs(
        val id: String,
    ) : Args
}
