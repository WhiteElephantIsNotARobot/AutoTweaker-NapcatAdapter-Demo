package io.github.autotweaker.demo.adapter.napcat.bridge

import io.github.autotweaker.demo.adapter.napcat.api.NapCatApi
import io.github.autotweaker.demo.adapter.napcat.model.data.ForwardMessage
import io.github.autotweaker.demo.adapter.napcat.model.message.MessageSegment
import org.slf4j.LoggerFactory

/**
 * 消息上下文构建器
 *
 * 负责为 LLM 构建带上下文的消息，包括：
 * - 注入会话所在的群/私聊信息
 * - 注入最近的群消息历史（可通过设置关闭）
 * - 处理合并转发消息
 * - 注入环境信息
 *
 * @property napCat NapCat API 实例，用于获取群名、成员列表、消息历史等
 * @property sessionManager 会话管理器，用于获取用户历史注入设置
 */
class ContextBuilder(
    private val napCat: NapCatApi,
    private val sessionManager: SessionManager
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 构建带上下文的消息
     *
     * 在消息开头注入会话所在的群/私聊信息，以及最近的群消息历史和环境信息。
     * 消息历史注入可通过用户设置关闭。
     */
    suspend fun buildMessageWithContext(groupId: Long?, userId: Long, text: String): String {
        return try {
            val contextBuilder = StringBuilder()

            // 注入会话所在的群/私聊信息
            contextBuilder.appendLine("<session-info>")
            if (groupId != null) {
                // 群聊：获取群名并注入群号和群名
                val groupName = try {
                    napCat.getGroupList().find { it.groupId == groupId }?.groupName?.let { escapeXml(it) }
                } catch (e: Exception) {
                    logger.warn("Failed to get group list  groupId={}", groupId, e)
                    null
                }
                contextBuilder.appendLine("会话类型：群聊")
                contextBuilder.appendLine("群号：$groupId")
                if (groupName != null) {
                    contextBuilder.appendLine("群名：${escapeXml(groupName)}")
                }
            } else {
                // 私聊
                contextBuilder.appendLine("会话类型：私聊")
                contextBuilder.appendLine("用户 QQ 号：$userId")
            }
            contextBuilder.appendLine("</session-info>")
            // 构建上下文
            contextBuilder.appendLine("<context>")

            // 检查用户是否启用了消息历史注入
            val historyEnabled = sessionManager.getUserHistoryInjection(userId)
            if (historyEnabled) {
                // 获取消息历史
                val messages = try {
                    if (groupId != null) {
                        napCat.getGroupMsgHistory(groupId, count = 20)
                    } else {
                        napCat.getPrivateMsgHistory(userId, count = 20)
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to load message history", e)
                    emptyList()
                }

                // 获取群成员列表用于获取昵称（仅群聊）
                val members = if (groupId != null) {
                    try {
                        napCat.getGroupMemberList(groupId)
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                // 收集合并转发消息
                val forwardMessages = mutableMapOf<String, List<ForwardMessage>>()

                // 构建消息历史
                messages.forEach { msg ->
                    val nickname = escapeXml(if (groupId != null) {
                        members.find { it.userId == msg.userId }?.let {
                            it.card.ifEmpty { it.nickname }
                        } ?: msg.sender.nickname.ifEmpty { msg.sender.userId.toString() }
                    } else {
                        msg.sender.nickname.ifEmpty { msg.sender.userId.toString() }
                    })
                    val time = formatTime(msg.time)

                    // 检测合并转发消息
                    val forwardId = extractForwardId(msg.rawMessage)
                    if (forwardId != null) {
                        try {
                            val forwardContent = napCat.getForwardMsg(forwardId)
                            forwardMessages[forwardId] = forwardContent
                            contextBuilder.appendLine("[$time] $nickname: [合并转发消息 id=$forwardId]")
                        } catch (e: Exception) {
                            logger.warn("Failed to get forward message  forwardId={}", forwardId, e)
                            contextBuilder.appendLine("[$time] $nickname: ${escapeXml(msg.rawMessage)}")
                        }
                    } else {
                        contextBuilder.appendLine("[$time] $nickname: ${escapeXml(msg.rawMessage)}")
                    }
                }

                // 添加合并转发消息内容
                if (forwardMessages.isNotEmpty()) {
                    contextBuilder.appendLine()
                    contextBuilder.appendLine("<forward>")
                    forwardMessages.forEach { (id, forwardMsgs) ->
                        contextBuilder.appendLine("  <forward id=\"$id\">")
                        forwardMsgs.forEach { msg ->
                            val nickname = escapeXml(if (groupId != null) {
                                members.find { it.userId == msg.sender.userId }?.let {
                                    it.card.ifEmpty { it.nickname }
                                } ?: msg.sender.nickname.ifEmpty { msg.sender.userId.toString() }
                            } else {
                                msg.sender.nickname.ifEmpty { msg.sender.userId.toString() }
                            })
                            val time = formatTime(msg.time)
                            contextBuilder.appendLine("    [$time] $nickname: ${escapeXml(msg.rawMessage)}")
                        }
                        contextBuilder.appendLine("  </forward>")
                    }
                    contextBuilder.appendLine("</forward>")
                }
            } else {
                logger.debug("History injection disabled  userId={}", userId)
            }

            contextBuilder.appendLine("</context>")
            contextBuilder.appendLine("<environment>用户所在平台：QQ，请注意输出格式</environment>")
            contextBuilder.append(text)

            val result = contextBuilder.toString()
            logger.debug("Message context built  userId={}  groupId={}  length={}", userId, groupId, result.length)
            result
        } catch (e: Exception) {
            logger.error("Failed to build message context", e)
            // 失败时只发送原始消息
            text
        }
    }

    /**
     * 处理消息链中的合并转发段
     *
     * 从消息链中提取 Forward 段，获取实际内容并替换到文本中
     */
    suspend fun processForwardSegments(text: String, forwardSegments: List<MessageSegment.Forward>): String {
        var result = text
        forwardSegments.forEach { forward ->
            val forwardId = forward.id
            try {
                val forwardContent = napCat.getForwardMsg(forwardId)
                if (forwardContent.isNotEmpty()) {
                    val forwardText = buildString {
                        appendLine("<forward id=\"$forwardId\">")
                        forwardContent.forEach { msg ->
                            val nickname = escapeXml(msg.sender.nickname.ifEmpty { msg.sender.userId.toString() })
                            val time = formatTime(msg.time)
                            appendLine("  [$time] $nickname: ${escapeXml(msg.rawMessage)}")
                        }
                        append("</forward>")
                    }
                    // 替换 CQ 码为实际内容
                    result = result.replace("[CQ:forward,id=$forwardId]", forwardText)
                }
            } catch (e: Exception) {
                logger.warn("Failed to get forward message {}: {}", forwardId, e.message)
            }
        }
        return result
    }

    /**
     * 从 raw_message 中提取合并转发消息 ID
     *
     * @param rawMessage 原始消息文本
     * @return 合并转发消息 ID，如果不是合并转发消息返回 null
     */
    fun extractForwardId(rawMessage: String): String? {
        return FORWARD_PATTERN.find(rawMessage)?.groupValues?.get(1)
    }

    private fun escapeXml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun formatTime(epochSeconds: Long): String =
        java.time.Instant.ofEpochSecond(epochSeconds).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))

    companion object {
        private val FORWARD_PATTERN = "\\[CQ:forward,id=(\\d+)\\]".toRegex()
    }
}
