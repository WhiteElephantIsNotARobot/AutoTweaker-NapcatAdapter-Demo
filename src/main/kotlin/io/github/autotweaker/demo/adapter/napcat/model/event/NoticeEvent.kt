package io.github.autotweaker.demo.adapter.napcat.model.event

import io.github.autotweaker.demo.adapter.napcat.model.common.FlexibleStringSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 通知事件。 */
sealed interface NoticeEvent : Event {
	@SerialName("notice_type")
	val noticeType: NoticeType
}

/** 群成员增加。operatorId 为邀请者（有人邀请入群时）。 */
@Serializable
data class GroupIncreaseNoticeEvent(
	override val time: Long,
	@SerialName("self_id") override val selfId: Long,
	@SerialName("notice_type") override val noticeType: NoticeType,
	@SerialName("group_id") val groupId: Long,
	@SerialName("user_id") val userId: Long,
	@SerialName("operator_id") val operatorId: Long,
) : NoticeEvent

/** 群成员减少。operatorId 为操作者（被踢时），主动退群时为空。 */
@Serializable
data class GroupDecreaseNoticeEvent(
	override val time: Long,
	@SerialName("self_id") override val selfId: Long,
	@SerialName("notice_type") override val noticeType: NoticeType,
	@SerialName("group_id") val groupId: Long,
	@SerialName("user_id") val userId: Long,
	@SerialName("operator_id") val operatorId: Long? = null,
) : NoticeEvent

/** 群禁言。duration 为 0 表示解除禁言。 */
@Serializable
data class GroupBanNoticeEvent(
	override val time: Long,
	@SerialName("self_id") override val selfId: Long,
	@SerialName("notice_type") override val noticeType: NoticeType,
	@SerialName("group_id") val groupId: Long,
	@SerialName("user_id") val userId: Long,
	@SerialName("operator_id") val operatorId: Long,
	val duration: Long,
) : NoticeEvent

/** 群消息撤回。 */
@Serializable
data class GroupRecallNoticeEvent(
	override val time: Long,
	@SerialName("self_id") override val selfId: Long,
	@SerialName("notice_type") override val noticeType: NoticeType,
	@SerialName("group_id") val groupId: Long,
	@SerialName("user_id") val userId: Long,
	@SerialName("operator_id") val operatorId: Long? = null,
	@SerialName("message_id")
	@Serializable(with = FlexibleStringSerializer::class)
	val messageId: String,
) : NoticeEvent

/** 群管理员变更。subType: set/unset。 */
@Serializable
data class GroupAdminNoticeEvent(
	override val time: Long,
	@SerialName("self_id") override val selfId: Long,
	@SerialName("notice_type") override val noticeType: NoticeType,
	@SerialName("group_id") val groupId: Long,
	@SerialName("user_id") val userId: Long,
	@SerialName("sub_type") val subType: String,
) : NoticeEvent

/** 群文件上传。 */
@Serializable
data class GroupUploadNoticeEvent(
	override val time: Long,
	@SerialName("self_id") override val selfId: Long,
	@SerialName("notice_type") override val noticeType: NoticeType,
	@SerialName("group_id") val groupId: Long,
	@SerialName("user_id") val userId: Long,
	val file: GroupFile,
) : NoticeEvent

/** 群文件基本信息（事件与 API 返回共用）。 */
@Serializable
data class GroupFile(
	val id: String,
	val name: String,
	val size: Long,
	val url: String? = null,
)

/** 群名片变更。 */
@Serializable
data class GroupCardNoticeEvent(
	override val time: Long,
	@SerialName("self_id") override val selfId: Long,
	@SerialName("notice_type") override val noticeType: NoticeType,
	@SerialName("group_id") val groupId: Long,
	@SerialName("user_id") val userId: Long,
	@SerialName("card_new") val cardNew: String,
	@SerialName("card_old") val cardOld: String,
) : NoticeEvent

/** 好友添加。 */
@Serializable
data class FriendAddNoticeEvent(
	override val time: Long,
	@SerialName("self_id") override val selfId: Long,
	@SerialName("notice_type") override val noticeType: NoticeType,
	@SerialName("user_id") val userId: Long,
) : NoticeEvent

/** 好友消息撤回。 */
@Serializable
data class FriendRecallNoticeEvent(
	override val time: Long,
	@SerialName("self_id") override val selfId: Long,
	@SerialName("notice_type") override val noticeType: NoticeType,
	@SerialName("user_id") val userId: Long,
	@SerialName("message_id")
	@Serializable(with = FlexibleStringSerializer::class)
	val messageId: String,
) : NoticeEvent

/**
 * 通知事件（notify）。常见的 sub_type：
 * - poke：戳一戳（userId 发起者，targetId 接收者）
 * - honor：群荣誉变更（userId 获得者，honor 荣誉名）
 */
@Serializable
data class NotifyNoticeEvent(
	override val time: Long,
	@SerialName("self_id") override val selfId: Long,
	@SerialName("notice_type") override val noticeType: NoticeType,
	@SerialName("sub_type") val subType: String,
	@SerialName("group_id") val groupId: Long? = null,
	@SerialName("user_id") val userId: Long,
	@SerialName("target_id") val targetId: Long? = null,
	val honor: String? = null,
) : NoticeEvent
