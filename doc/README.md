# AutoTweaker API

> 版本: v0.1.0-alpha.34+5bc34eb8
> 完整文档: [autotweaker.github.io/doc/](https://autotweaker.github.io/doc/)

## 概述

AutoTweaker API 定义了适配器（Adapter）开发所需的接口和数据类型。Adapter 通过 SPI 机制自动发现和加载，可以扩展工具、LLM 提供商、国际化翻译等功能。

API 分两个平台：**common**（跨平台）和 **jvm**（JVM 专属）。自 v0.1.0-alpha.34 起使用 Kotlin Multiplatform（KMP）。

## 包索引

### 核心接口

| 包 | 平台 | 包含 |
|------|------|------|
| [api.adapter](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.adapter/index.html) | jvm | Adapter、CoreAPI、AgentAPI、PathResolver |
| [api.tool](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.tool/index.html) | common | Tool、ToolArgs |
| [api.config](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.config/index.html) | jvm | SettingDef、SettingService |
| [api.i18n](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.i18n/index.html) | jvm | I18nDef、I18nService |
| [api.llm](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.llm/index.html) | jvm | LlmClient |
| [api.hook](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.hook/index.html) | common | ShutdownHook、StartupHook |

### 基础组件

| 包 | 平台 | 包含 |
|------|------|------|
| [api](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api/index.html) | common, jvm | Able 接口、扩展函数、常量 |
| [api.base](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.base/index.html) | jvm | SettingBase、I18nBase、Mutable、CatchingResult、ReentrantMutex |
| [api.base.store](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.base.store/index.html) | jvm | AtomicStore、MutableStore、ImmutableStore、StoreBase |
| [api.storage](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.storage/index.html) | jvm | JsonStore、ObjectStorage |
| [api.trace](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.trace/index.html) | jvm | TraceRecorder |
| [api.debug](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.debug/index.html) | common | Debugger、DbAPI |

### 数据类型

| 包 | 平台 |
|------|------|
| [api.types](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.types/index.html) | common, jvm |
| [api.types.adapter](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.types.adapter/index.html) | jvm |
| [api.types.agent](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.types.agent/index.html) | common, jvm |
| [api.types.config](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.types.config/index.html) | common, jvm |
| [api.types.debug](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.types.debug/index.html) | common |
| [api.types.exception](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.types.exception/index.html) | common, jvm |
| [api.types.i18n](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.types.i18n/index.html) | common |
| [api.types.llm](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.types.llm/index.html) | common, jvm |
| [api.types.log](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.types.log/index.html) | common |
| [api.types.serializer](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.types.serializer/index.html) | common, jvm |
| [api.types.session](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.types.session/index.html) | common, jvm |
| [api.types.shell](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.types.shell/index.html) | common, jvm |
| [api.types.tool](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.types.tool/index.html) | common |
| [api.types.tool.args](https://autotweaker.github.io/doc/-auto-tweaker%20-a-p-i/io.github.autotweaker.api.types.tool.args/index.html) | common |

## 快速入门

```kotlin
@AutoService(Adapter::class)
class MyAdapter : Adapter {
    companion object {
        lateinit var core: CoreAPI
            private set
    }

    override val name = "my-adapter"
    override val description = "我的适配器"

    override fun start(core: CoreAPI) {
        this.core = core
    }

    override fun stop() {}
}
```

## 插件加载

所有插件通过 SPI（ServiceLoader）加载。外部插件 JAR 放入 `~/.config/autotweaker/plugins/`。各插件共享同一个 URLClassLoader，Adapter 和 Tool 可直接互访。
