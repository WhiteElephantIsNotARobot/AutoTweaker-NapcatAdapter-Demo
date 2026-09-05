package io.github.autotweaker.demo.adapter.napcat.tool

import com.google.auto.service.AutoService
import io.github.autotweaker.api.Loggable
import io.github.autotweaker.api.Traceable
import io.github.autotweaker.api.tool.Ready
import io.github.autotweaker.api.tool.Rejected
import io.github.autotweaker.api.tool.Tool
import io.github.autotweaker.api.tool.toolSuccess
import io.github.autotweaker.api.types.tool.ToolMeta
import io.github.autotweaker.api.types.tool.text
import io.github.autotweaker.api.unreachable
import io.github.autotweaker.demo.adapter.napcat.NapCatAdapter
import io.github.autotweaker.demo.adapter.napcat.api.NapCatApiException
import io.github.autotweaker.demo.adapter.napcat.model.data.EssenceMsg
import io.github.autotweaker.demo.adapter.napcat.model.data.GroupFileList
import io.github.autotweaker.demo.adapter.napcat.model.data.GroupInfo
import io.github.autotweaker.demo.adapter.napcat.model.data.GroupMember
import io.github.autotweaker.demo.adapter.napcat.model.event.GroupMessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.event.PrivateMessageEvent
import io.github.autotweaker.demo.adapter.napcat.model.message.MessageChain
import io.github.autotweaker.demo.adapter.napcat.model.message.Segment
import io.github.autotweaker.demo.adapter.napcat.tool.qq.QqArgs
import io.github.autotweaker.demo.adapter.napcat.tool.qq.meta.qqMeta
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlin.time.Duration.Companion.seconds

/**
 * qq 工具：让 agent 通过当前连接的 QQ 账号收发消息、管理群与文件。
 *
 * resolve 把 LLM 层 schema [QqArgs] 解析为执行就绪的 [QqRequest]（路径规范化、默认值、下载目标），
 * execute 只消费 [QqRequest] 调用 NapCat API，不再进行任何解析。
 */
@AutoService(Tool::class)
class QqTool : Tool<QqArgs>, Loggable, Traceable {

	override suspend fun meta(): Pair<ToolMeta, KSerializer<QqArgs>> =
		qqMeta(QQ_DESCRIPTIONS)

	override suspend fun resolve(args: QqArgs, cwd: Path): Tool.ResolveResult {
		val runtime = NapCatAdapter.runtime()
			?: unreachable("NapCatAdapter has not been initialized")

		val request = try {
			args.toRequest(cwd, runtime.core.pathResolver)
		} catch (e: Throwable) {
			return Rejected("参数解析失败：${e.message}") {
				// 路径参数解析异常只可能来自本地路径类函数，此时 request 尚未构建，用 args 还原动作标签。
				val action = when (args) {
					is QqArgs.UploadGroupFile -> "上传文件到群 ${args.groupId}"
					is QqArgs.UploadPrivateFile -> "上传文件给用户 ${args.userId}"
					is QqArgs.OcrImage -> "OCR 识别图片"
					// 其余分支转换无本地路径解析步骤，不应到达。
					else -> unreachable("unexpected parse failure: ${args::class.simpleName}")
				}
				text("$action 失败：参数解析失败（${e.message}）")
			}
		}
		val label = describe(request)

		if (!runtime.isRunning()) {
			return Rejected("QQ 服务未连接") { text("$label 失败：QQ 服务未连接") }
		}

		fun localFileMissing(file: String): Tool.ResolveResult? {
			if (file.startsWith("http://") || file.startsWith("https://") ||
				file.startsWith("base64://") || file.startsWith("file://")
			) return null
			return if (!Files.isRegularFile(Path.of(file))) {
				Rejected("找不到本地文件：$file") { text("$label 失败：找不到本地文件 $file") }
			} else null
		}

		when (request) {
			is QqRequest.UploadGroupFile -> localFileMissing(request.file)?.let { return it }
			is QqRequest.UploadPrivateFile -> localFileMissing(request.file)?.let { return it }
			is QqRequest.OcrImage -> localFileMissing(request.image)?.let { return it }
			is QqRequest.DownloadFile ->
				if (!request.url.startsWith("http://") && !request.url.startsWith("https://")) {
					return Rejected("该链接不是 http(s) 链接") { text("$label 失败：该链接不是 http(s) 链接") }
				}

			else -> Unit
		}

		return ready(request)
	}

