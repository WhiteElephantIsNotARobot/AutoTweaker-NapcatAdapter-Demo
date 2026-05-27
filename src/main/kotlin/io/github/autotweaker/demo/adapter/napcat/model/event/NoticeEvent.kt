package io.github.autotweaker.demo.adapter.napcat.model.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class NoticeEvent : OneBotEvent() {
    @SerialName("notice_type") abstract val noticeType: NoticeType
}

@Serializable
data class GroupIncreaseNoticeEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("notice_type") override val noticeType: NoticeType,
    @SerialName("group_id") val groupId: Long,
    @SerialName("user_id") val userId: Long,
    @SerialName("operator_id") val operatorId: Long? = null
) : NoticeEvent()

@Serializable
data class GroupDecreaseNoticeEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("notice_type") override val noticeType: NoticeType,
    @SerialName("group_id") val groupId: Long,
    @SerialName("user_id") val userId: Long,
    @SerialName("operator_id") val operatorId: Long? = null
) : NoticeEvent()

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

@Serializable
data class GroupAdminNoticeEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("notice_type") override val noticeType: NoticeType,
    @SerialName("group_id") val groupId: Long,
    @SerialName("user_id") val userId: Long,
    @SerialName("sub_type") val subType: String
) : NoticeEvent()

@Serializable
data class GroupUploadNoticeEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("notice_type") override val noticeType: NoticeType,
    @SerialName("group_id") val groupId: Long,
    @SerialName("user_id") val userId: Long,
    val file: FileInfo
) : NoticeEvent() {
    @Serializable
    data class FileInfo(
        val id: String,
        val name: String,
        val size: Long,
        val url: String
    )
}

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

@Serializable
data class FriendAddNoticeEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("notice_type") override val noticeType: NoticeType,
    @SerialName("user_id") val userId: Long
) : NoticeEvent()

@Serializable
data class FriendRecallNoticeEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("notice_type") override val noticeType: NoticeType,
    @SerialName("user_id") val userId: Long,
    @SerialName("message_id") val messageId: Int
) : NoticeEvent()

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
