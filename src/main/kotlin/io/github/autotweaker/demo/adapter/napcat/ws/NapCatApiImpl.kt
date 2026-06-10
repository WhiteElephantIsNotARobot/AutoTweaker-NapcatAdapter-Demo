package io.github.autotweaker.demo.adapter.napcat.ws

import io.github.autotweaker.api.trace.TraceRecorder
import io.github.autotweaker.demo.adapter.napcat.NapCatAdapter
import io.github.autotweaker.demo.adapter.napcat.api.NapCatApi
import io.github.autotweaker.demo.adapter.napcat.model.data.*
import io.github.autotweaker.demo.adapter.napcat.model.event.GroupMessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.PrivateMessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.message.MessageChain
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * NapCat API 抽象实现
 *
 * 实现 [NapCatApi] 接口的所有 21 个 API 方法，
 * 将具体的 API 调用委托给子类提供的 [callApi] 方法。
 *
 * @property json JSON 序列化配置
 */
abstract class NapCatApiImpl(
    protected val json: Json
) : NapCatApi {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val trace: TraceRecorder by lazy { NapCatAdapter.core.trace(this::class) }

    /**
     * 发送 OneBot API 请求并获取原始 JSON 响应
     *
     * @param action API 动作名称
     * @param params 请求参数
     * @return 原始 JSON 响应
     */
    protected abstract suspend fun callApi(
        action: String,
        params: Map<String, JsonElement>? = null
    ): JsonObject

    /**
     * 发送 OneBot API 请求并反序列化响应数据
     */
    protected suspend fun <T> callApiAndDecode(
        action: String,
        params: Map<String, JsonElement>? = null,
        deserializer: DeserializationStrategy<T>
    ): T {
        val response = callApi(action, params)
        val status = response["status"]?.jsonPrimitive?.content
        val retcode = response["retcode"]?.jsonPrimitive?.int
        val message = response["message"]?.jsonPrimitive?.content

        if (status != "ok" || retcode != 0) {
            throw NapCatApiException(retcode ?: -1, message ?: "Unknown error")
        }

        val data = response["data"]
        if (data == null || data is JsonNull) {
            throw NapCatApiException(-1, "Response data is null")
        }

        return json.decodeFromJsonElement(deserializer, data)
    }

    // ==================== NapCatApi 实现 ====================

    override suspend fun getLoginInfo(): LoginInfo =
        callApiAndDecode("get_login_info", deserializer = LoginInfo.serializer())

    override suspend fun getStatus(): BotStatus =
        callApiAndDecode("get_status", deserializer = BotStatus.serializer())

    override suspend fun getVersionInfo(): VersionInfo =
        callApiAndDecode("get_version_info", deserializer = VersionInfo.serializer())

    override suspend fun sendPrivateMessage(userId: Long, message: MessageChain): MessageResult {
        val params = buildMap {
            put("user_id", JsonPrimitive(userId))
            put("message", json.encodeToJsonElement(message))
        }
        return callApiAndDecode("send_private_msg", params, MessageResult.serializer())
    }

    override suspend fun sendGroupMessage(groupId: Long, message: MessageChain): MessageResult {
        val params = buildMap {
            put("group_id", JsonPrimitive(groupId))
            put("message", json.encodeToJsonElement(message))
        }
        return callApiAndDecode("send_group_msg", params, MessageResult.serializer())
    }

    override suspend fun deleteMessage(messageId: Int) {
        val params = buildMap {
            put("message_id", json.encodeToJsonElement(messageId))
        }
        callApiAndDecode("delete_msg", params, JsonObject.serializer())
    }

    override suspend fun getMessage(messageId: Int): MessageDetail {
        val params = buildMap {
            put("message_id", json.encodeToJsonElement(messageId))
        }
        return callApiAndDecode("get_msg", params, MessageDetail.serializer())
    }

    override suspend fun getFriendList(): List<FriendInfo> =
        callApiAndDecode("get_friend_list", deserializer = serializer<List<FriendInfo>>())

    override suspend fun getGroupList(noCache: Boolean): List<GroupInfo> {
        val params = buildMap {
            put("no_cache", json.encodeToJsonElement(noCache))
        }
        return callApiAndDecode("get_group_list", params, serializer<List<GroupInfo>>())
    }

    override suspend fun getGroupMemberList(groupId: Long): List<GroupMemberInfo> {
        val params = buildMap {
            put("group_id", JsonPrimitive(groupId))
        }
        return callApiAndDecode("get_group_member_list", params, serializer<List<GroupMemberInfo>>())
    }

    override suspend fun getGroupMemberInfo(groupId: Long, userId: Long): GroupMemberInfo {
        val params = buildMap {
            put("group_id", JsonPrimitive(groupId))
            put("user_id", JsonPrimitive(userId))
        }
        return callApiAndDecode("get_group_member_info", params, GroupMemberInfo.serializer())
    }

    override suspend fun setGroupKick(groupId: Long, userId: Long, rejectAddRequest: Boolean) {
        val params = buildMap {
            put("group_id", JsonPrimitive(groupId))
            put("user_id", JsonPrimitive(userId))
            put("reject_add_request", json.encodeToJsonElement(rejectAddRequest))
        }
        callApiAndDecode("set_group_kick", params, JsonObject.serializer())
    }

    override suspend fun setGroupBan(groupId: Long, userId: Long, duration: Int) {
        val params = buildMap {
            put("group_id", JsonPrimitive(groupId))
            put("user_id", JsonPrimitive(userId))
            put("duration", json.encodeToJsonElement(duration))
        }
        callApiAndDecode("set_group_ban", params, JsonObject.serializer())
    }

    override suspend fun setGroupCard(groupId: Long, userId: Long, card: String) {
        val params = buildMap {
            put("group_id", JsonPrimitive(groupId))
            put("user_id", JsonPrimitive(userId))
            put("card", json.encodeToJsonElement(card))
        }
        callApiAndDecode("set_group_card", params, JsonObject.serializer())
    }

    override suspend fun setGroupName(groupId: Long, groupName: String) {
        val params = buildMap {
            put("group_id", JsonPrimitive(groupId))
            put("group_name", json.encodeToJsonElement(groupName))
        }
        callApiAndDecode("set_group_name", params, JsonObject.serializer())
    }

    override suspend fun setGroupAdmin(groupId: Long, userId: Long, enable: Boolean) {
        val params = buildMap {
            put("group_id", JsonPrimitive(groupId))
            put("user_id", JsonPrimitive(userId))
            put("enable", json.encodeToJsonElement(enable))
        }
        callApiAndDecode("set_group_admin", params, JsonObject.serializer())
    }

    override suspend fun getGroupMsgHistory(groupId: Long, messageSeq: Long?, count: Int): List<GroupMessageEvent> {
        val params = buildMap {
            put("group_id", JsonPrimitive(groupId))
            if (messageSeq != null) {
                put("message_seq", json.encodeToJsonElement(messageSeq))
            }
            put("count", json.encodeToJsonElement(count))
        }
        val response = callApi("get_group_msg_history", params)
        val status = response["status"]?.jsonPrimitive?.content
        if (status != "ok") {
            val retcode = response["retcode"]?.jsonPrimitive?.int ?: -1
            val msg = response["message"]?.jsonPrimitive?.content ?: "Unknown error"
            throw NapCatApiException(retcode, msg)
        }
        val data = response["data"]?.jsonObject ?: return emptyList()
        val messages = data["messages"]?.jsonArray ?: return emptyList()
        return messages.mapNotNull { element ->
            try {
                val obj = element.jsonObject
                if (obj["message_type"]?.jsonPrimitive?.content == "group") {
                    json.decodeFromJsonElement(GroupMessageEvent.serializer(), obj)
                } else {
                    null
                }
            } catch (e: Exception) {
            trace.exception(e)
                logger.debug("Failed to parse group message", e)
                null
            }
        }
    }

    override suspend fun getPrivateMsgHistory(userId: Long, messageSeq: Long?, count: Int): List<PrivateMessageEvent> {
        val params = buildMap {
            put("user_id", JsonPrimitive(userId))
            if (messageSeq != null) {
                put("message_seq", json.encodeToJsonElement(messageSeq))
            }
            put("count", json.encodeToJsonElement(count))
        }
        val response = callApi("get_friend_msg_history", params)
        val status = response["status"]?.jsonPrimitive?.content
        if (status != "ok") {
            val retcode = response["retcode"]?.jsonPrimitive?.int ?: -1
            val msg = response["message"]?.jsonPrimitive?.content ?: "Unknown error"
            throw NapCatApiException(retcode, msg)
        }
        val data = response["data"]?.jsonObject ?: return emptyList()
        val messages = data["messages"]?.jsonArray ?: return emptyList()
        return messages.mapNotNull { element ->
            try {
                val obj = element.jsonObject
                if (obj["message_type"]?.jsonPrimitive?.content == "private") {
                    json.decodeFromJsonElement(PrivateMessageEvent.serializer(), obj)
                } else {
                    null
                }
            } catch (e: Exception) {
            trace.exception(e)
                logger.debug("Failed to parse private message", e)
                null
            }
        }
    }

    override suspend fun getImage(file: String): FileInfo {
        val params = buildMap {
            put("file", json.encodeToJsonElement(file))
        }
        return callApiAndDecode("get_image", params, FileInfo.serializer())
    }

    override suspend fun getRecord(file: String, outFormat: String?): FileInfo {
        val params = buildMap {
            put("file", json.encodeToJsonElement(file))
            if (outFormat != null) put("out_format", json.encodeToJsonElement(outFormat))
        }
        return callApiAndDecode("get_record", params, FileInfo.serializer())
    }

    override suspend fun getFile(file: String): FileInfo {
        val params = buildMap {
            put("file", json.encodeToJsonElement(file))
        }
        return callApiAndDecode("get_file", params, FileInfo.serializer())
    }

    override suspend fun getForwardMsg(id: String): List<ForwardMessage> {
        val params = buildMap {
            put("id", json.encodeToJsonElement(id))
        }
        val response = callApi("get_forward_msg", params)
        val data = response["data"]?.jsonObject ?: return emptyList()
        val messages = data["messages"]?.jsonArray ?: return emptyList()
        return messages.mapNotNull { element ->
            try {
                json.decodeFromJsonElement(ForwardMessage.serializer(), element)
            } catch (e: Exception) {
            trace.exception(e)
                logger.debug("Failed to parse forward message", e)
                null
            }
        }
    }
}
