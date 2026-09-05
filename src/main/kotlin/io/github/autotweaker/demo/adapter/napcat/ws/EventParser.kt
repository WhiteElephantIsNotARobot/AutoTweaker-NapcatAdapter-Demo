package io.github.autotweaker.demo.adapter.napcat.ws

import io.github.autotweaker.api.*
import io.github.autotweaker.api.base.catching
import io.github.autotweaker.demo.adapter.napcat.model.event.Event
import io.github.autotweaker.demo.adapter.napcat.model.event.FriendAddNoticeEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.FriendRecallNoticeEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.FriendRequestEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.GroupAdminNoticeEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.GroupBanNoticeEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.GroupCardNoticeEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.GroupDecreaseNoticeEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.GroupIncreaseNoticeEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.GroupMessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.GroupRecallNoticeEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.GroupRequestEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.GroupUploadNoticeEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.HeartbeatMetaEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.LifecycleMetaEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.MessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.NotifyNoticeEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.PrivateMessageEvent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 根据 `post_type` / 子类型把 OneBot JSON 事件解析为类型化 [Event]。
 *
 * 未知或无法解析的事件返回 null，由调用方决定如何记录。
 */
object EventParser : Traceable {

	fun parse(json: Json, obj: JsonObject): Event? {
		val postType = obj["post_type"]?.jsonPrimitive?.contentOrNull ?: return null
		return when (postType) {
			"message" -> parseMessage(json, obj)
			"notice" -> parseNotice(json, obj)
			"request" -> parseRequest(json, obj)
			"meta_event" -> parseMeta(json, obj)
			else -> null
		}
	}

	private fun parseMessage(json: Json, obj: JsonObject): MessageEvent? = when (val type = obj["message_type"]?.jsonPrimitive?.contentOrNull) {
		"private" -> decode(json, obj, PrivateMessageEvent.serializer())
		"group" -> decode(json, obj, GroupMessageEvent.serializer())
		else -> null
	}

	private fun parseNotice(json: Json, obj: JsonObject): Event? {
		val type = obj["notice_type"]?.jsonPrimitive?.contentOrNull ?: return null
		return when (type) {
			"group_increase" -> decode(json, obj, GroupIncreaseNoticeEvent.serializer())
			"group_decrease" -> decode(json, obj, GroupDecreaseNoticeEvent.serializer())
			"group_ban" -> decode(json, obj, GroupBanNoticeEvent.serializer())
			"group_recall" -> decode(json, obj, GroupRecallNoticeEvent.serializer())
			"group_admin" -> decode(json, obj, GroupAdminNoticeEvent.serializer())
			"group_upload" -> decode(json, obj, GroupUploadNoticeEvent.serializer())
			"group_card" -> decode(json, obj, GroupCardNoticeEvent.serializer())
			"friend_add" -> decode(json, obj, FriendAddNoticeEvent.serializer())
			"friend_recall" -> decode(json, obj, FriendRecallNoticeEvent.serializer())
			"notify" -> decode(json, obj, NotifyNoticeEvent.serializer())
			else -> null
		}
	}

	private fun parseRequest(json: Json, obj: JsonObject): Event? = when (val type = obj["request_type"]?.jsonPrimitive?.contentOrNull) {
		"friend" -> decode(json, obj, FriendRequestEvent.serializer())
		"group" -> decode(json, obj, GroupRequestEvent.serializer())
		else -> null
	}

	private fun parseMeta(json: Json, obj: JsonObject): Event? = when (val type = obj["meta_event_type"]?.jsonPrimitive?.contentOrNull) {
		"heartbeat" -> decode(json, obj, HeartbeatMetaEvent.serializer())
		"lifecycle" -> decode(json, obj, LifecycleMetaEvent.serializer())
		else -> null
	}

	private fun <T> decode(json: Json, obj: JsonObject, serializer: KSerializer<T>): T? =
		trace.catching { json.decodeFromJsonElement(serializer, obj) }.getOrNull()
}
