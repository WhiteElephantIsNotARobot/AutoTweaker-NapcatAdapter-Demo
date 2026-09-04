package io.github.autotweaker.demo.adapter.napcat.api

import io.github.autotweaker.demo.adapter.napcat.model.data.BotStatus
import io.github.autotweaker.demo.adapter.napcat.model.data.EssenceMsg
import io.github.autotweaker.demo.adapter.napcat.model.data.FileResource
import io.github.autotweaker.demo.adapter.napcat.model.data.ForwardNode
import io.github.autotweaker.demo.adapter.napcat.model.data.Friend
import io.github.autotweaker.demo.adapter.napcat.model.data.GroupFileList
import io.github.autotweaker.demo.adapter.napcat.model.data.GroupFileSystemInfo
import io.github.autotweaker.demo.adapter.napcat.model.data.GroupInfo
import io.github.autotweaker.demo.adapter.napcat.model.data.GroupMember
import io.github.autotweaker.demo.adapter.napcat.model.data.LoginInfo
import io.github.autotweaker.demo.adapter.napcat.model.data.MessageDetail
import io.github.autotweaker.demo.adapter.napcat.model.data.MessageResult
import io.github.autotweaker.demo.adapter.napcat.model.data.Stranger
import io.github.autotweaker.demo.adapter.napcat.model.data.VersionInfo
import io.github.autotweaker.demo.adapter.napcat.model.event.GroupMessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.PrivateMessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.message.MessageChain
import kotlinx.serialization.json.JsonObject

/** 通用发送目标类型。 */
enum class MessageType { PRIVATE, GROUP }

/**
 * 原始调用通道：任何 NapCat/OneBot action 均可通过 [raw] 透传，typed 接口未覆盖的扩展动作由此访问。
 */
interface RawNapCatApi {
	/**
	 * 发送任意 OneBot action。
	 *
	 * @param action 动作名（如 "send_private_msg"）
	 * @param params action 参数
	 * @return 响应 JSON（含 status/retcode/message/data 包装）
	 * @throws NapCatApiException 调用失败（retcode != 0 或 status != ok）
	 */
	suspend fun raw(action: String, params: JsonObject = JsonObject(emptyMap())): JsonObject
}

/**
 * NapCat OneBot 11 动作接口。
 *
 * 覆盖消息、好友、群、群文件、资源文件、OCR 等稳定动作；结构复杂或易随 NapCat 版本变化的动作
 * 返回原始 [JsonObject] 或通过 [RawNapCatApi.raw] 访问。
 *
 * 所有方法都是挂起函数。QQ 号类 id 使用 [Long]，消息/文件等 NapCat 哈希 id 使用 [String]。
 */
interface NapCatApi : RawNapCatApi {

	// ==================== 系统 ====================

	suspend fun getLoginInfo(): LoginInfo

	suspend fun getStatus(): BotStatus

	suspend fun getVersionInfo(): VersionInfo

	/** 清除缓存。 */
	suspend fun cleanCache()

	// ==================== 消息 ====================

	/** 发送私聊消息。 */
	suspend fun sendPrivateMessage(userId: Long, message: MessageChain): MessageResult

	/** 发送群消息。 */
	suspend fun sendGroupMessage(groupId: Long, message: MessageChain): MessageResult

	/**
	 * 通用发送。[userId]（私聊）与 [groupId]（群）按 [type] 二选一。
	 */
	suspend fun sendMessage(
		type: MessageType,
		userId: Long? = null,
		groupId: Long? = null,
		message: MessageChain,
	): MessageResult

	/** 撤回消息。 */
	suspend fun deleteMessage(messageId: String)

	/** 获取消息详情。 */
	suspend fun getMessage(messageId: String): MessageDetail

	/** 获取群消息历史。[messageSeq] 缺省从最新向前取。 */
	suspend fun getGroupMsgHistory(
		groupId: Long,
		messageSeq: Long? = null,
		count: Int = 20,
	): List<GroupMessageEvent>

	/** 获取私聊消息历史。 */
	suspend fun getFriendMsgHistory(
		userId: Long,
		messageSeq: Long? = null,
		count: Int = 20,
	): List<PrivateMessageEvent>

	/** 获取合并转发内容（节点结构随 NapCat 版本变化，按原始返回）。 */
	suspend fun getForwardMsg(messageId: String): JsonObject

	/** 发送群合并转发。nodes 为带发送者信息的内容节点。 */
	suspend fun sendGroupForwardMessage(groupId: Long, nodes: List<ForwardNode>): MessageResult

