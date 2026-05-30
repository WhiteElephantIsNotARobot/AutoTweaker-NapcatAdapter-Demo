package io.github.autotweaker.demo.adapter.napcat.model.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 通知事件基类
 *
 * 群组或好友发生状态变化时触发。
 */
@Serializable
sealed class NoticeEvent : OneBotEvent() {
    /** 通知类型 */
    @SerialName("notice_type") abstract val noticeType: NoticeType
}

/**
 * 群成员增加通知
 *
 * 新成员加入群时触发。
 *
 * @property time 事件时间戳
 * @property selfId 机器人 QQ 号
 * @property noticeType 通知类型
 * @property groupId 群号
 * @property userId 新成员 QQ 号
 * @property operatorId 操作者 QQ 号（邀请时）
 */
@Serializable
data class GroupIncreaseNoticeEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("notice_type") override val noticeType: NoticeType,
    @SerialName("group_id") val groupId: Long,
    @SerialName("user_id") val userId: Long,
    @SerialName("operator_id") val operatorId: Long? = null
) : NoticeEvent()

/**
 * 群成员减少通知
 *
 * 成员离开或被踢出群时触发。
 *
 * @property time 事件时间戳
 * @property selfId 机器人 QQ 号
 * @property noticeType 通知类型
 * @property groupId 群号
 * @property userId 离开的成员 QQ 号
 * @property operatorId 操作者 QQ 号（被踢时）
 */
@Serializable
data class GroupDecreaseNoticeEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("notice_type") override val noticeType: NoticeType,
    @SerialName("group_id") val groupId: Long,
    @SerialName("user_id") val userId: Long,
    @SerialName("operator_id") val operatorId: Long? = null
) : NoticeEvent()

/**
 * 群禁言通知
 *
 * 群成员被禁言或解除禁言时触发。
 *
 * @property time 事件时间戳
 * @property selfId 机器人 QQ 号
 * @property noticeType 通知类型
 * @property groupId 群号
 * @property userId 被禁言的成员 QQ 号
 * @property operatorId 操作者 QQ 号
 * @property duration 禁言时长（秒），0 表示解除禁言
 */
@Serializable
data class GroupBanNoticeEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("notice_type") override val noticeType: NoticeType,
    @SerialName("group_id") val groupId: Long,
    @SerialName("user_id") val userId: Long,
    @SerialName("operator_id") val operatorId: Long,
    val duration: Int
) : NoticeEvent()

/**
 * 群消息撤回通知
 *
 * 群消息被撤回时触发。
 *
 * @property time 事件时间戳
 * @property selfId 机器人 QQ 号
 * @property noticeType 通知类型
 * @property groupId 群号
 * @property userId 消息发送者 QQ 号
 * @property operatorId 操作者 QQ 号（撤回他人消息时）
 * @property messageId 被撤回的消息 ID
 */
@Serializable
data class GroupRecallNoticeEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("notice_type") override val noticeType: NoticeType,
    @SerialName("group_id") val groupId: Long,
    @SerialName("user_id") val userId: Long,
    @SerialName("operator_id") val operatorId: Long,
    @SerialName("message_id") val messageId: Int
) : NoticeEvent()

/**
 * 群管理员变动通知
 *
 * 群管理员被设置或取消时触发。
 *
 * @property time 事件时间戳
 * @property selfId 机器人 QQ 号
 * @property noticeType 通知类型
 * @property groupId 群号
 * @property userId 被操作的成员 QQ 号
 * @property subType 子类型。已知值: set, unset
 */
@Serializable
data class GroupAdminNoticeEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("notice_type") override val noticeType: NoticeType,
    @SerialName("group_id") val groupId: Long,
    @SerialName("user_id") val userId: Long,
    @SerialName("sub_type") val subType: String
) : NoticeEvent()

/**
 * 群文件上传通知
 *
 * 群成员上传文件时触发。
 *
 * @property time 事件时间戳
 * @property selfId 机器人 QQ 号
 * @property noticeType 通知类型
 * @property groupId 群号
 * @property userId 上传者 QQ 号
 * @property file 上传的文件信息
 */
@Serializable
data class GroupUploadNoticeEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("notice_type") override val noticeType: NoticeType,
    @SerialName("group_id") val groupId: Long,
    @SerialName("user_id") val userId: Long,
    val file: UploadFileInfo
) : NoticeEvent() {
    /**
     * 上传文件信息
     *
     * @property id 文件 ID
     * @property name 文件名
     * @property size 文件大小（字节）
     * @property url 文件下载 URL
     */
    @Serializable
    data class UploadFileInfo(
        val id: String,
        val name: String,
        val size: Long,
        val url: String
    )
}

/**
 * 群名片修改通知
 *
 * 群成员修改群名片时触发。
 *
 * @property time 事件时间戳
 * @property selfId 机器人 QQ 号
 * @property noticeType 通知类型
 * @property groupId 群号
 * @property userId 修改者的 QQ 号
 * @property cardNew 新的群名片
 * @property cardOld 旧的群名片
 */
@Serializable
data class GroupCardNoticeEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("notice_type") override val noticeType: NoticeType,
    @SerialName("group_id") val groupId: Long,
    @SerialName("user_id") val userId: Long,
    @SerialName("card_new") val cardNew: String,
    @SerialName("card_old") val cardOld: String
) : NoticeEvent()

/**
 * 好友添加通知
 *
 * 收到好友请求并同意后触发。
 *
 * @property time 事件时间戳
 * @property selfId 机器人 QQ 号
 * @property noticeType 通知类型
 * @property userId 新好友的 QQ 号
 */
@Serializable
data class FriendAddNoticeEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("notice_type") override val noticeType: NoticeType,
    @SerialName("user_id") val userId: Long
) : NoticeEvent()

/**
 * 好友消息撤回通知
 *
 * 好友消息被撤回时触发。
 *
 * @property time 事件时间戳
 * @property selfId 机器人 QQ 号
 * @property noticeType 通知类型
 * @property userId 好友 QQ 号
 * @property messageId 被撤回的消息 ID
 */
@Serializable
data class FriendRecallNoticeEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("notice_type") override val noticeType: NoticeType,
    @SerialName("user_id") val userId: Long,
    @SerialName("message_id") val messageId: Int
) : NoticeEvent()

/**
 * 通知事件（戳一戳等）
 *
 * @property time 事件时间戳
 * @property selfId 机器人 QQ 号
 * @property noticeType 通知类型
 * @property subType 子类型（如 poke 表示戳一戳）
 * @property groupId 群号（群内通知时）
 * @property userId 发起者 QQ 号
 * @property targetId 目标 QQ 号
 */
@Serializable
data class NotifyNoticeEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("notice_type") override val noticeType: NoticeType,
    @SerialName("sub_type") val subType: String,
    @SerialName("group_id") val groupId: Long? = null,
    @SerialName("user_id") val userId: Long,
    @SerialName("target_id") val targetId: Long? = null
) : NoticeEvent()
