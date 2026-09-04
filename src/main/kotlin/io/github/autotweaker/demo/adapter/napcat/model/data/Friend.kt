package io.github.autotweaker.demo.adapter.napcat.model.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 好友。 */
@Serializable
data class Friend(
	@SerialName("user_id") val userId: Long,
	val nickname: String,
	val remark: String? = null,
)

/** 非好友用户信息（get_stranger_info）。 */
@Serializable
data class Stranger(
	@SerialName("user_id") val userId: Long,
	val nickname: String? = null,
	val sex: String? = null,
	val age: Int? = null,
	val qid: String? = null,
	val level: Int? = null,
	@SerialName("login_days") val loginDays: Int? = null,
	@SerialName("user_displayname") val userDisplayName: String? = null,
)