	/** 发送私聊合并转发。 */
	suspend fun sendPrivateForwardMessage(userId: Long, nodes: List<ForwardNode>): MessageResult

	// ==================== 好友 ====================

	suspend fun getFriendList(noCache: Boolean = false): List<Friend>

	/** 好友点赞。times 受 NapCat 频率限制。 */
	suspend fun sendLike(userId: Long, times: Int = 1)

	/** 处理好友添加请求。 */
	suspend fun setFriendAddRequest(flag: String, approve: Boolean = true, remark: String = "")

	suspend fun setFriendRemark(userId: Long, remark: String)

	suspend fun deleteFriend(userId: Long)

	/** 好友戳一戳。 */
	suspend fun friendPoke(userId: Long)

	// ==================== 群 ====================

	suspend fun getGroupList(noCache: Boolean = false): List<GroupInfo>

	suspend fun getGroupInfo(groupId: Long, noCache: Boolean = false): GroupInfo

	/** 处理加群请求。subType: add/invite。 */
	suspend fun setGroupAddRequest(flag: String, subType: String, approve: Boolean = true, reason: String = "")

	suspend fun setGroupKick(groupId: Long, userId: Long, rejectAddRequest: Boolean = false)

	/** 禁言/解除禁言，duration 为秒，0 解除。 */
	suspend fun setGroupBan(groupId: Long, userId: Long, duration: Long)

	suspend fun setGroupWholeBan(groupId: Long, enable: Boolean)

	suspend fun setGroupAdmin(groupId: Long, userId: Long, enable: Boolean)

	suspend fun setGroupCard(groupId: Long, userId: Long, card: String)

	suspend fun setGroupName(groupId: Long, groupName: String)

	/** 退出群聊。isDismiss 仅群主可解散。 */
	suspend fun setGroupLeave(groupId: Long, isDismiss: Boolean = false)

	suspend fun setGroupSpecialTitle(groupId: Long, userId: Long, specialTitle: String)

	suspend fun getGroupMemberList(groupId: Long, noCache: Boolean = false): List<GroupMember>

	suspend fun getGroupMemberInfo(groupId: Long, userId: Long, noCache: Boolean = false): GroupMember

	/** 群内戳一戳。 */
	suspend fun groupPoke(groupId: Long, userId: Long)

	// ==================== 精华消息 ====================

	suspend fun getEssenceMsgList(groupId: Long): List<EssenceMsg>

	suspend fun setEssenceMsg(messageId: String)

	suspend fun deleteEssenceMsg(messageId: String)

	// ==================== 群公告 ====================

	/** 发送文本群公告（NapCat 的 _send_group_notice）。 */
	suspend fun sendGroupNotice(groupId: Long, content: String): JsonObject

	suspend fun getGroupNotice(groupId: Long): JsonObject

	suspend fun delGroupNotice(groupId: Long, noticeId: String)

	// ==================== 群文件 ====================

	/**
	 * 上传群文件。
	 *
	 * @param file 本地路径或 http(s)/base64 资源
	 * @param folder 目标文件夹 id，留空为根目录
	 */
	suspend fun uploadGroupFile(groupId: Long, file: String, name: String, folder: String? = null)

	suspend fun uploadPrivateFile(userId: Long, file: String, name: String)

	suspend fun getGroupFileSystemInfo(groupId: Long): GroupFileSystemInfo

	suspend fun getGroupRootFiles(groupId: Long): GroupFileList

	suspend fun getGroupFilesByFolder(groupId: Long, folderId: String): GroupFileList

	suspend fun deleteGroupFile(groupId: Long, fileId: String, busid: Long)

	suspend fun createGroupFileFolder(groupId: Long, name: String): JsonObject

	suspend fun deleteGroupFolder(groupId: Long, folderId: String)

	suspend fun getGroupFileUrl(groupId: Long, fileId: String, busid: Long): JsonObject

	// ==================== 资源文件 ====================

	suspend fun getImage(file: String): FileResource

	suspend fun getRecord(file: String, outFormat: String? = null): FileResource

	suspend fun getFile(file: String): FileResource

	suspend fun canSendImage(): Boolean

	suspend fun canSendRecord(): Boolean

	/** OCR 图片识别。image 为图片路径/url/file。 */
	suspend fun ocrImage(image: String): JsonObject

	// ==================== 其他 ====================

	suspend fun getStrangerInfo(userId: Long, noCache: Boolean = false): Stranger
}
