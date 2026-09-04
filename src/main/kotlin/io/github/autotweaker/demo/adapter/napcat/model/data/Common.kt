package io.github.autotweaker.demo.adapter.napcat.model.data

import io.github.autotweaker.demo.adapter.napcat.model.common.FlexibleStringSerializer
import io.github.autotweaker.demo.adapter.napcat.model.message.MessageChain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 消息中的发送者对象。
 *
 * 群匿名消息的 user_id 可能缺失或为特殊值，因此置空。
 */
@Serializable
data class Sender(
	@SerialName("user_id") val userId: Long? = null,
	val nickname: String? = null,
	val card: String? = null,
	val role: String? = null,
	val title: String? = null,
	val sex: String? = null,
	val age: Int? = null,
	val level: String? = null,
)

/** 发送消息的返回结果。message_id 为 NapCat 哈希 id，可能是大整数。 */
@Serializable
data class MessageResult(
	@SerialName("message_id")
	@Serializable(with = FlexibleStringSerializer::class)
	val messageId: String,
)

/** get_msg 返回的消息详情。 */
@Serializable
data class MessageDetail(
	@SerialName("message_id")
	@Serializable(with = FlexibleStringSerializer::class)
	val messageId: String,
	@SerialName("real_id") val realId: String? = null,
	@SerialName("message_type") val messageType: String,
	val sender: Sender,
	val time: Long,
	val message: MessageChain,
	@SerialName("raw_message") val rawMessage: String? = null,
	@SerialName("group_id") val groupId: Long? = null,
	@SerialName("user_id") val userId: Long? = null,
)

/** 登录信息。 */
@Serializable
data class LoginInfo(
	@SerialName("user_id") val userId: Long,
	val nickname: String,
)

/** 运行状态。NapCat 返回的其余字段（stat 等）体积大且不稳定，按需忽略。 */
@Serializable
data class BotStatus(
	val online: Boolean,
	val good: Boolean,
)

/** 版本信息。 */
@Serializable
data class VersionInfo(
	@SerialName("app_name") val appName: String,
	@SerialName("app_version") val appVersion: String,
	@SerialName("protocol_version") val protocolVersion: String,
)
