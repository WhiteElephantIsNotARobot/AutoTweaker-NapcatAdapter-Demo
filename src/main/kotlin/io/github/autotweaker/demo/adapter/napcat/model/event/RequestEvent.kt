package io.github.autotweaker.demo.adapter.napcat.model.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class RequestEvent : OneBotEvent() {
    @SerialName("request_type") abstract val requestType: RequestType
    abstract val flag: String
}

@Serializable
data class FriendRequestEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("request_type") override val requestType: RequestType,
    override val flag: String,
    @SerialName("user_id") val userId: Long,
    val comment: String,
    val nick: String
) : RequestEvent()

@Serializable
data class GroupRequestEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("request_type") override val requestType: RequestType,
    override val flag: String,
    @SerialName("group_id") val groupId: Long,
    @SerialName("user_id") val userId: Long,
    val comment: String,
    @SerialName("sub_type") val subType: String
) : RequestEvent()
