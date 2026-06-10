package io.github.autotweaker.demo.adapter.napcat.tool

import com.google.auto.service.AutoService
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.demo.adapter.napcat.NapCatAdapter
import io.github.autotweaker.demo.adapter.napcat.api.NapCatApi
import io.github.autotweaker.demo.adapter.napcat.model.message.MessageSegment
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.Args
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.BanGroupMemberArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.DeleteMessageArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetFileArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetForwardMsgArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetFriendListArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetGroupListArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetGroupMemberInfoArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetGroupMemberListArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetGroupMsgHistoryArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetImageArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetLoginInfoArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetMessageArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetPrivateMsgHistoryArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetRecordArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetStatusArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetVersionInfoArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.KickGroupMemberArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.SendGroupMessageArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.SendMessageArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.SetGroupAdminArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.SetGroupCardArgs
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.SetGroupNameArgs
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * QQ 工具
 *
 * 让 LLM 通过工具调用机制访问 NapCat OneBot 11 API 的全部接口。
 * 工具名称为 "qq"，使用 sealed interface 实现多函数分派。
 *
 * 函数定义见 [QqToolFunctions]。
 */
@AutoService(Tool::class)
class QqTool : Tool<QqToolFunctions.Args> {

    override val argsSerializer = Args.serializer()
    override val name = "qq"
    override val description = "QQ 机器人操作工具，提供消息收发、群管理、好友查询等功能"

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun describe(): Map<KProperty1<*, *>, String> = mapOf(
        // 消息
        SendMessageArgs::userId to "目标用户 QQ 号",
        SendMessageArgs::message to "消息内容",
        SendGroupMessageArgs::groupId to "目标群号",
        SendGroupMessageArgs::message to "消息内容",
        DeleteMessageArgs::messageId to "要撤回的消息 ID",
        GetMessageArgs::messageId to "消息 ID",
        // 群组查询
        GetGroupListArgs::noCache to "是否跳过缓存，默认 false",
        GetGroupMemberListArgs::groupId to "群号",
        GetGroupMemberInfoArgs::groupId to "群号",
        GetGroupMemberInfoArgs::userId to "用户 QQ 号",
        GetGroupMsgHistoryArgs::groupId to "群号",
        GetGroupMsgHistoryArgs::count to "获取数量，默认 20",
        GetPrivateMsgHistoryArgs::userId to "用户 QQ 号",
        GetPrivateMsgHistoryArgs::count to "获取数量，默认 20",
        // 群管理
        KickGroupMemberArgs::groupId to "群号",
        KickGroupMemberArgs::userId to "要踢出的用户 QQ 号",
        KickGroupMemberArgs::reject to "是否拒绝再次加群，默认 false",
        BanGroupMemberArgs::groupId to "群号",
        BanGroupMemberArgs::userId to "要禁言的用户 QQ 号",
        BanGroupMemberArgs::duration to "禁言时长（秒），0 表示解除禁言，默认 1800",
        SetGroupCardArgs::groupId to "群号",
        SetGroupCardArgs::userId to "用户 QQ 号",
        SetGroupCardArgs::card to "新的群名片",
        SetGroupNameArgs::groupId to "群号",
        SetGroupNameArgs::groupName to "新的群名称",
        SetGroupAdminArgs::groupId to "群号",
        SetGroupAdminArgs::userId to "用户 QQ 号",
        SetGroupAdminArgs::enable to "true 设置管理员，false 取消管理员",
        // 文件
        GetImageArgs::file to "图片文件 ID",
        GetRecordArgs::file to "语音文件 ID",
        GetRecordArgs::outFormat to "输出格式（如 mp3），null 表示原格式",
        GetFileArgs::file to "文件 ID",
        // 合并转发
        GetForwardMsgArgs::id to "合并转发消息 ID",
    )

