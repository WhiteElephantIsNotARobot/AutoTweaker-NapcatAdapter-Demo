package io.github.autotweaker.demo.adapter.napcat.model.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 元事件。 */
sealed interface MetaEvent : Event {
	@SerialName("meta_event_type")
	val metaEventType: MetaEventType
}

/** 心跳。 */
@Serializable
data class HeartbeatMetaEvent(
	override val time: Long,
	@SerialName("self_id") override val selfId: Long,
	@SerialName("meta_event_type") override val metaEventType: MetaEventType,
	val status: HeartbeatStatus,
	val interval: Long? = null,
) : MetaEvent

/** 生命周期。subType: enable/disable/connect。 */
@Serializable
data class LifecycleMetaEvent(
	override val time: Long,
	@SerialName("self_id") override val selfId: Long,
	@SerialName("meta_event_type") override val metaEventType: MetaEventType,
	@SerialName("sub_type") val subType: String,
) : MetaEvent
