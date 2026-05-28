package io.github.autotweaker.demo.adapter.napcat.model.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 合并转发消息
 *
 * @property selfId 机器人 QQ 号
 * @property userId 发送者 QQ 号
 * @property time 消息时间戳
 * @property messageId 消息 ID
 * @property sender 发送者信息
 * @property rawMessage 原始消息文本
 * @property message 消息内容（原始格式）
 * @property messageType 消息类型（private/group）
 * @property groupId 群号（仅群消息）
 */
@Serializable
data class ForwardMessage(
    @SerialName("self_id") val selfId: Long,
    @SerialName("user_id") val userId: Long,
    val time: Long,
    @SerialName("message_id") val messageId: Int,
    val sender: Sender,
    @SerialName("raw_message") val rawMessage: String,
    val message: List<RawMessageSegment>,
    @SerialName("message_type") val messageType: String? = null,
    @SerialName("group_id") val groupId: Long? = null
)