	override suspend fun execute(
		request: JsonElement,
		cwd: Path,
		outputChannel: SendChannel<Tool.RuntimeOutput>,
	): Tool.ToolOutput {
		val req = Json.decodeFromJsonElement(QqRequest.serializer(), request)
		val api = NapCatAdapter.runtime()
			?.takeIf { it.isRunning() }
			?.client
			?: throw NapCatApiException(-1, null, "QQ 服务未连接")

		val resultText: String = when (req) {
			is QqRequest.GetGroupList -> api.getGroupList(req.noCache)
				.joinToString("\n") { "${it.groupId} ${it.groupName}（成员 ${it.memberCount ?: "?"}）" }
				.ifEmpty { "（没有已加入的群）" }

			is QqRequest.GetGroupInfo -> api.getGroupInfo(req.groupId).groupInfoText()

			is QqRequest.GetGroupMemberInfo -> api.getGroupMemberInfo(req.groupId, req.userId).memberText()

			is QqRequest.GetGroupMemberList -> api.getGroupMemberList(req.groupId).joinToString("\n") { it.memberText() }

			is QqRequest.GetGroupMsgHistory -> api.getGroupMsgHistory(req.groupId, count = req.count)
				.joinToString("\n") { messageLine(it) }

			is QqRequest.GetPrivateMsgHistory -> api.getFriendMsgHistory(req.userId, count = req.count)
				.joinToString("\n") { messageLine(it) }

			is QqRequest.GetMsg -> {
				val detail = api.getMessage(req.messageId)
				buildString {
					append(messageLine(detail.rawMessage, detail.sender.nickname ?: detail.userId?.toString() ?: "未知"))
					if (detail.message.isNotEmpty()) {
						append("\n结构化消息：").append(chainText(detail.message))
					}
				}
			}

			is QqRequest.SendPrivateMsg -> {
				val result = api.sendPrivateMessage(req.userId, listOf(Segment.Text(req.message)))
				"已发送，message_id=${result.messageId}"
			}

			is QqRequest.SendGroupMsg -> {
				val result = api.sendGroupMessage(req.groupId, listOf(Segment.Text(req.message)))
				"已发送，message_id=${result.messageId}"
			}

			is QqRequest.DeleteMsg -> {
				api.deleteMessage(req.messageId)
				"已撤回消息 ${req.messageId}"
			}

			is QqRequest.SetGroupKick -> {
				api.setGroupKick(req.groupId, req.userId, req.rejectAddRequest)
				"已将 ${req.userId} 移出群 ${req.groupId}"
			}

			is QqRequest.SetGroupBan -> {
				api.setGroupBan(req.groupId, req.userId, req.duration)
				if (req.duration == 0L) "已解除 ${req.userId} 的禁言" else "已禁言 ${req.userId} ${req.duration} 秒"
			}

			is QqRequest.SetGroupWholeBan -> {
				api.setGroupWholeBan(req.groupId, req.enable)
				if (req.enable) "群 ${req.groupId} 已全员禁言" else "群 ${req.groupId} 已解除全员禁言"
			}

			is QqRequest.SetGroupAdmin -> {
				api.setGroupAdmin(req.groupId, req.userId, req.enable)
				if (req.enable) "已将 ${req.userId} 设为群 ${req.groupId} 管理员" else "已取消 ${req.userId} 的管理员"
			}

			is QqRequest.SetGroupCard -> {
				api.setGroupCard(req.groupId, req.userId, req.card)
				"已设置 ${req.userId} 的群名片"
			}

			is QqRequest.SetGroupSpecialTitle -> {
				api.setGroupSpecialTitle(req.groupId, req.userId, req.specialTitle)
				"已设置 ${req.userId} 的专属头衔"
			}

			is QqRequest.SetGroupName -> {
				api.setGroupName(req.groupId, req.groupName)
				"群名已改为：${req.groupName}"
			}

			is QqRequest.SetGroupLeave -> {
				api.setGroupLeave(req.groupId)
				"已退出群 ${req.groupId}"
			}

			is QqRequest.GroupPoke -> {
				api.groupPoke(req.groupId, req.userId)
				"已戳了戳 ${req.userId}"
			}

			is QqRequest.SendLike -> {
				api.sendLike(req.userId, req.times)
				"已点赞"
			}

			is QqRequest.OcrImage -> {
				val image = api.ocrImage(req.image)
				image["texts"]?.let { "OCR 结果：$it" } ?: "OCR 完成：$image"
			}

			is QqRequest.GetForwardMsg -> api.getForwardMsg(req.messageId).toString()

			is QqRequest.GetEssenceMsgList -> api.getEssenceMsgList(req.groupId)
				.joinToString("\n") { essenceLine(it) }
				.ifEmpty { "（该群没有精华消息）" }

			is QqRequest.SetEssenceMsg -> {
				api.setEssenceMsg(req.messageId)
				"已将消息 ${req.messageId} 设为群精华"
			}

			is QqRequest.DeleteEssenceMsg -> {
				api.deleteEssenceMsg(req.messageId)
				"已取消消息 ${req.messageId} 的精华状态"
			}

			is QqRequest.SendGroupNotice -> {
				api.sendGroupNotice(req.groupId, req.content)
				"群公告已发布"
			}

			is QqRequest.GetGroupRootFiles -> api.getGroupRootFiles(req.groupId).fileListText()

			is QqRequest.GetGroupFilesByFolder -> api.getGroupFilesByFolder(req.groupId, req.folderId).fileListText()

			is QqRequest.GetGroupFileUrl -> api.getGroupFileUrl(req.groupId, req.fileId, req.busid).toString()

			is QqRequest.UploadGroupFile -> {
				api.uploadGroupFile(req.groupId, req.file, req.name, req.folder)
				"已上传文件 ${req.name} 到群 ${req.groupId}"
			}

			is QqRequest.UploadPrivateFile -> {
				api.uploadPrivateFile(req.userId, req.file, req.name)
				"已上传文件 ${req.name} 给用户 ${req.userId}"
			}

			is QqRequest.DownloadFile -> downloadFile(req)
		}

		return resultText.toolSuccess { text(doneLabel(describe(req))) }
	}

