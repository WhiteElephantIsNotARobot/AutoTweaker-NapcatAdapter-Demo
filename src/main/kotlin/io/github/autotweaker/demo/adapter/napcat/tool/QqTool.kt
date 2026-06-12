package io.github.autotweaker.demo.adapter.napcat.tool

import com.google.auto.service.AutoService
import io.github.autotweaker.api.trace.TraceRecorder
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.demo.adapter.napcat.NapCatAdapter
import io.github.autotweaker.demo.adapter.napcat.api.NapCatApi
import io.github.autotweaker.demo.adapter.napcat.model.message.MessageSegment
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.Args
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.BanGroupMember
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.DeleteMessage
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetFile
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetForwardMsg
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetFriendList
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetGroupList
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetGroupMemberInfo
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetGroupMemberList
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetGroupMsgHistory
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetImage
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetLoginInfo
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetMessage
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetPrivateMsgHistory
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetRecord
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetStatus
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.GetVersionInfo
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.KickGroupMember
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.SendGroupMessage
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.SendMessage
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.SetGroupAdmin
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.SetGroupCard
import io.github.autotweaker.demo.adapter.napcat.tool.QqToolFunctions.SetGroupName
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
    private val trace: TraceRecorder by lazy { NapCatAdapter.core.trace(this::class) }

    override suspend fun describe(): Map<KProperty1<*, *>, String> = mapOf(
        // 消息
        SendMessage::userId to "目标用户 QQ 号",
        SendMessage::message to "消息内容",
        SendGroupMessage::groupId to "目标群号",
        SendGroupMessage::message to "消息内容",
        DeleteMessage::messageId to "要撤回的消息 ID",
        GetMessage::messageId to "消息 ID",
        // 群组查询
        GetGroupList::noCache to "是否跳过缓存，默认 false",
        GetGroupMemberList::groupId to "群号",
        GetGroupMemberInfo::groupId to "群号",
        GetGroupMemberInfo::userId to "用户 QQ 号",
        GetGroupMsgHistory::groupId to "群号",
        GetGroupMsgHistory::count to "获取数量，默认 20",
        GetPrivateMsgHistory::userId to "用户 QQ 号",
        GetPrivateMsgHistory::count to "获取数量，默认 20",
        // 群管理
        KickGroupMember::groupId to "群号",
        KickGroupMember::userId to "要踢出的用户 QQ 号",
        KickGroupMember::reject to "是否拒绝再次加群，默认 false",
        BanGroupMember::groupId to "群号",
        BanGroupMember::userId to "要禁言的用户 QQ 号",
        BanGroupMember::duration to "禁言时长（秒），0 表示解除禁言，默认 1800",
        SetGroupCard::groupId to "群号",
        SetGroupCard::userId to "用户 QQ 号",
        SetGroupCard::card to "新的群名片",
        SetGroupName::groupId to "群号",
        SetGroupName::groupName to "新的群名称",
        SetGroupAdmin::groupId to "群号",
        SetGroupAdmin::userId to "用户 QQ 号",
        SetGroupAdmin::enable to "true 设置管理员，false 取消管理员",
        // 文件
        GetImage::file to "图片文件 ID",
        GetRecord::file to "语音文件 ID",
        GetRecord::outFormat to "输出格式（如 mp3），null 表示原格式",
        GetFile::file to "文件 ID",
        // 合并转发
        GetForwardMsg::id to "合并转发消息 ID",
    )

    override suspend fun describeFunctions(): Map<KClass<*>, String> = mapOf(
        SendMessage::class to "发送私聊消息",
        SendGroupMessage::class to "发送群消息",
        DeleteMessage::class to "撤回消息",
        GetMessage::class to "获取消息详情",
        GetFriendList::class to "获取好友列表",
        GetGroupList::class to "获取群列表",
        GetGroupMemberList::class to "获取群成员列表",
        GetGroupMemberInfo::class to "获取群成员信息",
        GetGroupMsgHistory::class to "获取群消息历史",
        GetPrivateMsgHistory::class to "获取私聊消息历史",
        KickGroupMember::class to "踢出群成员",
        BanGroupMember::class to "禁言群成员",
        SetGroupCard::class to "设置群名片",
        SetGroupName::class to "设置群名称",
        SetGroupAdmin::class to "设置或取消群管理员",
        GetLoginInfo::class to "获取机器人登录信息（QQ 号和昵称）",
        GetStatus::class to "获取机器人在线状态",
        GetVersionInfo::class to "获取 NapCat 版本信息",
        GetImage::class to "获取图片文件信息",
        GetRecord::class to "获取语音文件信息",
        GetFile::class to "获取文件信息",
        GetForwardMsg::class to "获取合并转发消息",
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
            trace.exception(e)
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
                is SendMessage ->
                    api.sendPrivateMessage(args.userId, listOf(MessageSegment.Text(args.message)))
                is SendGroupMessage ->
                    api.sendGroupMessage(args.groupId, listOf(MessageSegment.Text(args.message)))
                is DeleteMessage -> api.deleteMessage(args.messageId)
                is GetMessage -> api.getMessage(args.messageId)
                // 好友
                is GetFriendList -> api.getFriendList()
                // 群组查询
                is GetGroupList -> api.getGroupList(args.noCache)
                is GetGroupMemberList -> api.getGroupMemberList(args.groupId)
                is GetGroupMemberInfo -> api.getGroupMemberInfo(args.groupId, args.userId)
                is GetGroupMsgHistory -> api.getGroupMsgHistory(args.groupId, count = args.count)
                is GetPrivateMsgHistory -> api.getPrivateMsgHistory(args.userId, count = args.count)
                // 群管理
                is KickGroupMember -> api.setGroupKick(args.groupId, args.userId, args.reject)
                is BanGroupMember -> api.setGroupBan(args.groupId, args.userId, args.duration)
                is SetGroupCard -> api.setGroupCard(args.groupId, args.userId, args.card)
                is SetGroupName -> api.setGroupName(args.groupId, args.groupName)
                is SetGroupAdmin -> api.setGroupAdmin(args.groupId, args.userId, args.enable)
                // 系统
                is GetLoginInfo -> api.getLoginInfo()
                is GetStatus -> api.getStatus()
                is GetVersionInfo -> api.getVersionInfo()
                // 文件
                is GetImage -> api.getImage(args.file)
                is GetRecord -> api.getRecord(args.file, args.outFormat)
                is GetFile -> api.getFile(args.file)
                // 合并转发
                is GetForwardMsg -> api.getForwardMsg(args.id)
            }

            val output = when (result) {
                is Unit -> "ok"
                is String -> result
                else -> result.toString()
            }
            Tool.ToolOutput(output, true)
        } catch (e: Exception) {
            trace.exception(e)
            logger.error("Failed to execute tool  tool=qq", e)
            Tool.ToolOutput("执行失败，请稍后重试", false)
        }
    }
}
