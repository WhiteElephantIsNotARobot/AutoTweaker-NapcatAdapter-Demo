package io.github.autotweaker.demo.adapter.napcat.api

import io.github.autotweaker.demo.adapter.napcat.model.data.*
import io.github.autotweaker.demo.adapter.napcat.model.event.GroupMessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.PrivateMessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.message.MessageChain

/**
 * NapCat OneBot 11 API 接口
 *
 * 提供与 NapCat 机器人交互的类型安全方法，包括：
 * - 系统信息查询
 * - 消息发送与管理
 * - 好友/群组管理
 * - 文件获取
 *
 * 所有方法都是挂起函数，需要在协程中调用。
 */
interface NapCatApi {
    // ==================== 系统 API ====================

    /**
     * 获取机器人登录信息
     *
     * @return 包含 QQ 号和昵称的 [LoginInfo]
     */
    suspend fun getLoginInfo(): LoginInfo

    /**
     * 获取机器人在线状态
     *
     * @return 包含在线状态和健康状态的 [BotStatus]
     */
    suspend fun getStatus(): BotStatus

    /**
     * 获取 NapCat 版本信息
     *
     * @return 包含应用名称、版本号和协议版本的 [VersionInfo]
     */
    suspend fun getVersionInfo(): VersionInfo

    // ==================== 消息 API ====================

    /**
     * 发送私聊消息
     *
     * @param userId 目标用户的 QQ 号
     * @param message 消息内容链
     * @return 包含消息 ID 的 [MessageResult]
     */
    suspend fun sendPrivateMessage(userId: Long, message: MessageChain): MessageResult

    /**
     * 发送群消息
     *
     * @param groupId 目标群号
     * @param message 消息内容链
     * @return 包含消息 ID 的 [MessageResult]
     */
    suspend fun sendGroupMessage(groupId: Long, message: MessageChain): MessageResult

    /**
     * 撤回消息
     *
     * @param messageId 要撤回的消息 ID
     */
    suspend fun deleteMessage(messageId: Int)

    /**
     * 获取消息详情
     *
     * @param messageId 消息 ID
     * @return 消息详情 [MessageDetail]
     */
    suspend fun getMessage(messageId: Int): MessageDetail

    // ==================== 好友 API ====================

    /**
     * 获取好友列表
     *
     * @return 好友信息列表
     */
    suspend fun getFriendList(): List<FriendInfo>

    // ==================== 群组 API ====================

    /**
     * 获取群列表
     *
     * @param noCache 是否跳过缓存，默认 false
     * @return 群信息列表
     */
    suspend fun getGroupList(noCache: Boolean = false): List<GroupInfo>

    /**
     * 获取群成员列表
     *
     * @param groupId 群号
     * @return 群成员信息列表
     */
    suspend fun getGroupMemberList(groupId: Long): List<GroupMemberInfo>

    /**
     * 获取群成员信息
     *
     * @param groupId 群号
     * @param userId 用户 QQ 号
     * @return 群成员信息 [GroupMemberInfo]
     */
    suspend fun getGroupMemberInfo(groupId: Long, userId: Long): GroupMemberInfo

    /**
     * 踢出群成员
     *
     * @param groupId 群号
     * @param userId 要踢出的用户 QQ 号
     * @param rejectAddRequest 是否拒绝再次加群，默认 false
     */
    suspend fun setGroupKick(groupId: Long, userId: Long, rejectAddRequest: Boolean = false)

    /**
     * 禁言群成员
     *
     * @param groupId 群号
     * @param userId 要禁言的用户 QQ 号
     * @param duration 禁言时长（秒），0 表示解除禁言，默认 1800 秒（30 分钟）
     */
    suspend fun setGroupBan(groupId: Long, userId: Long, duration: Int = 1800)

    /**
     * 设置群名片
     *
     * @param groupId 群号
     * @param userId 用户 QQ 号
     * @param card 新的群名片
     */
    suspend fun setGroupCard(groupId: Long, userId: Long, card: String)

    /**
     * 设置群名称
     *
     * @param groupId 群号
     * @param groupName 新的群名称
     */
    suspend fun setGroupName(groupId: Long, groupName: String)

    /**
     * 设置群管理员
     *
     * @param groupId 群号
     * @param userId 用户 QQ 号
     * @param enable true 为设置管理员，false 为取消管理员
     */
    suspend fun setGroupAdmin(groupId: Long, userId: Long, enable: Boolean)

    // ==================== 消息历史 API ====================

    /**
     * 获取群消息历史
     *
     * @param groupId 群号
     * @param messageSeq 起始消息序号（可选，不传则从最新开始）
     * @param count 获取数量，默认 20
     * @return 消息列表
     */
    suspend fun getGroupMsgHistory(groupId: Long, messageSeq: Long? = null, count: Int = 20): List<GroupMessageEvent>

    /**
     * 获取私聊消息历史
     *
     * @param userId 用户 QQ 号
     * @param messageSeq 起始消息序号（可选，不传则从最新开始）
     * @param count 获取数量，默认 20
     * @return 消息列表
     */
    suspend fun getPrivateMsgHistory(userId: Long, messageSeq: Long? = null, count: Int = 20): List<PrivateMessageEvent>

    // ==================== 文件 API ====================

    /**
     * 获取图片信息
     *
     * @param file 图片文件 ID
     * @return 图片文件信息 [FileInfo]
     */
    suspend fun getImage(file: String): FileInfo

    /**
     * 获取语音信息
     *
     * @param file 语音文件 ID
     * @param outFormat 输出格式（如 "mp3"），null 表示原格式
     * @return 语音文件信息 [FileInfo]
     */
    suspend fun getRecord(file: String, outFormat: String? = null): FileInfo

    /**
     * 获取文件信息
     *
     * @param file 文件 ID
     * @return 文件信息 [FileInfo]
     */
    suspend fun getFile(file: String): FileInfo

    // ==================== 合并转发 API ====================

    /**
     * 获取合并转发消息
     *
     * @param id 合并转发消息 ID
     * @return 合并转发消息列表
     */
    suspend fun getForwardMsg(id: String): List<ForwardMessage>

    // ==================== 合并转发发送 API ====================

    /**
     * 发送私聊合并转发消息
     *
     * @param userId 目标用户的 QQ 号
     * @param message 合并转发节点列表
     * @return 包含消息 ID 的 [MessageResult]
     */
    suspend fun sendPrivateForwardMsg(userId: Long, message: MessageChain): MessageResult

    /**
     * 发送群合并转发消息
     *
     * @param groupId 目标群号
     * @param message 合并转发节点列表
     * @return 包含消息 ID 的 [MessageResult]
     */
    suspend fun sendGroupForwardMsg(groupId: Long, message: MessageChain): MessageResult
}
