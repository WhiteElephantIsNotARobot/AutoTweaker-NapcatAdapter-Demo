package io.github.autotweaker.demo.adapter.napcat.model.event

import io.github.autotweaker.demo.adapter.napcat.model.common.FlexibleStringSerializer
import io.github.autotweaker.demo.adapter.napcat.model.data.Sender
import io.github.autotweaker.demo.adapter.napcat.model.message.MessageChain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 消息事件。 */
sealed interface MessageEvent : Event {
	@SerialName("message_id")
	@Serializable(with = FlexibleStringSerializer::class)
	val messageId: String

	/** 结构化消息。 */
	val message: MessageChain

	/** 原始消息文本（CQ 码或纯文本）。 */
	@SerialName("raw_message")
	val rawMessage: String

	val sender: Sender

	@SerialName("user_id")
	val userId: Long
}

/** 私聊消息。sub_type: friend/group/other。 */
@Serializable
data class PrivateMessageEvent(
	override val time: Long,
	@SerialName("self_id") override val selfId: Long,
	@SerialName("message_id")
	@Serializable(with = FlexibleStringSerializer::class)
	override val messageId: String,
	override val message: MessageChain,
	@SerialName("raw_message") override val rawMessage: String,
	override val sender: Sender,
	@SerialName("user_id") override val userId: Long,
	@SerialName("sub_type") val subType: String = "friend",
) : MessageEvent

/** 群消息。sub_type: normal/anonymous/notice。 */
@Serializable
data class GroupMessageEvent(
	override val time: Long,
	@SerialName("self_id") override val selfId: Long,
	@SerialName("message_id")
	@Serializable(with = FlexibleStringSerializer::class)
	override val messageId: String,
	override val message: MessageChain,
	@SerialName("raw_message") override val rawMessage: String,
	override val sender: Sender,
	@SerialName("user_id") override val userId: Long,
	@SerialName("group_id") val groupId: Long,
	@SerialName("sub_type") val subType: String = "normal",
) : MessageEvent
