package io.github.autotweaker.demo.adapter.napcat.tool

import io.github.autotweaker.api.TMP_HOST_PATH
import io.github.autotweaker.api.TMP_PATH
import io.github.autotweaker.api.adapter.PathResolver
import io.github.autotweaker.api.types.serializer.PathSerializer
import io.github.autotweaker.demo.adapter.napcat.tool.qq.QqArgs
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.name

/**
 * qq 工具的执行请求：由 [QqArgs] 在 resolve 阶段解析而来，所有字段执行就绪。
 *
 * - 本地文件参数已在解析时经 PathResolver 处理为宿主可读路径（URL/base64 资源原样保留）
 * - 可选参数已填默认值
 * - 下载的目标路径与展示路径已确定
 *
 * execute 只消费本模型调用 NapCat API，不再做路径解析与默认值处理。
 */
@Serializable
sealed interface QqRequest {

	@Serializable data class GetGroupList(val noCache: Boolean) : QqRequest
	@Serializable data class GetGroupInfo(val groupId: Long) : QqRequest
	@Serializable data class GetGroupMemberInfo(val groupId: Long, val userId: Long) : QqRequest
	@Serializable data class GetGroupMemberList(val groupId: Long) : QqRequest
	@Serializable data class GetGroupMsgHistory(val groupId: Long, val count: Int) : QqRequest
	@Serializable data class GetPrivateMsgHistory(val userId: Long, val count: Int) : QqRequest
	@Serializable data class GetMsg(val messageId: String) : QqRequest
	@Serializable data class SendPrivateMsg(val userId: Long, val message: String) : QqRequest
	@Serializable data class SendGroupMsg(val groupId: Long, val message: String) : QqRequest
	@Serializable data class DeleteMsg(val messageId: String) : QqRequest
	@Serializable data class SetGroupKick(val groupId: Long, val userId: Long, val rejectAddRequest: Boolean) : QqRequest
	@Serializable data class SetGroupBan(val groupId: Long, val userId: Long, val duration: Long) : QqRequest
	@Serializable data class SetGroupWholeBan(val groupId: Long, val enable: Boolean) : QqRequest
	@Serializable data class SetGroupAdmin(val groupId: Long, val userId: Long, val enable: Boolean) : QqRequest
	@Serializable data class SetGroupCard(val groupId: Long, val userId: Long, val card: String) : QqRequest
	@Serializable data class SetGroupSpecialTitle(val groupId: Long, val userId: Long, val specialTitle: String) : QqRequest
	@Serializable data class SetGroupName(val groupId: Long, val groupName: String) : QqRequest
	@Serializable data class SetGroupLeave(val groupId: Long) : QqRequest
	@Serializable data class GroupPoke(val groupId: Long, val userId: Long) : QqRequest
	@Serializable data class SendLike(val userId: Long, val times: Int) : QqRequest

	/** OCR 图片已解析为 NapCat 可读的宿主路径或原样 URL。 */
	@Serializable data class OcrImage(val image: String) : QqRequest
	@Serializable data class GetForwardMsg(val messageId: String) : QqRequest
	@Serializable data class GetEssenceMsgList(val groupId: Long) : QqRequest
	@Serializable data class SetEssenceMsg(val messageId: String) : QqRequest
	@Serializable data class DeleteEssenceMsg(val messageId: String) : QqRequest
	@Serializable data class SendGroupNotice(val groupId: Long, val content: String) : QqRequest
	@Serializable data class GetGroupRootFiles(val groupId: Long) : QqRequest
	@Serializable data class GetGroupFilesByFolder(val groupId: Long, val folderId: String) : QqRequest
	@Serializable data class GetGroupFileUrl(val groupId: Long, val fileId: String, val busid: Long) : QqRequest

	/** file 为解析后的宿主路径或原样 URL/base64；name/folder 已定。 */
	@Serializable data class UploadGroupFile(
		val groupId: Long,
		val file: String,
		val name: String,
		val folder: String?,
	) : QqRequest

	@Serializable data class UploadPrivateFile(val userId: Long, val file: String, val name: String) : QqRequest

	/**
	 * 下载参数全部就绪：target 为宿主落盘路径，displayPath 为展示给用户的路径，timeoutSeconds 已填默认。
	 */
	@Serializable data class DownloadFile(
		val url: String,
		val fileName: String,
		@Serializable(with = PathSerializer::class) val target: Path,
		@Serializable(with = PathSerializer::class) val displayPath: Path,
		val timeoutSeconds: Int,
	) : QqRequest
}

