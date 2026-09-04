package io.github.autotweaker.demo.adapter.napcat.model.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 资源文件信息（get_image/get_record/get_file）。
 *
 * NapCat 无数据库，资源存在 LRU 缓存中，各动作返回字段不稳定，故全部可空。
 */
@Serializable
data class FileResource(
	val file: String? = null,
	val url: String? = null,
	@SerialName("file_name") val fileName: String? = null,
	@SerialName("file_size") val fileSize: Long? = null,
	val path: String? = null,
	val base64: String? = null,
	@SerialName("sub_type") val subType: String? = null,
	@SerialName("file_id") val fileId: String? = null,
)
