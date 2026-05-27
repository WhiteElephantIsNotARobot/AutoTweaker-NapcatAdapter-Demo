package io.github.autotweaker.demo.adapter.napcat.model.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class MetaEvent : OneBotEvent()

@Serializable
data class HeartbeatMetaEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    val status: HeartbeatStatus,
    val interval: Long
) : MetaEvent() {
    @Serializable
    data class HeartbeatStatus(
        val online: Boolean,
        val good: Boolean
    )
}

@Serializable
data class LifecycleMetaEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("sub_type") val subType: String
) : MetaEvent()
