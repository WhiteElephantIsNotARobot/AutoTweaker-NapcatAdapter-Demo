package io.github.autotweaker.demo.adapter.napcat.model.message

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** 一条完整消息由若干 [Segment] 组成。 */
typealias MessageChain = List<Segment>

/** [MessageChain] 的显式序列化器（供 api 层按需编码参数）。 */
val MessageChainSerializer: KSerializer<MessageChain> = ListSerializer(Segment.serializer())

/**
 * OneBot 11 消息段，序列化为 `{"type":"xxx","data":{...}}`。
 *
 * 字段同时覆盖发送与接收两种形态：发送侧 [Segment.Node]、[Segment.Music] 等仅发送段保留可空字段；
 * 接收侧 NapCat 追加的字段（[Segment.Image.fileSize]、[Segment.Face.raw] 等）在解码时填充。
 */
@Serializable(with = SegmentSerializer::class)
sealed interface Segment {

	/** 文本。 */
	data class Text(val text: String) : Segment

	/** @提及，qq 为 "all" 时表示 @全体成员。 */
	data class At(val qq: String) : Segment

	/** QQ 表情，解码时 NapCat 会附带 raw/resultId/chainCount。 */
	data class Face(
		val id: String? = null,
		val raw: JsonElement? = null,
		val resultId: String? = null,
		val chainCount: Long? = null,
	) : Segment

	/** 商城表情，接收时通常会被 NapCat 转换为 [Image]。 */
	data class Mface(
		val emojiId: String,
		val emojiPackageId: String,
		val key: String? = null,
		val summary: String? = null,
	) : Segment

	/** 骰子，发送时无需参数，接收时 [result] 为 1-6。 */
	data class Dice(val result: Int? = null) : Segment

	/** 石头剪刀布，发送时无需参数，接收时 [result] 1=石头 2=剪刀 3=布。 */
	data class Rps(val result: Int? = null) : Segment

	/** 戳一戳。 */
	data class Poke(val type: String, val id: String) : Segment

	/** 图片。 */
	data class Image(
		val file: String? = null,
		val url: String? = null,
		val summary: String? = null,
		val subType: Int? = null,
		val fileSize: Long? = null,
		val key: String? = null,
		val emojiId: String? = null,
		val emojiPackageId: String? = null,
	) : Segment

	/** 语音。 */
	data class Record(
		val file: String? = null,
		val fileSize: Long? = null,
		val path: String? = null,
	) : Segment

	/** 视频。 */
	data class Video(
		val file: String? = null,
		val url: String? = null,
		val fileSize: Long? = null,
		val thumb: String? = null,
	) : Segment

	/** 文件，发送时 [file] 为资源、[name] 为文件名；接收时 [file] 为文件名、[fileId] 为 id。 */
	data class FileMsg(
		val file: String? = null,
		val fileId: String? = null,
		val fileSize: Long? = null,
		val name: String? = null,
	) : Segment

	/** JSON 卡片消息，data 字段可能是字符串或对象。 */
	data class JsonMsg(val data: String) : Segment

	/** 音乐分享，仅支持发送。 */
	data class Music(
		val type: String,
		val id: String? = null,
		val url: String? = null,
		val image: String? = null,
		val singer: String? = null,
		val title: String? = null,
		val content: String? = null,
	) : Segment

	/** 合并转发消息的引用，解码时若 NapCat 解析了内容会附带 [content]。 */
	data class Forward(val id: String, val content: MessageChain? = null) : Segment

	/** 转发节点，仅用于构造合并转发（send_*_forward_msg 的 messages）。 */
	data class Node(
		val userId: String,
		val nickname: String,
		val content: MessageChain,
	) : Segment

	/** 推荐好友/群。 */
	data class Contact(val type: String, val id: String) : Segment

	/** 位置。 */
	data class Location(
		val lat: Double,
		val lon: Double,
		val title: String? = null,
		val content: String? = null,
	) : Segment

