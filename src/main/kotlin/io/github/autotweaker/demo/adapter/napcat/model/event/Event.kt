package io.github.autotweaker.demo.adapter.napcat.model.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OneBot 11 事件根类型。
 *
 * 事件 JSON 中的 `post_type` 用于路由解析（见 ws 层），因此不在此建模。
 */
sealed interface Event {
	/** 事件时间戳（秒）。 */
	val time: Long

	/** 收到事件的机器人 QQ 号（各实现类以 @SerialName 标注 self_id）。 */
	val selfId: Long
}

@Serializable
enum class NoticeType {
	@SerialName("group_increase") GROUP_INCREASE,
	@SerialName("group_decrease") GROUP_DECREASE,
	@SerialName("group_ban") GROUP_BAN,
	@SerialName("group_recall") GROUP_RECALL,
	@SerialName("group_admin") GROUP_ADMIN,
	@SerialName("group_upload") GROUP_UPLOAD,
	@SerialName("group_card") GROUP_CARD,
	@SerialName("friend_add") FRIEND_ADD,
	@SerialName("friend_recall") FRIEND_RECALL,
	@SerialName("notify") NOTIFY,
}

@Serializable
enum class RequestType {
	@SerialName("friend") FRIEND,
	@SerialName("group") GROUP,
}

@Serializable
enum class MetaEventType {
	@SerialName("heartbeat") HEARTBEAT,
	@SerialName("lifecycle") LIFECYCLE,
}

/** heartbeat 事件携带的运行状态。 */
@Serializable
data class HeartbeatStatus(
	val online: Boolean,
	val good: Boolean,
)
