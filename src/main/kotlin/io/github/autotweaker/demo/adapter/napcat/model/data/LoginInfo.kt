package io.github.autotweaker.demo.adapter.napcat.model.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginInfo(
    @SerialName("user_id") val userId: Long,
    val nickname: String
)

@Serializable
data class BotStatus(
    val online: Boolean,
    val good: Boolean
)

@Serializable
data class VersionInfo(
    @SerialName("app_name") val appName: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("protocol_version") val protocolVersion: String
)

@Serializable
data class Sender(
    @SerialName("user_id") val userId: Long,
    val nickname: String,
    val card: String? = null,
    val role: String? = null
)

@Serializable
data class FriendInfo(
    @SerialName("user_id") val userId: Long,
    val nickname: String,
    val remark: String? = null
)

@Serializable
data class GroupInfo(
    @SerialName("group_id") val groupId: Long,
    @SerialName("group_name") val groupName: String,
    @SerialName("member_count") val memberCount: Int? = null,
    @SerialName("max_member_count") val maxMemberCount: Int? = null
)

@Serializable
data class GroupMemberInfo(
    @SerialName("group_id") val groupId: Long,
    @SerialName("user_id") val userId: Long,
    val nickname: String,
    val card: String = "",
    val role: String = "member"
)

@Serializable
data class MessageResult(
    @SerialName("message_id") val messageId: Int
)

@Serializable
data class MessageDetail(
    @SerialName("message_id") val messageId: Int,
    @SerialName("real_id") val realId: Int? = null,
    @SerialName("message_type") val messageType: String,
    val sender: Sender,
    val time: Long,
    val message: List<RawMessageSegment>,
    @SerialName("raw_message") val rawMessage: String,
    @SerialName("group_id") val groupId: Long? = null
)

@Serializable
data class RawMessageSegment(
    val type: String,
    val data: Map<String, String>? = null
)

@Serializable
data class FileInfo(
    val file: String? = null,
    val url: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    @SerialName("file_name") val fileName: String? = null,
    val path: String? = null
)

@Serializable
data class ApiResponse<T>(
    val status: String,
    val retcode: Int,
    val data: T? = null,
    val message: String? = null,
    val echo: String? = null
)