	// ==================== resolve 展示 ====================

	private fun ready(request: QqRequest): Tool.ResolveResult.Ready {
		val label = describe(request)
		val detail = requestDetail(request)
		return Ready(
			QqRequest.serializer(), request,
			request = { reason ->
				text("请求$label" + if (reason.isNotEmpty()) "（原因：$reason）" else "")
				detail?.let { text(it) }
			},
			executing = { text("正在$label") },
			cancelled = { text("$label 已取消") },
			rejected = { reason -> text("$label 被拒绝" + (reason?.let { "：$it" } ?: "")) },
			failed = { e -> text("$label 失败：${e.message}") },
			timeout = { elapsed -> text("$label 超时：$elapsed") },
		)
	}

	private fun describe(request: QqRequest): String = when (request) {
		is QqRequest.GetGroupList -> "获取群列表"
		is QqRequest.GetGroupInfo -> "获取群 ${request.groupId} 信息"
		is QqRequest.GetGroupMemberInfo -> "获取群 ${request.groupId} 成员 ${request.userId} 信息"
		is QqRequest.GetGroupMemberList -> "获取群 ${request.groupId} 成员列表"
		is QqRequest.GetGroupMsgHistory -> "读取群 ${request.groupId} 历史消息"
		is QqRequest.GetPrivateMsgHistory -> "读取用户 ${request.userId} 私聊历史"
		is QqRequest.GetMsg -> "获取消息 ${request.messageId}"
		is QqRequest.SendPrivateMsg -> "发送私聊消息给 ${request.userId}"
		is QqRequest.SendGroupMsg -> "发送群消息到群 ${request.groupId}"
		is QqRequest.DeleteMsg -> "撤回消息 ${request.messageId}"
		is QqRequest.SetGroupKick -> "将 ${request.userId} 移出群 ${request.groupId}"
		is QqRequest.SetGroupBan ->
			if (request.duration == 0L) "解除 ${request.userId} 禁言" else "禁言 ${request.userId} ${request.duration} 秒"

		is QqRequest.SetGroupWholeBan -> if (request.enable) "全员禁言群 ${request.groupId}" else "解除群 ${request.groupId} 全员禁言"
		is QqRequest.SetGroupAdmin -> if (request.enable) "设置 ${request.userId} 为群管理员" else "取消 ${request.userId} 群管理员"
		is QqRequest.SetGroupCard -> "设置 ${request.userId} 群名片"
		is QqRequest.SetGroupSpecialTitle -> "设置 ${request.userId} 专属头衔"
		is QqRequest.SetGroupName -> "修改群 ${request.groupId} 名称"
		is QqRequest.SetGroupLeave -> "退出群 ${request.groupId}"
		is QqRequest.GroupPoke -> "在群 ${request.groupId} 戳 ${request.userId}"
		is QqRequest.SendLike -> "给 ${request.userId} 点赞"
		is QqRequest.OcrImage -> "OCR 识别图片"
		is QqRequest.GetForwardMsg -> "读取合并转发 ${request.messageId}"
		is QqRequest.GetEssenceMsgList -> "获取群 ${request.groupId} 精华列表"
		is QqRequest.SetEssenceMsg -> "将 ${request.messageId} 设为群精华"
		is QqRequest.DeleteEssenceMsg -> "取消 ${request.messageId} 精华"
		is QqRequest.SendGroupNotice -> "发布群 ${request.groupId} 公告"
		is QqRequest.GetGroupRootFiles -> "读取群 ${request.groupId} 根目录文件"
		is QqRequest.GetGroupFilesByFolder -> "读取群 ${request.groupId} 文件夹 ${request.folderId} 文件"
		is QqRequest.GetGroupFileUrl -> "获取群 ${request.groupId} 文件 ${request.fileId} 下载链接"
		is QqRequest.UploadGroupFile -> "上传文件到群 ${request.groupId}"
		is QqRequest.UploadPrivateFile -> "上传文件给 ${request.userId}"
		is QqRequest.DownloadFile -> "下载文件到本地"
	}

