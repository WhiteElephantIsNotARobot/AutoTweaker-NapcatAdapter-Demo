package io.github.autotweaker.demo.adapter.napcat.setting

import com.google.auto.service.AutoService
import io.github.autotweaker.api.base.IntSetting
import io.github.autotweaker.api.base.StringSetting
import io.github.autotweaker.api.base.zh
import io.github.autotweaker.api.config.SettingDef

/** NapCat 正向 WebSocket 地址。 */
@AutoService(SettingDef::class)
class Host : StringSetting("127.0.0.1", zh("NapCat 正向 WebSocket 地址"))

/** NapCat 正向 WebSocket 端口。 */
@AutoService(SettingDef::class)
class Port : IntSetting(3001, zh("NapCat 正向 WebSocket 端口"))

/** NapCat 正向 WebSocket 访问令牌，留空表示无令牌。 */
@AutoService(SettingDef::class)
class Token : StringSetting("", zh("NapCat 正向 WebSocket 访问令牌"))
