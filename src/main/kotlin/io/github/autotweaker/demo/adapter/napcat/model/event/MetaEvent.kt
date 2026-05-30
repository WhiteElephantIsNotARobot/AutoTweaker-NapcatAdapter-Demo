package io.github.autotweaker.demo.adapter.napcat.model.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 元事件基类
 *
 * 元事件用于 WebSocket 连接的状态管理，包括心跳和生命周期事件。
 */
@Serializable
sealed class MetaEvent : OneBotEvent()

/**
 * 心跳元事件
 *
 * 定期发送，用于检测连接是否存活。
 *
 * @property time 事件时间戳
 * @property selfId 机器人 QQ 号
 * @property status 机器人状态
 * @property interval 心跳间隔（毫秒）
 */
@Serializable
data class HeartbeatMetaEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    val status: HeartbeatStatus,
    val interval: Long
) : MetaEvent() {
    /**
     * 心跳状态信息
     *
     * @property online 是否在线
     * @property good 是否正常运行
     */
    @Serializable
    data class HeartbeatStatus(
        val online: Boolean,
        val good: Boolean
    )
}

/**
 * 生命周期元事件
 *
 * WebSocket 连接建立或断开时触发。
 *
 * @property time 事件时间戳
 * @property selfId 机器人 QQ 号
 * @property subType 子类型。已知值: connect, enable, disable
 */
@Serializable
data class LifecycleMetaEvent(
    override val time: Long,
    @SerialName("self_id") override val selfId: Long,
    @SerialName("sub_type") val subType: String
) : MetaEvent()
