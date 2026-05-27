package io.github.autotweaker.demo.adapter.napcat.api

import io.github.autotweaker.demo.adapter.napcat.model.data.*
import io.github.autotweaker.demo.adapter.napcat.model.message.MessageChain

interface NapCatApi {
    // 系统
    suspend fun getLoginInfo(): LoginInfo
    suspend fun getStatus(): BotStatus
    suspend fun getVersionInfo(): VersionInfo

    // 消息
    suspend fun sendPrivateMessage(userId: Long, message: MessageChain): MessageResult
    suspend fun sendGroupMessage(groupId: Long, message: MessageChain): MessageResult
    suspend fun deleteMessage(messageId: Int)
    suspend fun getMessage(messageId: Int): MessageDetail

    // 好友
    suspend fun getFriendList(): List<FriendInfo>

    // 群组
    suspend fun getGroupList(noCache: Boolean = false): List<GroupInfo>
    suspend fun getGroupMemberList(groupId: Long): List<GroupMemberInfo>
    suspend fun getGroupMemberInfo(groupId: Long, userId: Long): GroupMemberInfo
    suspend fun setGroupKick(groupId: Long, userId: Long, rejectAddRequest: Boolean = false)
    suspend fun setGroupBan(groupId: Long, userId: Long, duration: Int = 1800)
    suspend fun setGroupCard(groupId: Long, userId: Long, card: String)
    suspend fun setGroupName(groupId: Long, groupName: String)
    suspend fun setGroupAdmin(groupId: Long, userId: Long, enable: Boolean)

    // 文件
    suspend fun getImage(file: String): FileInfo
    suspend fun getRecord(file: String, outFormat: String? = null): FileInfo
    suspend fun getFile(file: String): FileInfo
}