    override suspend fun describeFunctions(): Map<KClass<*>, String> = mapOf(
        SendMessageArgs::class to "发送私聊消息",
        SendGroupMessageArgs::class to "发送群消息",
        DeleteMessageArgs::class to "撤回消息",
        GetMessageArgs::class to "获取消息详情",
        GetFriendListArgs::class to "获取好友列表",
        GetGroupListArgs::class to "获取群列表",
        GetGroupMemberListArgs::class to "获取群成员列表",
        GetGroupMemberInfoArgs::class to "获取群成员信息",
        GetGroupMsgHistoryArgs::class to "获取群消息历史",
        GetPrivateMsgHistoryArgs::class to "获取私聊消息历史",
        KickGroupMemberArgs::class to "踢出群成员",
        BanGroupMemberArgs::class to "禁言群成员",
        SetGroupCardArgs::class to "设置群名片",
        SetGroupNameArgs::class to "设置群名称",
        SetGroupAdminArgs::class to "设置或取消群管理员",
        GetLoginInfoArgs::class to "获取机器人登录信息（QQ 号和昵称）",
        GetStatusArgs::class to "获取机器人在线状态",
        GetVersionInfoArgs::class to "获取 NapCat 版本信息",
        GetImageArgs::class to "获取图片文件信息",
        GetRecordArgs::class to "获取语音文件信息",
        GetFileArgs::class to "获取文件信息",
        GetForwardMsgArgs::class to "获取合并转发消息",
    )

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
                "Failed to resolve NapCatApi  toolCL={}  adapterCL={}  sameCL={}  hasCore={}",
                toolCl, adapterCl, sameCl, hasCore, e
            )
            throw IllegalStateException(
                "NapCatApi 服务不可用，请检查连接状态"
            )
        }
    }

    override suspend fun execute(args: Args, outputChannel: Channel<Tool.RuntimeOutput>?): Tool.ToolOutput {
        return try {
            val api = resolveApi()

            val result = when (args) {
                // 消息
                is SendMessageArgs ->
                    api.sendPrivateMessage(args.userId, listOf(MessageSegment.Text(args.message)))
                is SendGroupMessageArgs ->
                    api.sendGroupMessage(args.groupId, listOf(MessageSegment.Text(args.message)))
                is DeleteMessageArgs -> api.deleteMessage(args.messageId)
                is GetMessageArgs -> api.getMessage(args.messageId)
                // 好友
                is GetFriendListArgs -> api.getFriendList()
                // 群组查询
                is GetGroupListArgs -> api.getGroupList(args.noCache)
                is GetGroupMemberListArgs -> api.getGroupMemberList(args.groupId)
                is GetGroupMemberInfoArgs -> api.getGroupMemberInfo(args.groupId, args.userId)
                is GetGroupMsgHistoryArgs -> api.getGroupMsgHistory(args.groupId, count = args.count)
                is GetPrivateMsgHistoryArgs -> api.getPrivateMsgHistory(args.userId, count = args.count)
                // 群管理
                is KickGroupMemberArgs -> api.setGroupKick(args.groupId, args.userId, args.reject)
                is BanGroupMemberArgs -> api.setGroupBan(args.groupId, args.userId, args.duration)
                is SetGroupCardArgs -> api.setGroupCard(args.groupId, args.userId, args.card)
                is SetGroupNameArgs -> api.setGroupName(args.groupId, args.groupName)
                is SetGroupAdminArgs -> api.setGroupAdmin(args.groupId, args.userId, args.enable)
                // 系统
                is GetLoginInfoArgs -> api.getLoginInfo()
                is GetStatusArgs -> api.getStatus()
                is GetVersionInfoArgs -> api.getVersionInfo()
                // 文件
                is GetImageArgs -> api.getImage(args.file)
                is GetRecordArgs -> api.getRecord(args.file, args.outFormat)
                is GetFileArgs -> api.getFile(args.file)
                // 合并转发
                is GetForwardMsgArgs -> api.getForwardMsg(args.id)
            }

            val output = when (result) {
                is Unit -> "ok"
                is String -> result
                else -> result.toString()
            }
            Tool.ToolOutput(output, true)
        } catch (e: Exception) {
            logger.error("Failed to execute tool  tool=qq", e)
            Tool.ToolOutput("执行失败，请稍后重试", false)
        }
    }
}
