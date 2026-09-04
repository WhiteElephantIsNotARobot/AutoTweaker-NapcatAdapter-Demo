package io.github.autotweaker.demo.adapter.napcat.api

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
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
import io.github.autotweaker.demo.adapter.napcat.model.message.MessageChainSerializer
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * [NapCatApi] 的抽象实现：typed 方法 -> OneBot action 请求与响应解码。
 *
 * 传输层（子类）负责把 [sendAction] 变成实际的 WS/HTTP 请求，并对响应做最终校验。
 */
abstract class NapCatApiBase(
	protected val json: Json,
) : NapCatApi, Loggable, Traceable {

	/**
	 * 发送 action 并返回响应 JSON（含 status/retcode/message/data）。
	 * 必须保证失败时抛出 [NapCatApiException]。
	 */
	protected abstract suspend fun sendAction(action: String, params: JsonObject): JsonObject

	override suspend fun raw(action: String, params: JsonObject): JsonObject = sendAction(action, params)

	private fun <T> listSerializer(element: KSerializer<T>): KSerializer<List<T>> = ListSerializer(element)

	// ==================== 底层解码 helper ====================

	/** 请求并要求 data 按 [deserializer] 解码。 */
	protected suspend fun <T> requestData(
		action: String,
		deserializer: DeserializationStrategy<T>,
		params: JsonObject,
	): T {
		val resp = sendAction(action, params)
		val data = resp["data"]
		if (data == null || data is JsonNull) {
			error(action, "missing data")
		}
		return decode(action, data, deserializer)
	}

	/** 请求并仅校验，不关心 data。 */
	protected suspend fun requestOk(action: String, params: JsonObject) {
		sendAction(action, params)
	}

	/** 请求并把 data 作为 [JsonObject] 返回；data 缺省/非对象时返回空对象。 */
	protected suspend fun requestJsonObject(action: String, params: JsonObject): JsonObject {
		val resp = sendAction(action, params)
		val data = resp["data"]
		return (data as? JsonObject) ?: EMPTY_PARAMS
	}

	/** 请求返回整个响应（data 可能为空），调用方自行解读。 */
	protected suspend fun requestRawResponse(action: String, params: JsonObject): JsonObject =
		sendAction(action, params)

	private fun <T> decode(
		action: String,
		data: JsonElement,
		deserializer: DeserializationStrategy<T>,
	): T = trace.catching { json.decodeFromJsonElement(deserializer, data) }
		.onFailure { e ->
			log.error("Failed to decode response  action={}  dataType={}", action, deserializer.descriptor.serialName, e)
		}
		.getOrThrow()

	// ==================== 系统 ====================

	override suspend fun getLoginInfo(): LoginInfo =
		requestData("get_login_info", LoginInfo.serializer(), EMPTY_PARAMS)

	override suspend fun getStatus(): BotStatus =
		requestData("get_status", BotStatus.serializer(), EMPTY_PARAMS)

	override suspend fun getVersionInfo(): VersionInfo =
		requestData("get_version_info", VersionInfo.serializer(), EMPTY_PARAMS)

	override suspend fun cleanCache() = requestOk("clean_cache", EMPTY_PARAMS)

	// ==================== 消息 ====================

	override suspend fun sendPrivateMessage(userId: Long, message: MessageChain): MessageResult {
		val params = buildJsonObject {
			put("user_id", userId)
			put("message", json.encodeToJsonElement(MessageChainSerializer, message))
		}
		return requestData("send_private_msg", MessageResult.serializer(), params)
	}

	override suspend fun sendGroupMessage(groupId: Long, message: MessageChain): MessageResult {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("message", json.encodeToJsonElement(MessageChainSerializer, message))
		}
		return requestData("send_group_msg", MessageResult.serializer(), params)
	}

	override suspend fun sendMessage(
		type: MessageType,
		userId: Long?,
		groupId: Long?,
		message: MessageChain,
	): MessageResult {
		val actionType = if (type == MessageType.PRIVATE) "private" else "group"
		val params = buildJsonObject {
			put("message_type", actionType)
			userId?.let { put("user_id", it) }
			groupId?.let { put("group_id", it) }
			put("message", json.encodeToJsonElement(MessageChainSerializer, message))
		}
		return requestData("send_msg", MessageResult.serializer(), params)
	}

	override suspend fun deleteMessage(messageId: String) {
		val params = buildJsonObject { put("message_id", messageId) }
		requestOk("delete_msg", params)
	}

	override suspend fun getMessage(messageId: String): MessageDetail {
		val params = buildJsonObject { put("message_id", messageId) }
		return requestData("get_msg", MessageDetail.serializer(), params)
	}

	override suspend fun getGroupMsgHistory(
		groupId: Long,
		messageSeq: Long?,
		count: Int,
	): List<GroupMessageEvent> {
		val params = buildJsonObject {
			put("group_id", groupId)
			messageSeq?.let { put("message_seq", it) }
			put("count", count)
		}
		return requestMessageList("get_group_msg_history", params, GroupMessageEvent.serializer())
	}

	override suspend fun getFriendMsgHistory(
		userId: Long,
		messageSeq: Long?,
		count: Int,
	): List<PrivateMessageEvent> {
		val params = buildJsonObject {
			put("user_id", userId)
			messageSeq?.let { put("message_seq", it) }
			put("count", count)
		}
		return requestMessageList("get_friend_msg_history", params, PrivateMessageEvent.serializer())
	}

	override suspend fun getForwardMsg(messageId: String): JsonObject {
		val params = buildJsonObject { put("id", messageId) }
		return requestJsonObject("get_forward_msg", params)
	}

	override suspend fun sendGroupForwardMessage(groupId: Long, nodes: List<ForwardNode>): MessageResult {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("messages", encodeNodes(nodes))
		}
		return requestForwardMessageResult("send_group_forward_msg", params)
	}

	override suspend fun sendPrivateForwardMessage(userId: Long, nodes: List<ForwardNode>): MessageResult {
		val params = buildJsonObject {
			put("user_id", userId)
			put("messages", encodeNodes(nodes))
		}
		return requestForwardMessageResult("send_private_forward_msg", params)
	}

	/** 历史接口：响应 data 形如 {"messages":[...]}。 */
	private suspend fun <T> requestMessageList(
		action: String,
		params: JsonObject,
		deserializer: DeserializationStrategy<T>,
	): List<T> {
		val obj = requestJsonObject(action, params)
		val messages = obj["messages"]?.jsonArray ?: return emptyList()
		return messages.map { element ->
			json.decodeFromJsonElement(deserializer, element)
		}
	}

	private fun encodeNodes(nodes: List<ForwardNode>): JsonArray = buildJsonArray {
		nodes.forEach { node ->
			add(buildJsonObject {
				put("type", "node")
				putJsonObject("data") {
					put("user_id", node.userId)
					put("nickname", node.nickname)
					put("content", json.encodeToJsonElement(MessageChainSerializer, node.content))
				}
			})
		}
	}

	/** 合并转发成功时部分实现不返回 message_id，此处降级为空串（不吞其它错误）。 */
	private suspend fun requestForwardMessageResult(action: String, params: JsonObject): MessageResult {
		val resp = sendAction(action, params)
		val data = resp["data"] as? JsonObject
		val id = data?.get("message_id")?.jsonPrimitive?.contentOrNull ?: ""
		return MessageResult(id)
	}

	// ==================== 好友 ====================

	override suspend fun getFriendList(noCache: Boolean): List<Friend> {
		val params = buildJsonObject { put("no_cache", noCache) }
		return requestData("get_friend_list", listSerializer(Friend.serializer()), params)
	}

	override suspend fun sendLike(userId: Long, times: Int) {
		val params = buildJsonObject {
			put("user_id", userId)
			put("times", times)
		}
		requestOk("send_like", params)
	}

	override suspend fun setFriendAddRequest(flag: String, approve: Boolean, remark: String) {
		val params = buildJsonObject {
			put("flag", flag)
			put("approve", approve)
			if (remark.isNotEmpty()) put("remark", remark)
		}
		requestOk("set_friend_add_request", params)
	}

	override suspend fun setFriendRemark(userId: Long, remark: String) {
		val params = buildJsonObject {
			put("user_id", userId)
			put("remark", remark)
		}
		requestOk("set_friend_remark", params)
	}

	override suspend fun deleteFriend(userId: Long) {
		val params = buildJsonObject { put("user_id", userId) }
		requestOk("delete_friend", params)
	}

	override suspend fun friendPoke(userId: Long) {
		val params = buildJsonObject { put("user_id", userId) }
		requestOk("friend_poke", params)
	}

	// ==================== 群 ====================

	override suspend fun getGroupList(noCache: Boolean): List<GroupInfo> {
		val params = buildJsonObject { put("no_cache", noCache) }
		return requestData("get_group_list", listSerializer(GroupInfo.serializer()), params)
	}

	override suspend fun getGroupInfo(groupId: Long, noCache: Boolean): GroupInfo {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("no_cache", noCache)
		}
		return requestData("get_group_info", GroupInfo.serializer(), params)
	}

	override suspend fun setGroupAddRequest(flag: String, subType: String, approve: Boolean, reason: String) {
		val params = buildJsonObject {
			put("flag", flag)
			put("sub_type", subType)
			put("approve", approve)
			if (reason.isNotEmpty()) put("reason", reason)
		}
		requestOk("set_group_add_request", params)
	}

	override suspend fun setGroupKick(groupId: Long, userId: Long, rejectAddRequest: Boolean) {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("user_id", userId)
			put("reject_add_request", rejectAddRequest)
		}
		requestOk("set_group_kick", params)
	}

	override suspend fun setGroupBan(groupId: Long, userId: Long, duration: Long) {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("user_id", userId)
			put("duration", duration)
		}
		requestOk("set_group_ban", params)
	}

	override suspend fun setGroupWholeBan(groupId: Long, enable: Boolean) {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("enable", enable)
		}
		requestOk("set_group_whole_ban", params)
	}

	override suspend fun setGroupAdmin(groupId: Long, userId: Long, enable: Boolean) {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("user_id", userId)
			put("enable", enable)
		}
		requestOk("set_group_admin", params)
	}

	override suspend fun setGroupCard(groupId: Long, userId: Long, card: String) {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("user_id", userId)
			put("card", card)
		}
		requestOk("set_group_card", params)
	}

	override suspend fun setGroupName(groupId: Long, groupName: String) {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("group_name", groupName)
		}
		requestOk("set_group_name", params)
	}

	override suspend fun setGroupLeave(groupId: Long, isDismiss: Boolean) {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("is_dismiss", isDismiss)
		}
		requestOk("set_group_leave", params)
	}

	override suspend fun setGroupSpecialTitle(groupId: Long, userId: Long, specialTitle: String) {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("user_id", userId)
			put("special_title", specialTitle)
		}
		requestOk("set_group_special_title", params)
	}

	override suspend fun getGroupMemberList(groupId: Long, noCache: Boolean): List<GroupMember> {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("no_cache", noCache)
		}
		return requestData("get_group_member_list", listSerializer(GroupMember.serializer()), params)
	}

	override suspend fun getGroupMemberInfo(groupId: Long, userId: Long, noCache: Boolean): GroupMember {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("user_id", userId)
			put("no_cache", noCache)
		}
		return requestData("get_group_member_info", GroupMember.serializer(), params)
	}

	override suspend fun groupPoke(groupId: Long, userId: Long) {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("user_id", userId)
		}
		requestOk("group_poke", params)
	}

	// ==================== 精华消息 ====================

	override suspend fun getEssenceMsgList(groupId: Long): List<EssenceMsg> {
		val params = buildJsonObject { put("group_id", groupId) }
		return requestData("get_essence_msg_list", listSerializer(EssenceMsg.serializer()), params)
	}

	override suspend fun setEssenceMsg(messageId: String) {
		val params = buildJsonObject { put("message_id", messageId) }
		requestOk("set_essence_msg", params)
	}

	override suspend fun deleteEssenceMsg(messageId: String) {
		val params = buildJsonObject { put("message_id", messageId) }
		requestOk("delete_essence_msg", params)
	}

	// ==================== 群公告 ====================

	override suspend fun sendGroupNotice(groupId: Long, content: String): JsonObject {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("content", content)
		}
		return requestRawResponse("_send_group_notice", params)
	}

	override suspend fun getGroupNotice(groupId: Long): JsonObject {
		val params = buildJsonObject { put("group_id", groupId) }
		return requestJsonObject("_get_group_notice", params)
	}

	override suspend fun delGroupNotice(groupId: Long, noticeId: String) {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("notice_id", noticeId)
		}
		requestOk("_del_group_notice", params)
	}

	// ==================== 群文件 ====================

	override suspend fun uploadGroupFile(groupId: Long, file: String, name: String, folder: String?) {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("file", file)
			put("name", name)
			folder?.let { put("folder", it) }
		}
		requestOk("upload_group_file", params)
	}

	override suspend fun uploadPrivateFile(userId: Long, file: String, name: String) {
		val params = buildJsonObject {
			put("user_id", userId)
			put("file", file)
			put("name", name)
		}
		requestOk("upload_private_file", params)
	}

	override suspend fun getGroupFileSystemInfo(groupId: Long): GroupFileSystemInfo {
		val params = buildJsonObject { put("group_id", groupId) }
		return requestData("get_group_file_system_info", GroupFileSystemInfo.serializer(), params)
	}

	override suspend fun getGroupRootFiles(groupId: Long): GroupFileList {
		val params = buildJsonObject { put("group_id", groupId) }
		return requestData("get_group_root_files", GroupFileList.serializer(), params)
	}

	override suspend fun getGroupFilesByFolder(groupId: Long, folderId: String): GroupFileList {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("folder_id", folderId)
		}
		return requestData("get_group_files_by_folder", GroupFileList.serializer(), params)
	}

	override suspend fun deleteGroupFile(groupId: Long, fileId: String, busid: Long) {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("file_id", fileId)
			put("busid", busid)
		}
		requestOk("delete_group_file", params)
	}

	override suspend fun createGroupFileFolder(groupId: Long, name: String): JsonObject {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("name", name)
		}
		return requestJsonObject("create_group_file_folder", params)
	}

	override suspend fun deleteGroupFolder(groupId: Long, folderId: String) {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("folder_id", folderId)
		}
		requestOk("delete_group_folder", params)
	}

	override suspend fun getGroupFileUrl(groupId: Long, fileId: String, busid: Long): JsonObject {
		val params = buildJsonObject {
			put("group_id", groupId)
			put("file_id", fileId)
			put("busid", busid)
		}
		return requestJsonObject("get_group_file_url", params)
	}

	// ==================== 资源文件 ====================

	override suspend fun getImage(file: String): FileResource {
		val params = buildJsonObject { put("file", file) }
		return requestData("get_image", FileResource.serializer(), params)
	}

	override suspend fun getRecord(file: String, outFormat: String?): FileResource {
		val params = buildJsonObject {
			put("file", file)
			outFormat?.let { put("out_format", it) }
		}
		return requestData("get_record", FileResource.serializer(), params)
	}

	override suspend fun getFile(file: String): FileResource {
		val params = buildJsonObject { put("file", file) }
		return requestData("get_file", FileResource.serializer(), params)
	}

	override suspend fun canSendImage(): Boolean =
		requestBoolField("can_send_image")

	override suspend fun canSendRecord(): Boolean =
		requestBoolField("can_send_record")

	private suspend fun requestBoolField(action: String): Boolean {
		val resp = sendAction(action, EMPTY_PARAMS)
		val data = resp["data"]?.jsonObject
		val yes = data?.get("yes")?.jsonPrimitive?.contentOrNull ?: return false
		return yes == "true" || yes == "1"
	}

	override suspend fun ocrImage(image: String): JsonObject {
		val params = buildJsonObject { put("image", image) }
		return requestJsonObject("ocr_image", params)
	}

	// ==================== 其他 ====================

	override suspend fun getStrangerInfo(userId: Long, noCache: Boolean): Stranger {
		val params = buildJsonObject {
			put("user_id", userId)
			put("no_cache", noCache)
		}
		return requestData("get_stranger_info", Stranger.serializer(), params)
	}

	// ==================== 异常 ====================

	private fun error(action: String, why: String): Nothing {
		log.error("NapCat action failed  action={}  why={}", action, why)
		throw NapCatApiException(-1, null, why)
	}

	companion object {
		val EMPTY_PARAMS: JsonObject = JsonObject(emptyMap())
	}
}
