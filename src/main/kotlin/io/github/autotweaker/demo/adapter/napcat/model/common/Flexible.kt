package io.github.autotweaker.demo.adapter.napcat.model.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * NapCat 的哈希 id（消息 id 等）可能是大整数，超出 JSON number 安全范围时 NapCat 会以 string 返回。
 * 用此序列化器把声明为 [String] 的 id 字段收容 number/string 两种形态。
 */
object FlexibleStringSerializer : KSerializer<String> {
	override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FlexibleString")

	override fun deserialize(decoder: Decoder): String {
		val element = decoder.decodeSerializableValue(JsonPrimitive.serializer())
		val content = element.content
		if (element.isString || content.isNotEmpty()) return content
		throw SerializationException("Not a valid string: $element")
	}

	override fun serialize(encoder: Encoder, value: String) {
		encoder.encodeString(value)
	}
}
