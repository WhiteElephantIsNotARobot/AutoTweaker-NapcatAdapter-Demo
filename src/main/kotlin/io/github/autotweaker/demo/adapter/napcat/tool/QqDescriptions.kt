package io.github.autotweaker.demo.adapter.napcat.tool

import io.github.autotweaker.demo.adapter.napcat.tool.qq.meta.QqMetaDescriptions

/**
 * qq 工具的 Meta 描述文案。
 *
 * 文案直接面向 LLM（工具函数与参数说明）与审批人展示，用简洁中文短句。
 */
val QQ_DESCRIPTIONS: QqMetaDescriptions = QqMetaDescriptions(
	toolDescription = "QQ 工具：向当前连接的 QQ 账号及其群聊/私聊发送消息、查询消息与群信息、管理群、OCR 识别图片、上传下载文件",
	functions = QqMetaDescriptions.Functions(
		getGroupList = QqMetaDescriptions.Functions.GetGroupList(
			noCache = "是否跳过缓存强制刷新，默认 false",
		) to "获取当前 QQ 账号已加入的群列表",
		getGroupInfo = QqMetaDescriptions.Functions.GetGroupInfo(
			groupId = "群号",
		) to "获取群的基本信息（群名、成员数）",
		getGroupMemberInfo = QqMetaDescriptions.Functions.GetGroupMemberInfo(
			groupId = "群号",
			userId = "成员 QQ 号",
		) to "获取某群成员的资料（群名片、角色等）",
		getGroupMemberList = QqMetaDescriptions.Functions.GetGroupMemberList(
			groupId = "群号",
		) to "获取群成员列表",
		getGroupMsgHistory = QqMetaDescriptions.Functions.GetGroupMsgHistory(
			groupId = "群号",
			count = "获取条数，默认 20",
		) to "获取群最近的聊天记录（补足你未直接看到的上下文）",
		getPrivateMsgHistory = QqMetaDescriptions.Functions.GetPrivateMsgHistory(
			userId = "用户 QQ 号",
			count = "获取条数，默认 20",
		) to "获取与某用户私聊的最近记录",
		getMsg = QqMetaDescriptions.Functions.GetMsg(
			messageId = "消息 ID",
		) to "按消息 ID 获取单条消息详情",
		sendPrivateMsg = QqMetaDescriptions.Functions.SendPrivateMsg(
			userId = "目标用户 QQ 号",
			message = "消息文本",
		) to "发送私聊消息给指定用户",
		sendGroupMsg = QqMetaDescriptions.Functions.SendGroupMsg(
			groupId = "目标群号",
			message = "消息文本",
		) to "向指定群发送消息",
		deleteMsg = QqMetaDescriptions.Functions.DeleteMsg(
			messageId = "要撤回的消息 ID",
		) to "撤回消息（可撤回自己发的）",
		setGroupKick = QqMetaDescriptions.Functions.SetGroupKick(
			groupId = "群号",
			userId = "被踢成员 QQ 号",
			rejectAddRequest = "是否同时拒绝其再次加群，默认 false",
		) to "将成员移出群",
		setGroupBan = QqMetaDescriptions.Functions.SetGroupBan(
			groupId = "群号",
			userId = "被禁言成员 QQ 号",
			duration = "禁言时长秒数，0 表示解除禁言",
		) to "禁言或解除禁言群成员",
		setGroupWholeBan = QqMetaDescriptions.Functions.SetGroupWholeBan(
			groupId = "群号",
			enable = "true 全员禁言，false 解除",
		) to "设置或解除群全员禁言",
		setGroupAdmin = QqMetaDescriptions.Functions.SetGroupAdmin(
			groupId = "群号",
			userId = "成员 QQ 号",
			enable = "true 设为管理员，false 取消",
		) to "设置或取消群管理员",
		setGroupCard = QqMetaDescriptions.Functions.SetGroupCard(
			groupId = "群号",
			userId = "成员 QQ 号",
			card = "新的群名片，空串清除",
		) to "修改成员的群名片",
		setGroupSpecialTitle = QqMetaDescriptions.Functions.SetGroupSpecialTitle(
			groupId = "群号",
			userId = "成员 QQ 号",
			specialTitle = "专属头衔，空串清除",
		) to "设置成员的群专属头衔",
		setGroupName = QqMetaDescriptions.Functions.SetGroupName(
			groupId = "群号",
			groupName = "新的群名称",
		) to "修改群名称",
		setGroupLeave = QqMetaDescriptions.Functions.SetGroupLeave(
			groupId = "要退出的群号",
		) to "退出群聊（不可轻易恢复，谨慎使用）",
		groupPoke = QqMetaDescriptions.Functions.GroupPoke(
			groupId = "群号",
			userId = "被戳的成员 QQ 号",
		) to "在群内戳一戳某成员",
		sendLike = QqMetaDescriptions.Functions.SendLike(
			userId = "目标 QQ 号",
			times = "点赞次数，默认 1，NapCat 有频率限制",
		) to "给用户点赞",
		ocrImage = QqMetaDescriptions.Functions.OcrImage(
			image = "图片：QQ 图片 file 标识、http(s) URL 或本地可读路径",
		) to "OCR 识别图片中的文字（收到图片时先用此获取图中文字）",
		getForwardMsg = QqMetaDescriptions.Functions.GetForwardMsg(
			messageId = "合并转发消息 ID",
		) to "读取合并转发消息的内容",
		getEssenceMsgList = QqMetaDescriptions.Functions.GetEssenceMsgList(
			groupId = "群号",
		) to "获取群精华消息列表",
		setEssenceMsg = QqMetaDescriptions.Functions.SetEssenceMsg(
			messageId = "要设为精华的消息 ID",
		) to "把消息设为群精华",
		deleteEssenceMsg = QqMetaDescriptions.Functions.DeleteEssenceMsg(
			messageId = "要取消精华的消息 ID",
		) to "取消消息的群精华状态",
		sendGroupNotice = QqMetaDescriptions.Functions.SendGroupNotice(
			groupId = "群号",
			content = "公告文本内容",
		) to "向群发布文本公告",
		getGroupRootFiles = QqMetaDescriptions.Functions.GetGroupRootFiles(
			groupId = "群号",
		) to "获取群文件根目录的文件与文件夹列表",
		getGroupFilesByFolder = QqMetaDescriptions.Functions.GetGroupFilesByFolder(
			groupId = "群号",
			folderId = "文件夹 ID（来自 get_group_root_files）",
		) to "获取群文件某文件夹下的文件列表",
		getGroupFileUrl = QqMetaDescriptions.Functions.GetGroupFileUrl(
			groupId = "群号",
			fileId = "文件 ID",
			busid = "文件的 busid",
		) to "获取群文件的下载链接",
		uploadGroupFile = QqMetaDescriptions.Functions.UploadGroupFile(
			groupId = "目标群号",
			file = "文件来源：本地路径（相对当前工作目录或绝对路径）、http(s) URL 或 base64:// 数据",
			name = "上传后显示的文件名，缺省取源文件名",
			folder = "目标文件夹 ID，缺省上传到根目录",
		) to "上传文件到群",
		uploadPrivateFile = QqMetaDescriptions.Functions.UploadPrivateFile(
			userId = "目标用户 QQ 号",
			file = "文件来源：本地路径（相对当前工作目录或绝对路径）、http(s) URL 或 base64:// 数据",
			name = "上传后显示的文件名，缺省取源文件名",
		) to "上传文件给指定用户",
		downloadFile = QqMetaDescriptions.Functions.DownloadFile(
			url = "要下载的 http(s) URL",
			fileName = "保存的文件名，缺省从 URL 推导",
			timeoutSeconds = "下载超时秒数，默认 60",
		) to "把网络文件下载到本地（保存在 AutoTweaker 临时目录），返回本地路径",
	),
)
