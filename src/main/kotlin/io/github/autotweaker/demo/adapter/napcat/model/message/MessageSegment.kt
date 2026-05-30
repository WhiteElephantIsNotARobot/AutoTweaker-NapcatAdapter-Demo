package io.github.autotweaker.demo.adapter.napcat.model.message

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * 消息链类型，由多个 [MessageSegment] 组成
 */
typealias MessageChain = List<MessageSegment>

/**
 * OneBot 11 消息段
 *
 * 使用密封接口定义类型安全的消息段，匹配 NapCat 的 `{"type":"xxx","data":{...}}` 格式。
 */
@Serializable(with = MessageSegmentSerializer::class)
sealed interface MessageSegment {
    /** 文本消息段 */
    @Serializable
    data class Text(val text: String) : MessageSegment

    /** @消息段 */
    @Serializable
    data class At(val qq: String? = null) : MessageSegment

    /** 图片消息段 */
    @Serializable
    data class Image(
        val file: String,
        val url: String? = null,
        @Serializable(with = FlexibleIntSerializer::class) @JvmField val subType: Int? = null
    ) : MessageSegment

    /** 表情消息段 */
    @Serializable
    data class Face(@Serializable(with = FlexibleIntSerializer::class) val id: Int) : MessageSegment

    /** 回复消息段 */
    @Serializable
    data class Reply(@Serializable(with = FlexibleIntSerializer::class) val id: Int) : MessageSegment

    /** JSON 消息段（卡片消息） */
    @Serializable
    data class JsonMsg(val data: String) : MessageSegment

    /** 语音消息段 */
    @Serializable
    data class Record(val file: String) : MessageSegment

    /** 视频消息段 */
    @Serializable
    data class Video(val file: String) : MessageSegment

    /** 合并转发消息节点 */
    @Serializable
    data class Node(
        @Serializable(with = FlexibleStringSerializer::class) @JvmField val userId: String,
        val nickname: String
    ) : MessageSegment

    /** 戳一戳消息段 */
    @Serializable
    data class Poke(val type: String, val id: String) : MessageSegment

    /** 合并转发消息段 */
    @Serializable
    data class Forward(val id: String) : MessageSegment

    /** 未知类型消息段 */
    data class Unknown(val type: String, val data: JsonObject? = null) : MessageSegment
}

/**
 * MessageSegment 自定义序列化器
 *
 * 处理 NapCat 的嵌套格式 `{"type":"xxx","data":{...}}`
 */
object MessageSegmentSerializer : KSerializer<MessageSegment> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): MessageSegment {
        val json = decoder.decodeSerializableValue(JsonObject.serializer())
        val type = json["type"]?.jsonPrimitive?.content ?: return MessageSegment.Unknown("unknown")
        val data = json["data"]?.jsonObject ?: JsonObject(emptyMap())

        return when (type) {
            "text" -> MessageSegment.Text(data["text"]?.jsonPrimitive?.content ?: "")
            "at" -> MessageSegment.At(data["qq"]?.jsonPrimitive?.contentOrNull)
            "image" -> MessageSegment.Image(
                file = data["file"]?.jsonPrimitive?.content ?: "",
                url = data["url"]?.jsonPrimitive?.contentOrNull,
                subType = data["sub_type"]?.jsonPrimitive?.intOrNull
            )
            "face" -> MessageSegment.Face(data["id"]?.jsonPrimitive?.int ?: 0)
            "reply" -> MessageSegment.Reply(data["id"]?.jsonPrimitive?.int ?: 0)
            "json" -> MessageSegment.JsonMsg(data["data"]?.jsonPrimitive?.content ?: "")
            "record" -> MessageSegment.Record(data["file"]?.jsonPrimitive?.content ?: "")
            "video" -> MessageSegment.Video(data["file"]?.jsonPrimitive?.content ?: "")
            "node" -> MessageSegment.Node(
                userId = data["user_id"]?.jsonPrimitive?.content ?: "",
                nickname = data["nickname"]?.jsonPrimitive?.content ?: ""
            )
            "poke" -> MessageSegment.Poke(
                type = data["type"]?.jsonPrimitive?.content ?: "",
                id = data["id"]?.jsonPrimitive?.content ?: ""
            )
            "forward" -> MessageSegment.Forward(
                id = data["id"]?.jsonPrimitive?.content ?: ""
            )
            else -> MessageSegment.Unknown(type, data)
        }
    }

    override fun serialize(encoder: Encoder, value: MessageSegment) {
        val json = when (value) {
            is MessageSegment.Text -> buildJsonObject {
                put("type", "text")
                putJsonObject("data") { put("text", value.text) }
            }
            is MessageSegment.At -> buildJsonObject {
                put("type", "at")
                putJsonObject("data") { put("qq", value.qq ?: "all") }
            }
            is MessageSegment.Image -> buildJsonObject {
                put("type", "image")
                putJsonObject("data") {
                    put("file", value.file)
                    value.url?.let { put("url", it) }
                    value.subType?.let { put("sub_type", it) }
                }
            }
            is MessageSegment.Face -> buildJsonObject {
                put("type", "face")
                putJsonObject("data") { put("id", value.id) }
            }
            is MessageSegment.Reply -> buildJsonObject {
                put("type", "reply")
                putJsonObject("data") { put("id", value.id) }
            }
            is MessageSegment.JsonMsg -> buildJsonObject {
                put("type", "json")
                putJsonObject("data") { put("data", value.data) }
            }
            is MessageSegment.Record -> buildJsonObject {
                put("type", "record")
                putJsonObject("data") { put("file", value.file) }
            }
            is MessageSegment.Video -> buildJsonObject {
                put("type", "video")
                putJsonObject("data") { put("file", value.file) }
            }
            is MessageSegment.Node -> buildJsonObject {
                put("type", "node")
                putJsonObject("data") {
                    put("user_id", value.userId)
                    put("nickname", value.nickname)
                }
            }
            is MessageSegment.Poke -> buildJsonObject {
                put("type", "poke")
                putJsonObject("data") {
                    put("type", value.type)
                    put("id", value.id)
                }
            }
            is MessageSegment.Forward -> buildJsonObject {
                put("type", "forward")
                putJsonObject("data") {
                    put("id", value.id)
                }
            }
            is MessageSegment.Unknown -> buildJsonObject {
                put("type", value.type)
                value.data?.let { put("data", it) }
            }
        }
        encoder.encodeSerializableValue(JsonObject.serializer(), json)
    }
}

/**
 * 灵活的 Int 序列化器
 *
 * 处理 NapCat 可能返回字符串或数字的情况
 */
object FlexibleIntSerializer : KSerializer<Int> {
    override val descriptor = JsonPrimitive.serializer().descriptor

    override fun deserialize(decoder: Decoder): Int {
        val element = decoder.decodeSerializableValue(JsonPrimitive.serializer())
        return element.intOrNull ?: element.content.toIntOrNull()
            ?: throw SerializationException("Cannot parse Int from: ${element.content}")
    }

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }
}

/**
 * 灵活的 String 序列化器
 *
 * 处理 NapCat 可能返回数字或字符串的情况
 */
object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor = JsonPrimitive.serializer().descriptor

    override fun deserialize(decoder: Decoder): String {
        val element = decoder.decodeSerializableValue(JsonPrimitive.serializer())
        return element.content
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}
