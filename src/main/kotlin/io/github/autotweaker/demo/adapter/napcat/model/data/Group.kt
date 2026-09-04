package io.github.autotweaker.demo.adapter.napcat.model.data

import io.github.autotweaker.demo.adapter.napcat.model.common.FlexibleStringSerializer
import io.github.autotweaker.demo.adapter.napcat.model.message.MessageChain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 群信息。 */
@Serializable
data class GroupInfo(
	@SerialName("group_id") val groupId: Long,
	@SerialName("group_name") val groupName: String,
	@SerialName("member_count") val memberCount: Int? = null,
	@SerialName("max_member_count") val maxMemberCount: Int? = null,
)

/** 群成员信息。 */
@Serializable
data class GroupMember(
	@SerialName("group_id") val groupId: Long? = null,
	@SerialName("user_id") val userId: Long,
	val nickname: String? = null,
	val card: String? = null,
	val role: String? = null,
	val title: String? = null,
	val sex: String? = null,
	val age: Int? = null,
	val area: String? = null,
	@SerialName("join_time") val joinTime: Long? = null,
	@SerialName("last_sent_time") val lastSentTime: Long? = null,
	val level: String? = null,
	val unfriendly: Boolean? = null,
	@SerialName("card_changeable") val cardChangeable: Boolean? = null,
	val shutUpTimestamp: Long? = null,
)

/** 精华消息。 */
@Serializable
data class EssenceMsg(
	@SerialName("sender_id") val senderId: Long,
	@SerialName("sender_nick") val senderNick: String? = null,
	@SerialName("sender_time") val senderTime: Long? = null,
	@SerialName("operator_id") val operatorId: Long? = null,
	@SerialName("operator_nick") val operatorNick: String? = null,
	@SerialName("operator_time") val operatorTime: Long? = null,
	@SerialName("message_id")
	@Serializable(with = FlexibleStringSerializer::class)
	val messageId: String,
	@SerialName("group_id") val groupId: Long? = null,
	val message: MessageChain? = null,
)

/** 群文件系统容量。 */
@Serializable
data class GroupFileSystemInfo(
	@SerialName("file_count") val fileCount: Long? = null,
	@SerialName("total_count") val totalCount: Long? = null,
	@SerialName("used_space") val usedSpace: Long? = null,
	@SerialName("max_space") val maxSpace: Long? = null,
)

/** 群文件。 */
@Serializable
data class GroupFileInfo(
	@SerialName("file_id") val fileId: String,
	val busid: Long,
	@SerialName("file_name") val fileName: String,
	@SerialName("file_size") val fileSize: Long,
	@SerialName("upload_time") val uploadTime: Long? = null,
	@SerialName("uploader") val uploader: Long? = null,
	val uploaderName: String? = null,
)

/** 群文件夹。 */
@Serializable
data class GroupFolderInfo(
	@SerialName("folder_id") val folderId: String,
	@SerialName("folder_name") val folderName: String,
	@SerialName("create_time") val createTime: Long? = null,
	val creator: Long? = null,
	val creatorName: String? = null,
)

/** 群根目录/子目录文件列表。 */
@Serializable
data class GroupFileList(
	val files: List<GroupFileInfo> = emptyList(),
	val folders: List<GroupFolderInfo> = emptyList(),
)
