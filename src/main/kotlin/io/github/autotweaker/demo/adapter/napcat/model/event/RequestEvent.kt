package io.github.autotweaker.demo.adapter.napcat.model.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 请求事件（加好友/加群）。 */
sealed interface RequestEvent : Event {
	@SerialName("request_type")
	val requestType: RequestType

	/** 请求标识，用于 set_*_add_request 回执。 */
	val flag: String
}

/** 好友请求。 */
@Serializable
data class FriendRequestEvent(
	override val time: Long,
	@SerialName("self_id") override val selfId: Long,
	@SerialName("request_type") override val requestType: RequestType,
	@SerialName("user_id") val userId: Long,
	val comment: String? = null,
	override val flag: String,
) : RequestEvent

/** 加群请求。subType: add/invite。 */
@Serializable
data class GroupRequestEvent(
	override val time: Long,
	@SerialName("self_id") override val selfId: Long,
	@SerialName("request_type") override val requestType: RequestType,
	@SerialName("sub_type") val subType: String,
	@SerialName("group_id") val groupId: Long,
	@SerialName("user_id") val userId: Long,
	val comment: String? = null,
	override val flag: String,
) : RequestEvent