	/** 审批时需要完整展示的内容性参数；describe 已表达的对象不再重复。 */
	private fun requestDetail(request: QqRequest): String? = when (request) {
		is QqRequest.SendPrivateMsg -> "消息内容：${request.message}"
		is QqRequest.SendGroupMsg -> "消息内容：${request.message}"
		is QqRequest.SetGroupCard -> "新的群名片：${request.card}"
		is QqRequest.SetGroupName -> "新的群名称：${request.groupName}"
		is QqRequest.SetGroupSpecialTitle -> "专属头衔：${request.specialTitle}"
		is QqRequest.SendGroupNotice -> "公告内容：${request.content}"
		is QqRequest.UploadGroupFile -> "上传文件：${request.name}（来源：${request.file}）"
		is QqRequest.UploadPrivateFile -> "上传文件：${request.name}（来源：${request.file}）"
		is QqRequest.OcrImage -> "图片：${request.image}"
		is QqRequest.DownloadFile -> "下载链接：${request.url}"
		else -> null
	}

	private fun doneLabel(label: String): String = "已$label"

	// ==================== 文件下载 ====================

	private suspend fun downloadFile(request: QqRequest.DownloadFile): String {
		val timeout = request.timeoutSeconds.seconds
		val bytes = withTimeoutOrNull(timeout) {
			httpClient.get(request.url).body<ByteArray>()
		} ?: throw NapCatApiException(-1, null, "下载超时：${request.url}")

		withContext(Dispatchers.IO) {
			Files.createDirectories(request.target.parent)
			request.target.writeBytes(bytes)
		}
		return "已下载到：${request.displayPath}"
	}

	private companion object {
		private val httpClient: HttpClient by lazy { HttpClient(CIO) }
	}
}

// ==================== 展示辅助 ====================

fun GroupInfo.groupInfoText(): String = buildString {
	append("群号 $groupId")
	if (groupName.isNotEmpty()) append("，群名 $groupName")
	append("，成员 ${memberCount ?: "?"}/${maxMemberCount ?: "?"}")
}

fun GroupMember.memberText(): String = buildString {
	append(card?.ifBlank { null } ?: nickname ?: userId)
	if (role != null) append("（$role）")
	append(" QQ=$userId")
}

private fun messageLine(event: GroupMessageEvent) =
	messageLine(event.rawMessage.ifEmpty { chainText(event.message) }, event.sender.nickname ?: event.userId.toString())

private fun messageLine(event: PrivateMessageEvent) =
	messageLine(event.rawMessage.ifEmpty { chainText(event.message) }, event.sender.nickname ?: event.userId.toString())

private fun messageLine(raw: String?, who: String): String =
	"$who: ${raw ?: "（无文本）"}"

fun essenceLine(essence: EssenceMsg): String = buildString {
	append(essence.senderNick ?: essence.senderId.toString())
	append("：").append(essence.message?.let(::chainText) ?: "（消息已过期）")
}

/**
 * 消息链文本：纯文本与 @ 以可读形式保留；其余富段输出完整 OneBot JSON，确保模型解析出的字段（url/file/id 等）不丢失。
 */
fun chainText(chain: MessageChain): String = chain.joinToString("") { segment ->
	when (segment) {
		is Segment.Text -> segment.text
		is Segment.At -> "@${segment.qq}"
		else -> " " + Json.encodeToString(segment)
	}
}

fun GroupFileList.fileListText(): String = buildString {
	if (files.isNotEmpty()) {
		append("文件：\n")
		files.forEach { append("  ${it.fileName}（${it.fileSize} 字节）file_id=${it.fileId} busid=${it.busid}\n") }
	}
	if (folders.isNotEmpty()) {
		append("文件夹：\n")
		folders.forEach { append("  ${it.folderName} folder_id=${it.folderId}\n") }
	}
	if (files.isEmpty() && folders.isEmpty()) append("（空目录）")
}.trimEnd()
