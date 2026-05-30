package io.github.autotweaker.demo.adapter.napcat.model.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 机器人登录信息
 *
 * @property userId 机器人 QQ 号
 * @property nickname 机器人昵称
 */
@Serializable
data class LoginInfo(
    @SerialName("user_id") val userId: Long,
    val nickname: String
)

/**
 * 机器人在线状态
 *
 * @property online 是否在线
 * @property good 是否正常运行
 */
@Serializable
data class BotStatus(
    val online: Boolean,
    val good: Boolean
)

/**
 * NapCat 版本信息
 *
 * @property appName 应用名称
 * @property appVersion 应用版本号
 * @property protocolVersion 协议版本
 */
@Serializable
data class VersionInfo(
    @SerialName("app_name") val appName: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("protocol_version") val protocolVersion: String
)

/**
 * 消息发送者信息
 *
 * @property userId 发送者 QQ 号
 * @property nickname 发送者昵称
 * @property card 群名片（仅群消息）
 * @property role 角色（owner/admin/member，仅群消息）
 */
@Serializable
data class Sender(
    @SerialName("user_id") val userId: Long,
    val nickname: String,
    val card: String? = null,
    val role: String? = null
)

/**
 * 好友信息
 *
 * @property userId 好友 QQ 号
 * @property nickname 好友昵称
 * @property remark 好友备注
 */
@Serializable
data class FriendInfo(
    @SerialName("user_id") val userId: Long,
    val nickname: String,
    val remark: String? = null
)

/**
 * 群信息
 *
 * @property groupId 群号
 * @property groupName 群名称
 * @property memberCount 成员数量
 * @property maxMemberCount 最大成员数量
 */
@Serializable
data class GroupInfo(
    @SerialName("group_id") val groupId: Long,
    @SerialName("group_name") val groupName: String,
    @SerialName("member_count") val memberCount: Int? = null,
    @SerialName("max_member_count") val maxMemberCount: Int? = null
)

/**
 * 群成员信息
 *
 * @property groupId 群号
 * @property userId 成员 QQ 号
 * @property nickname 成员昵称
 * @property card 群名片
 * @property role 角色（owner/admin/member）
 */
@Serializable
data class GroupMemberInfo(
    @SerialName("group_id") val groupId: Long,
    @SerialName("user_id") val userId: Long,
    val nickname: String,
    val card: String = "",
    val role: String = "member"
)

/**
 * 消息发送结果
 *
 * @property messageId 发送成功的消息 ID
 */
@Serializable
data class MessageResult(
    @SerialName("message_id") val messageId: Int
)

/**
 * 消息详情
 *
 * @property messageId 消息 ID
 * @property realId 真实消息 ID
 * @property messageType 消息类型（private/group）
 * @property sender 发送者信息
 * @property time 消息时间戳
 * @property message 消息内容（原始格式）
 * @property rawMessage 原始消息文本
 * @property groupId 群号（仅群消息）
 */
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

/**
 * 原始消息段
 *
 * @property type 消息段类型
 * @property data 消息段数据
 */
@Serializable
data class RawMessageSegment(
    val type: String,
    val data: JsonObject? = null
)

/**
 * 文件信息
 *
 * @property file 文件 ID
 * @property url 文件下载 URL
 * @property fileSize 文件大小（字节）
 * @property fileName 文件名
 * @property path 文件路径
 */
@Serializable
data class FileInfo(
    val file: String? = null,
    val url: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    @SerialName("file_name") val fileName: String? = null,
    val path: String? = null
)

/**
 * OneBot 11 API 响应包装
 *
 * @property T 响应数据类型
 * @property status 响应状态（ok/failed）
 * @property retcode 返回码（0 表示成功）
 * @property data 响应数据
 * @property message 错误信息
 * @property echo 请求回显 ID
 */
@Serializable
data class ApiResponse<T>(
    val status: String,
    val retcode: Int,
    val data: T? = null,
    val message: String? = null,
    val echo: String? = null
)
