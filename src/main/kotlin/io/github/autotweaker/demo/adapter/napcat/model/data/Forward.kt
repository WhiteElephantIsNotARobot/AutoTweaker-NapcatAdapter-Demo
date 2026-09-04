package io.github.autotweaker.demo.adapter.napcat.model.data

import io.github.autotweaker.demo.adapter.napcat.model.message.MessageChain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 构造合并转发时的消息节点（node）。
 *
 * 序列化为 `{"type":"node","data":{...}}`，napcat 要求 node 至少包含 user_id、nickname 与 content。
 */
@Serializable
data class ForwardNode(
	@SerialName("user_id") val userId: Long,
	val nickname: String,
	val content: MessageChain,
)
