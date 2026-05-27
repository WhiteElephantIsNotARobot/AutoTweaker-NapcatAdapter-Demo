package io.github.autotweaker.demo.adapter.napcat.model.event

import io.github.autotweaker.demo.adapter.napcat.model.data.Sender
import io.github.autotweaker.demo.adapter.napcat.model.message.MessageChain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class MessageEvent : OneBotEvent() {
    abstract val messageId: Int
    abstract val message: MessageChain
    @SerialName("raw_message") abstract val rawMessage: String
    abstract val sender: Sender
}

@Serializable
@SerialName("private")
data class PrivateMessageEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("message_id") override val messageId: Int,
    override val message: MessageChain,
    @SerialName("raw_message") override val rawMessage: String,
    override val sender: Sender,
    @SerialName("user_id") val userId: Long
) : MessageEvent()

@Serializable
@SerialName("group")
data class GroupMessageEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("message_id") override val messageId: Int,
    override val message: MessageChain,
    @SerialName("raw_message") override val rawMessage: String,
    override val sender: Sender,
    @SerialName("group_id") val groupId: Long,
    @SerialName("user_id") val userId: Long
) : MessageEvent()
