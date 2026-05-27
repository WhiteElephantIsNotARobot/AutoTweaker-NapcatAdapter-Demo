package io.github.autotweaker.demo.adapter.napcat.model.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

typealias MessageChain = List<MessageSegment>

@Serializable
sealed class MessageSegment {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : MessageSegment()

    @Serializable
    @SerialName("at")
    data class At(val qq: String) : MessageSegment()

    @Serializable
    @SerialName("image")
    data class Image(
        val file: String,
        val url: String? = null,
        @SerialName("sub_type") val subType: Int? = null
    ) : MessageSegment()

    @Serializable
    @SerialName("face")
    data class Face(val id: Int) : MessageSegment()

    @Serializable
    @SerialName("reply")
    data class Reply(val id: Int) : MessageSegment()

    @Serializable
    @SerialName("json")
    data class Json(val data: String) : MessageSegment()

    @Serializable
    @SerialName("record")
    data class Record(val file: String) : MessageSegment()

    @Serializable
    @SerialName("video")
    data class Video(val file: String) : MessageSegment()

    @Serializable
    @SerialName("node")
    data class Node(
        @SerialName("user_id") val userId: String,
        val nickname: String,
        val content: JsonElement? = null
    ) : MessageSegment()

    @Serializable
    @SerialName("poke")
    data class Poke(val type: String, val id: String) : MessageSegment()

    @Serializable
    data class Unknown(
        val type: String,
        val data: JsonElement? = null
    ) : MessageSegment()
}
