package io.github.autotweaker.demo.adapter.napcat.config

import com.google.auto.service.AutoService
import io.github.autotweaker.api.config.SettingDef
import io.github.autotweaker.api.types.config.SettingValue

/**
 * NapCat 连接与权限配置项
 */
object NapCatSettings {

    /**
     * NapCat WebSocket 服务器地址
     */
    @AutoService(SettingDef::class)
    class Host : SettingDef<SettingValue.ValString> {
        override val default = SettingValue.ValString("localhost")
        override val description = "NapCat WebSocket 服务器地址"
    }

    /**
     * NapCat WebSocket 服务器端口
     */
    @AutoService(SettingDef::class)
    class Port : SettingDef<SettingValue.ValInt> {
        override val default = SettingValue.ValInt(3001)
        override val description = "NapCat WebSocket 服务器端口"
    }

    /**
     * NapCat WebSocket 认证令牌
     */
    @AutoService(SettingDef::class)
    class Token : SettingDef<SettingValue.ValString> {
        override val default = SettingValue.ValString("")
        override val description = "NapCat WebSocket 认证令牌"
    }

    /**
     * 管理员 QQ 号（仅支持一个）
     */
    @AutoService(SettingDef::class)
    class AdminQQ : SettingDef<SettingValue.ValLong> {
        override val default = SettingValue.ValLong(0L)
        override val description = "管理员 QQ 号"
    }
}