	/** 链接分享。 */
	data class Share(
		val url: String,
		val title: String? = null,
		val content: String? = null,
		val image: String? = null,
	) : Segment

	/** XML 卡片消息。 */
	data class Xml(val data: String) : Segment

	/** 未识别的消息段，原样保留。 */
	data class Unknown(val type: String, val data: JsonObject? = null) : Segment
}

object SegmentSerializer : KSerializer<Segment> {
	override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Segment")

	override fun deserialize(decoder: Decoder): Segment {
		val obj = decoder.decodeSerializableValue(JsonObject.serializer())
		return obj.fromOneBot()
	}

	override fun serialize(encoder: Encoder, value: Segment) {
		encoder.encodeSerializableValue(JsonObject.serializer(), value.toOneBot())
	}
}

private fun JsonObject.fromOneBot(): Segment {
	val type = this["type"]?.jsonPrimitive?.contentOrNull ?: return Segment.Unknown("unknown", this)
	val d = this["data"]?.jsonObject ?: JsonObject(emptyMap())

	fun str(key: String) = d[key]?.jsonPrimitive?.contentOrNull
	fun int(key: String) = d[key]?.jsonPrimitive?.intOrNull
	fun long(key: String) = d[key]?.jsonPrimitive?.longOrNull
	fun double(key: String) = d[key]?.jsonPrimitive?.doubleOrNull

	return when (type) {
		"text" -> Segment.Text(str("text") ?: "")
		"at" -> Segment.At(str("qq") ?: "")
		"face" -> Segment.Face(
			id = str("id"),
			raw = d["raw"],
			resultId = str("resultId"),
			chainCount = long("chainCount"),
		)
		"mface" -> Segment.Mface(
			emojiId = str("emoji_id") ?: "",
			emojiPackageId = str("emoji_package_id") ?: "",
			key = str("key"),
			summary = str("summary"),
		)
		"dice" -> Segment.Dice(int("result"))
		"rps" -> Segment.Rps(int("result"))
		"poke" -> Segment.Poke(str("type") ?: "", str("id") ?: "")
		"image" -> Segment.Image(
			file = str("file"),
			url = str("url"),
			summary = str("summary"),
			subType = int("sub_type"),
			fileSize = long("file_size"),
			key = str("key"),
			emojiId = str("emoji_id"),
			emojiPackageId = str("emoji_package_id"),
		)
		"record" -> Segment.Record(file = str("file"), fileSize = long("file_size"), path = str("path"))
		"video" -> Segment.Video(
			file = str("file"),
			url = str("url"),
			fileSize = long("file_size"),
			thumb = str("thumb"),
		)
		"file" -> Segment.FileMsg(
			file = str("file"),
			fileId = str("file_id"),
			fileSize = long("file_size"),
			name = str("name"),
		)
		"json" -> Segment.JsonMsg(str("data") ?: d["data"].toString())
		"music" -> Segment.Music(
			type = str("type") ?: "",
			id = str("id"),
			url = str("url"),
			image = str("image"),
			singer = str("singer"),
			title = str("title"),
			content = str("content"),
		)
		"forward" -> Segment.Forward(
			id = str("id") ?: "",
			content = d["content"]?.jsonArrayOrNull(),
		)
		"node" -> Segment.Node(
			userId = str("user_id") ?: "",
			nickname = str("nickname") ?: "",
			content = d["content"]?.jsonArrayOrNull() ?: emptyList(),
		)
		"contact" -> Segment.Contact(str("type") ?: "", str("id") ?: "")
		"location" -> Segment.Location(
			lat = double("lat") ?: 0.0,
			lon = double("lon") ?: 0.0,
			title = str("title"),
			content = str("content"),
		)
		"share" -> Segment.Share(
			url = str("url") ?: "",
			title = str("title"),
			content = str("content"),
			image = str("image"),
		)
		"xml" -> Segment.Xml(str("data") ?: d["data"].toString())
		else -> Segment.Unknown(type, d)
	}
}