/** LLM schema 解析为执行请求。路径类参数在此完成规范化与默认值。 */
fun QqArgs.toRequest(cwd: Path, resolver: PathResolver): QqRequest = when (this) {
	is QqArgs.GetGroupList -> QqRequest.GetGroupList(noCache ?: false)
	is QqArgs.GetGroupInfo -> QqRequest.GetGroupInfo(groupId)
	is QqArgs.GetGroupMemberInfo -> QqRequest.GetGroupMemberInfo(groupId, userId)
	is QqArgs.GetGroupMemberList -> QqRequest.GetGroupMemberList(groupId)
	is QqArgs.GetGroupMsgHistory -> QqRequest.GetGroupMsgHistory(groupId, count ?: 20)
	is QqArgs.GetPrivateMsgHistory -> QqRequest.GetPrivateMsgHistory(userId, count ?: 20)
	is QqArgs.GetMsg -> QqRequest.GetMsg(messageId)
	is QqArgs.SendPrivateMsg -> QqRequest.SendPrivateMsg(userId, message)
	is QqArgs.SendGroupMsg -> QqRequest.SendGroupMsg(groupId, message)
	is QqArgs.DeleteMsg -> QqRequest.DeleteMsg(messageId)
	is QqArgs.SetGroupKick -> QqRequest.SetGroupKick(groupId, userId, rejectAddRequest ?: false)
	is QqArgs.SetGroupBan -> QqRequest.SetGroupBan(groupId, userId, duration)
	is QqArgs.SetGroupWholeBan -> QqRequest.SetGroupWholeBan(groupId, enable)
	is QqArgs.SetGroupAdmin -> QqRequest.SetGroupAdmin(groupId, userId, enable)
	is QqArgs.SetGroupCard -> QqRequest.SetGroupCard(groupId, userId, card)
	is QqArgs.SetGroupSpecialTitle -> QqRequest.SetGroupSpecialTitle(groupId, userId, specialTitle)
	is QqArgs.SetGroupName -> QqRequest.SetGroupName(groupId, groupName)
	is QqArgs.SetGroupLeave -> QqRequest.SetGroupLeave(groupId)
	is QqArgs.GroupPoke -> QqRequest.GroupPoke(groupId, userId)
	is QqArgs.SendLike -> QqRequest.SendLike(userId, times ?: 1)
	is QqArgs.OcrImage -> QqRequest.OcrImage(resolveLocal(cwd, resolver, image))
	is QqArgs.GetForwardMsg -> QqRequest.GetForwardMsg(messageId)
	is QqArgs.GetEssenceMsgList -> QqRequest.GetEssenceMsgList(groupId)
	is QqArgs.SetEssenceMsg -> QqRequest.SetEssenceMsg(messageId)
	is QqArgs.DeleteEssenceMsg -> QqRequest.DeleteEssenceMsg(messageId)
	is QqArgs.SendGroupNotice -> QqRequest.SendGroupNotice(groupId, content)
	is QqArgs.GetGroupRootFiles -> QqRequest.GetGroupRootFiles(groupId)
	is QqArgs.GetGroupFilesByFolder -> QqRequest.GetGroupFilesByFolder(groupId, folderId)
	is QqArgs.GetGroupFileUrl -> QqRequest.GetGroupFileUrl(groupId, fileId, busid)
	is QqArgs.UploadGroupFile -> QqRequest.UploadGroupFile(
		groupId = groupId,
		file = resolveLocal(cwd, resolver, file),
		name = name ?: localFileName(file),
		folder = folder,
	)
	is QqArgs.UploadPrivateFile -> QqRequest.UploadPrivateFile(
		userId = userId,
		file = resolveLocal(cwd, resolver, file),
		name = name ?: localFileName(file),
	)
	is QqArgs.DownloadFile -> {
		val fileName = sanitizeName(fileName ?: deriveName(url))
		val inContainer = resolver.inContainer(cwd)
		val target = (if (inContainer) TMP_HOST_PATH else TMP_PATH).resolve(fileName)
		val displayPath = if (inContainer) resolver.toContainerPath(target) else target
		QqRequest.DownloadFile(
			url = url,
			fileName = fileName,
			target = target,
			displayPath = displayPath,
			timeoutSeconds = timeoutSeconds ?: 60,
		)
	}
}

private fun resolveLocal(cwd: Path, resolver: PathResolver, raw: String): String {
	if (raw.startsWith("http://") || raw.startsWith("https://") ||
		raw.startsWith("base64://") || raw.startsWith("file://")
	) return raw
	val absolute = resolver.toAbsolutePath(cwd, Path.of(raw))
	val host = if (resolver.inContainer(cwd)) resolver.toHostPath(absolute) else absolute
	return host.toString()
}

private fun localFileName(file: String): String {
	val candidate = if (file.startsWith("file://")) file.removePrefix("file://") else file
	return Path.of(candidate).name
}

private fun deriveName(url: String): String =
	url.substringAfterLast('/').substringBefore('?').ifBlank { "download" }

private fun sanitizeName(name: String): String =
	name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "download" }
