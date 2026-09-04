package io.github.autotweaker.demo.adapter.napcat.api

/**
 * NapCat API 调用失败时抛出。
 *
 * OneBot 响应的 message 字段既可能是 string 也可能是 array（错误消息段），因此统一以字符串承载。
 */
class NapCatApiException(
	val retcode: Long,
	val status: String? = null,
	override val message: String,
) : Exception(message)
