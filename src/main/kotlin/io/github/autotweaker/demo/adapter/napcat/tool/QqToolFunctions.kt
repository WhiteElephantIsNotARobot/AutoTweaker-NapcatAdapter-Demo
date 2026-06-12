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
    data class SendMessage(
        val userId: Long,
        val message: String,
    ) : Args

    @Serializable
    data class SendGroupMessage(
        val groupId: Long,
        val message: String,
    ) : Args

    @Serializable
    data class DeleteMessage(
        val messageId: Int,
    ) : Args

    @Serializable
    data class GetMessage(
        val messageId: Int,
    ) : Args

    // ==================== 好友 ====================

    @Serializable
    data object GetFriendList : Args

    // ==================== 群组查询 ====================

    @Serializable
    data class GetGroupList(
        val noCache: Boolean = false,
    ) : Args

    @Serializable
    data class GetGroupMemberList(
        val groupId: Long,
    ) : Args

    @Serializable
    data class GetGroupMemberInfo(
        val groupId: Long,
        val userId: Long,
    ) : Args

    @Serializable
    data class GetGroupMsgHistory(
        val groupId: Long,
        val count: Int = 20,
    ) : Args

    @Serializable
    data class GetPrivateMsgHistory(
        val userId: Long,
        val count: Int = 20,
    ) : Args

    // ==================== 群管理 ====================

    @Serializable
    data class KickGroupMember(
        val groupId: Long,
        val userId: Long,
        val reject: Boolean = false,
    ) : Args

    @Serializable
    data class BanGroupMember(
        val groupId: Long,
        val userId: Long,
        val duration: Int = 1800,
    ) : Args

    @Serializable
    data class SetGroupCard(
        val groupId: Long,
        val userId: Long,
        val card: String,
    ) : Args

    @Serializable
    data class SetGroupName(
        val groupId: Long,
        val groupName: String,
    ) : Args

    @Serializable
    data class SetGroupAdmin(
        val groupId: Long,
        val userId: Long,
        val enable: Boolean,
    ) : Args

    // ==================== 系统 ====================

    @Serializable
    data object GetLoginInfo : Args

    @Serializable
    data object GetStatus : Args

    @Serializable
    data object GetVersionInfo : Args

    // ==================== 文件 ====================

    @Serializable
    data class GetImage(
        val file: String,
    ) : Args

    @Serializable
    data class GetRecord(
        val file: String,
        val outFormat: String? = null,
    ) : Args

    @Serializable
    data class GetFile(
        val file: String,
    ) : Args

    // ==================== 合并转发 ====================

    @Serializable
    data class GetForwardMsg(
        val id: String,
    ) : Args
}
