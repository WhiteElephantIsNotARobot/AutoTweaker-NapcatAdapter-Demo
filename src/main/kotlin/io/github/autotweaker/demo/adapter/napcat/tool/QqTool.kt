package io.github.autotweaker.demo.adapter.napcat.tool

import com.google.auto.service.AutoService
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.demo.adapter.napcat.NapCatAdapter
import io.github.autotweaker.demo.adapter.napcat.model.message.MessageSegment
import kotlinx.serialization.json.*

/**
 * QQ 工具
 *
 * 让 LLM 通过工具调用机制访问 NapCat OneBot 11 API 的全部接口。
 * 工具名称为 "qq"，最终调用名格式为 `qq_<functionName>`。
 *
 * 包含 21 个函数，覆盖：
 * - 消息收发与管理
 * - 好友/群组查询
 * - 群管理操作（踢人、禁言、设管等）
 * - 系统信息查询
 * - 文件信息获取
 * - 合并转发消息获取
 */
@AutoService(Tool::class)
class QqTool : Tool {

    override val meta = Tool.Meta(
        name = "qq",
        description = "QQ 机器人操作工具，提供消息收发、群管理、好友查询等功能",
        functions = listOf(
            // ==================== 消息 ====================
            Tool.Function(
                name = "send_private_message",
                description = "发送私聊消息",
                parameters = mapOf(
                    "userId" to Tool.Function.Property(
                        description = "目标用户 QQ 号",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    ),
                    "message" to Tool.Function.Property(
                        description = "消息内容",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    )
                )
            ),
            Tool.Function(
                name = "send_group_message",
                description = "发送群消息",
                parameters = mapOf(
                    "groupId" to Tool.Function.Property(
                        description = "目标群号",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    ),
                    "message" to Tool.Function.Property(
                        description = "消息内容",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    )
                )
            ),
            Tool.Function(
                name = "delete_message",
                description = "撤回消息",
                parameters = mapOf(
                    "messageId" to Tool.Function.Property(
                        description = "要撤回的消息 ID",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.IntegerValue()
                    )
                )
            ),
            Tool.Function(
                name = "get_message",
                description = "获取消息详情",
                parameters = mapOf(
                    "messageId" to Tool.Function.Property(
                        description = "消息 ID",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.IntegerValue()
                    )
                )
            ),
            // ==================== 好友 ====================
            Tool.Function(
                name = "get_friend_list",
                description = "获取好友列表",
                parameters = emptyMap()
            ),
            // ==================== 群组查询 ====================
            Tool.Function(
                name = "get_group_list",
                description = "获取群列表",
                parameters = mapOf(
                    "noCache" to Tool.Function.Property(
                        description = "是否跳过缓存",
                        required = false,
                        valueType = Tool.Function.Property.ValueType.BooleanValue
                    )
                )
            ),
            Tool.Function(
                name = "get_group_member_list",
                description = "获取群成员列表",
                parameters = mapOf(
                    "groupId" to Tool.Function.Property(
                        description = "群号",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    )
                )
            ),
            Tool.Function(
                name = "get_group_member_info",
                description = "获取群成员信息",
                parameters = mapOf(
                    "groupId" to Tool.Function.Property(
                        description = "群号",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    ),
                    "userId" to Tool.Function.Property(
                        description = "用户 QQ 号",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    )
                )
            ),
            Tool.Function(
                name = "get_group_msg_history",
                description = "获取群消息历史",
                parameters = mapOf(
                    "groupId" to Tool.Function.Property(
                        description = "群号",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    ),
                    "count" to Tool.Function.Property(
                        description = "获取数量，默认 20",
                        required = false,
                        valueType = Tool.Function.Property.ValueType.IntegerValue()
                    )
                )
            ),
            // ==================== 群管理 ====================
            Tool.Function(
                name = "kick_group_member",
                description = "踢出群成员",
                parameters = mapOf(
                    "groupId" to Tool.Function.Property(
                        description = "群号",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    ),
                    "userId" to Tool.Function.Property(
                        description = "要踢出的用户 QQ 号",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    ),
                    "reject" to Tool.Function.Property(
                        description = "是否拒绝再次加群",
                        required = false,
                        valueType = Tool.Function.Property.ValueType.BooleanValue
                    )
                )
            ),
            Tool.Function(
                name = "ban_group_member",
                description = "禁言群成员",
                parameters = mapOf(
                    "groupId" to Tool.Function.Property(
                        description = "群号",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    ),
                    "userId" to Tool.Function.Property(
                        description = "要禁言的用户 QQ 号",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    ),
                    "duration" to Tool.Function.Property(
                        description = "禁言时长（秒），0 表示解除禁言，默认 1800",
                        required = false,
                        valueType = Tool.Function.Property.ValueType.IntegerValue()
                    )
                )
            ),
            Tool.Function(
                name = "set_group_card",
                description = "设置群名片",
                parameters = mapOf(
                    "groupId" to Tool.Function.Property(
                        description = "群号",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    ),
                    "userId" to Tool.Function.Property(
                        description = "用户 QQ 号",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    ),
                    "card" to Tool.Function.Property(
                        description = "新的群名片",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    )
                )
            ),
            Tool.Function(
                name = "set_group_name",
                description = "设置群名称",
                parameters = mapOf(
                    "groupId" to Tool.Function.Property(
                        description = "群号",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    ),
                    "groupName" to Tool.Function.Property(
                        description = "新的群名称",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    )
                )
            ),
            Tool.Function(
                name = "set_group_admin",
                description = "设置或取消群管理员",
                parameters = mapOf(
                    "groupId" to Tool.Function.Property(
                        description = "群号",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    ),
                    "userId" to Tool.Function.Property(
                        description = "用户 QQ 号",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    ),
                    "enable" to Tool.Function.Property(
                        description = "true 设置管理员，false 取消管理员",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.BooleanValue
                    )
                )
            ),
            // ==================== 系统 ====================
            Tool.Function(
                name = "get_login_info",
                description = "获取机器人登录信息（QQ 号和昵称）",
                parameters = emptyMap()
            ),
            Tool.Function(
                name = "get_status",
                description = "获取机器人在线状态",
                parameters = emptyMap()
            ),
            Tool.Function(
                name = "get_version_info",
                description = "获取 NapCat 版本信息",
                parameters = emptyMap()
            ),
            // ==================== 文件 ====================
            Tool.Function(
                name = "get_image",
                description = "获取图片文件信息",
                parameters = mapOf(
                    "file" to Tool.Function.Property(
                        description = "图片文件 ID",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    )
                )
            ),
            Tool.Function(
                name = "get_record",
                description = "获取语音文件信息",
                parameters = mapOf(
                    "file" to Tool.Function.Property(
                        description = "语音文件 ID",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    ),
                    "outFormat" to Tool.Function.Property(
                        description = "输出格式（如 mp3），null 表示原格式",
                        required = false,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    )
                )
            ),
            Tool.Function(
                name = "get_file",
                description = "获取文件信息",
                parameters = mapOf(
                    "file" to Tool.Function.Property(
                        description = "文件 ID",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    )
                )
            ),
            // ==================== 合并转发 ====================
            Tool.Function(
                name = "get_forward_msg",
                description = "获取合并转发消息",
                parameters = mapOf(
                    "id" to Tool.Function.Property(
                        description = "合并转发消息 ID",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    )
                )
            )
        )
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun execute(input: Tool.ToolInput): Tool.ToolOutput {
        return try {
            val api = NapCatAdapter.napCatApi
            val args = input.arguments

            val result = when (input.functionName) {
                // 消息
                "send_private_message" -> {
                    val userId = args.long("userId")
                    val message = args.string("message")
                    api.sendPrivateMessage(userId, listOf(MessageSegment.Text(message)))
                }
                "send_group_message" -> {
                    val groupId = args.long("groupId")
                    val message = args.string("message")
                    api.sendGroupMessage(groupId, listOf(MessageSegment.Text(message)))
                }
                "delete_message" -> {
                    api.deleteMessage(args.int("messageId"))
                }
                "get_message" -> {
                    api.getMessage(args.int("messageId"))
                }
                // 好友
                "get_friend_list" -> {
                    api.getFriendList()
                }
                // 群组查询
                "get_group_list" -> {
                    api.getGroupList(args.booleanOrDefault("noCache", false))
                }
                "get_group_member_list" -> {
                    api.getGroupMemberList(args.long("groupId"))
                }
                "get_group_member_info" -> {
                    api.getGroupMemberInfo(args.long("groupId"), args.long("userId"))
                }
                "get_group_msg_history" -> {
                    api.getGroupMsgHistory(args.long("groupId"), count = args.intOrDefault("count", 20))
                }
                // 群管理
                "kick_group_member" -> {
                    api.setGroupKick(args.long("groupId"), args.long("userId"), args.booleanOrDefault("reject", false))
                }
                "ban_group_member" -> {
                    api.setGroupBan(args.long("groupId"), args.long("userId"), args.intOrDefault("duration", 1800))
                }
                "set_group_card" -> {
                    api.setGroupCard(args.long("groupId"), args.long("userId"), args.string("card"))
                }
                "set_group_name" -> {
                    api.setGroupName(args.long("groupId"), args.string("groupName"))
                }
                "set_group_admin" -> {
                    api.setGroupAdmin(args.long("groupId"), args.long("userId"), args.boolean("enable"))
                }
                // 系统
                "get_login_info" -> {
                    api.getLoginInfo()
                }
                "get_status" -> {
                    api.getStatus()
                }
                "get_version_info" -> {
                    api.getVersionInfo()
                }
                // 文件
                "get_image" -> {
                    api.getImage(args.string("file"))
                }
                "get_record" -> {
                    api.getRecord(args.string("file"), args.stringOrNull("outFormat"))
                }
                "get_file" -> {
                    api.getFile(args.string("file"))
                }
                // 合并转发
                "get_forward_msg" -> {
                    api.getForwardMsg(args.string("id"))
                }
                else -> return Tool.ToolOutput("未知函数: ${input.functionName}", false)
            }

            Tool.ToolOutput(json.encodeToString(result), true)
        } catch (e: Exception) {
            Tool.ToolOutput("执行失败: ${e.message}", false)
        }
    }

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.content ?: throw IllegalArgumentException("缺少参数: $key")

    private fun JsonObject.stringOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.long(key: String): Long =
        this[key]?.jsonPrimitive?.long ?: throw IllegalArgumentException("缺少参数: $key")

    private fun JsonObject.int(key: String): Int =
        this[key]?.jsonPrimitive?.int ?: throw IllegalArgumentException("缺少参数: $key")

    private fun JsonObject.intOrDefault(key: String, default: Int): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: default

    private fun JsonObject.boolean(key: String): Boolean =
        this[key]?.jsonPrimitive?.boolean ?: throw IllegalArgumentException("缺少参数: $key")

    private fun JsonObject.booleanOrDefault(key: String, default: Boolean): Boolean =
        this[key]?.jsonPrimitive?.booleanOrNull ?: default
}
