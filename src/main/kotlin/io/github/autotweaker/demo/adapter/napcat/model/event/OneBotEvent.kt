package io.github.autotweaker.demo.adapter.napcat.model.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class OneBotEvent {
    abstract val time: Long
    @SerialName("self_id") abstract val selfId: Long
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
    @SerialName("notify") NOTIFY
}

@Serializable
enum class RequestType {
    @SerialName("friend") FRIEND,
    @SerialName("group") GROUP
}
