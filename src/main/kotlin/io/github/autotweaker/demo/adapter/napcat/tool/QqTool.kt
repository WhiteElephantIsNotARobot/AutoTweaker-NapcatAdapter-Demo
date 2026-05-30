package io.github.autotweaker.demo.adapter.napcat.tool

import com.google.auto.service.AutoService
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.demo.adapter.napcat.NapCatAdapter
import io.github.autotweaker.demo.adapter.napcat.api.NapCatApi
import io.github.autotweaker.demo.adapter.napcat.model.message.MessageSegment
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.boolean
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.booleanOrDefault
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.int
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.intOrDefault
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.long
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.string
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.stringOrNull
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * QQ 工具
 *
 * 让 LLM 通过工具调用机制访问 NapCat OneBot 11 API 的全部接口。
 * 工具名称为 "qq"，最终调用名格式为 `qq_<functionName>`。
 *
 * 函数定义见 [QqToolFunctions]。
 */
@AutoService(Tool::class)
class QqTool : Tool {

    override val meta = Tool.Meta(
        name = "qq",
        description = "QQ 机器人操作工具，提供消息收发、群管理、好友查询等功能",
        functions = QqToolFunctions.functions
    )

    private val logger = LoggerFactory.getLogger(QqTool::class.java)

    /**
     * 获取 NapCatApi 实例
     *
     * 由于核心对 Adapter 和 Tool 使用不同的类加载器加载，
     * companion object 的静态字段在两个类加载器中是隔离的。
     * 此方法先尝试直接访问，失败后通过 ServiceLoader 查找 adapter 实例，
     * 再用反射读取 _napCatApi 字段。
     */
    private fun resolveApi(): NapCatApi {
        val toolCl = QqTool::class.java.classLoader
        val adapterCl = NapCatAdapter::class.java.classLoader
        val sameCl = toolCl === adapterCl
        logger.debug("resolveApi: Tool CL={}, Adapter CL={}, same={}", toolCl, adapterCl, sameCl)

        try {
            return NapCatAdapter.napCatApi
        } catch (e: Exception) {
            val hasCore = try { NapCatAdapter.core; true } catch (_: Exception) { false }
            logger.error(
                "NapCatApi 不可用: {}, Tool CL={}, Adapter CL={}, same={}, core={}",
                e.message, toolCl, adapterCl, sameCl, hasCore
            )
            throw IllegalStateException(
                "NapCatApi 服务不可用，请检查连接状态"
            )
        }
    }

    override suspend fun execute(input: Tool.ToolInput): Tool.ToolOutput {
        return try {
            val api = resolveApi()
            val args = input.arguments

            val result = when (input.functionName) {
                // 消息
                "send_private_message" -> {
                    api.sendPrivateMessage(args.long("userId"), listOf(MessageSegment.Text(args.string("message"))))
                }
                "send_group_message" -> {
                    api.sendGroupMessage(args.long("groupId"), listOf(MessageSegment.Text(args.string("message"))))
                }
                "delete_message" -> api.deleteMessage(args.int("messageId"))
                "get_message" -> api.getMessage(args.int("messageId"))
                // 好友
                "get_friend_list" -> api.getFriendList()
                // 群组查询
                "get_group_list" -> api.getGroupList(args.booleanOrDefault("noCache", false))
                "get_group_member_list" -> api.getGroupMemberList(args.long("groupId"))
                "get_group_member_info" -> api.getGroupMemberInfo(args.long("groupId"), args.long("userId"))
                "get_group_msg_history" -> api.getGroupMsgHistory(args.long("groupId"), count = args.intOrDefault("count", 20))
                "get_private_msg_history" -> api.getPrivateMsgHistory(args.long("userId"), count = args.intOrDefault("count", 20))
                // 群管理
                "kick_group_member" -> api.setGroupKick(args.long("groupId"), args.long("userId"), args.booleanOrDefault("reject", false))
                "ban_group_member" -> api.setGroupBan(args.long("groupId"), args.long("userId"), args.intOrDefault("duration", 1800))
                "set_group_card" -> api.setGroupCard(args.long("groupId"), args.long("userId"), args.string("card"))
                "set_group_name" -> api.setGroupName(args.long("groupId"), args.string("groupName"))
                "set_group_admin" -> api.setGroupAdmin(args.long("groupId"), args.long("userId"), args.boolean("enable"))
                // 系统
                "get_login_info" -> api.getLoginInfo()
                "get_status" -> api.getStatus()
                "get_version_info" -> api.getVersionInfo()
                // 文件
                "get_image" -> api.getImage(args.string("file"))
                "get_record" -> api.getRecord(args.string("file"), args.stringOrNull("outFormat"))
                "get_file" -> api.getFile(args.string("file"))
                // 合并转发
                "get_forward_msg" -> api.getForwardMsg(args.string("id"))
                else -> return Tool.ToolOutput("未知函数: ${input.functionName}", false)
            }

            val output = when (result) {
                is Unit -> "ok"
                is String -> result
                else -> result.toString()
            }
            Tool.ToolOutput(output, true)
        } catch (e: Exception) {
            logger.error("Tool execution failed: function={}", input.functionName, e)
            Tool.ToolOutput("执行失败，请稍后重试", false)
        }
    }
}