private fun JsonElement.jsonArrayOrNull(): List<Segment>? =
	(this as? JsonArray)?.mapNotNull { element ->
		element.jsonObjectOrNull()?.fromOneBot()
	}

private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

private fun Segment.toOneBot(): JsonObject = buildJsonObject {
	val (type, d) = toTypeData()
	put("type", type)
	put("data", d)
}

private fun Segment.toTypeData(): Pair<String, JsonObject> = when (this) {
	is Segment.Text -> "text" to buildJsonObject { put("text", text) }
	is Segment.At -> "at" to buildJsonObject { put("qq", qq) }
	is Segment.Face -> "face" to buildJsonObject {
		id?.let { put("id", it) }
		raw?.let { put("raw", it) }
		resultId?.let { put("resultId", it) }
		chainCount?.let { put("chainCount", it) }
	}
	is Segment.Mface -> "mface" to buildJsonObject {
		put("emoji_id", emojiId)
		put("emoji_package_id", emojiPackageId)
		key?.let { put("key", it) }
		summary?.let { put("summary", it) }
	}
	is Segment.Dice -> "dice" to buildJsonObject {
		result?.let { put("result", it) }
	}
	is Segment.Rps -> "rps" to buildJsonObject {
		result?.let { put("result", it) }
	}
	is Segment.Poke -> "poke" to buildJsonObject {
		put("type", type)
		put("id", id)
	}
	is Segment.Image -> "image" to buildJsonObject {
		file?.let { put("file", it) }
		url?.let { put("url", it) }
		summary?.let { put("summary", it) }
		subType?.let { put("sub_type", it) }
		fileSize?.let { put("file_size", it) }
		key?.let { put("key", it) }
		emojiId?.let { put("emoji_id", it) }
		emojiPackageId?.let { put("emoji_package_id", it) }
	}
	is Segment.Record -> "record" to buildJsonObject {
		file?.let { put("file", it) }
		fileSize?.let { put("file_size", it) }
		path?.let { put("path", it) }
	}
	is Segment.Video -> "video" to buildJsonObject {
		file?.let { put("file", it) }
		url?.let { put("url", it) }
		fileSize?.let { put("file_size", it) }
		thumb?.let { put("thumb", it) }
	}
	is Segment.FileMsg -> "file" to buildJsonObject {
		file?.let { put("file", it) }
		fileId?.let { put("file_id", it) }
		fileSize?.let { put("file_size", it) }
		name?.let { put("name", it) }
	}
	is Segment.JsonMsg -> "json" to buildJsonObject {
		put("data", data)
	}
	is Segment.Music -> "music" to buildJsonObject {
		put("type", type)
		id?.let { put("id", it) }
		url?.let { put("url", it) }
		image?.let { put("image", it) }
		singer?.let { put("singer", it) }
		title?.let { put("title", it) }
		content?.let { put("content", it) }
	}
	is Segment.Forward -> "forward" to buildJsonObject {
		put("id", id)
		content?.let { put("content", Json.encodeToJsonElement(MessageChainSerializer, it)) }
	}
	is Segment.Node -> "node" to buildJsonObject {
		put("user_id", userId)
		put("nickname", nickname)
		put("content", Json.encodeToJsonElement(MessageChainSerializer, content))
	}
	is Segment.Contact -> "contact" to buildJsonObject {
		put("type", type)
		put("id", id)
	}
	is Segment.Location -> "location" to buildJsonObject {
		put("lat", lat)
		put("lon", lon)
		title?.let { put("title", it) }
		content?.let { put("content", it) }
	}
	is Segment.Share -> "share" to buildJsonObject {
		put("url", url)
		title?.let { put("title", it) }
		content?.let { put("content", it) }
		image?.let { put("image", it) }
	}
	is Segment.Xml -> "xml" to buildJsonObject {
		put("data", data)
	}
	is Segment.Unknown -> type to (data ?: JsonObject(emptyMap()))
}
