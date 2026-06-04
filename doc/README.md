# AutoTweaker API 文档

> 版本: v0.1.0-alpha.20
> 源码: [github.com/AutoTweaker/core](https://github.com/AutoTweaker/core/tree/main/api/src/main/kotlin/io/github/autotweaker/api)

## 概述

AutoTweaker API 定义了适配器（Adapter）开发所需的全部接口和数据类型。适配器通过 SPI 机制自动发现和加载，可以扩展工具、LLM 提供商、国际化翻译等功能。

## 模块索引

### 核心接口

| 模块 | 说明 | 文档 |
|------|------|------|
| Adapter | 适配器生命周期接口 | [adapter/](adapter/README.md) |
| CoreAPI | 核心 API 总览 | [adapter/CoreAPI.md](adapter/CoreAPI.md) |
| Tool | Agent 工具接口 | [tool/](tool/README.md) |
| SettingDef | 配置项定义 | [config/SettingDef.md](config/SettingDef.md) |
| I18nDef | 国际化条目定义 | [i18n/I18nDef.md](i18n/I18nDef.md) |
| LlmClient | LLM 提供商接口 | [llm/LlmClient.md](llm/LlmClient.md) |

### CoreAPI 子接口

| 接口 | 说明 | 文档 |
|------|------|------|
| AdapterAPI | 适配器管理 | [core-api/AdapterAPI.md](core-api/AdapterAPI.md) |
| SessionAPI | 会话管理 | [core-api/SessionAPI.md](core-api/SessionAPI.md) |
| ConfigAPI | 配置管理 | [core-api/ConfigAPI.md](core-api/ConfigAPI.md) |
| SecretAPI | 密钥管理 | [core-api/SecretAPI.md](core-api/SecretAPI.md) |
| I18nAPI | 国际化管理 | [core-api/I18nAPI.md](core-api/I18nAPI.md) |
| TraceAPI | 追踪记录管理 | [adapter/CoreAPI.md](adapter/CoreAPI.md#traceapi) |

### 服务接口

| 接口 | 说明 | 文档 |
|------|------|------|
| SettingService | 设置读写服务 | [config/SettingService.md](config/SettingService.md) |
| JsonStore | JSON 键值存储 | [config/JsonStore.md](config/JsonStore.md) |
| I18nService | 国际化服务 | [i18n/I18nService.md](i18n/I18nService.md) |

### 数据类型

| 分类 | 说明 | 文档 |
|------|------|------|
| 基础类型 | Base64、SemVer、Unicode、Url | [types/primitives.md](types/primitives.md) |
| Agent | 状态、错误、流式输出、工具审批 | [types/agent.md](types/agent.md) |
| Config | 配置相关类型 | [types/config.md](types/config.md) |
| I18n | 国际化类型 | [types/i18n.md](types/i18n.md) |
| LLM | 聊天消息、请求、结果、模型数据 | [types/llm.md](types/llm.md) |
| Session | 会话上下文、消息、输出、工作区 | [types/session.md](types/session.md) |
| Shell | Shell 事件、执行、结果 | [types/shell.md](types/shell.md) |
| Trace | 追踪记录器 | [types/trace.md](types/trace.md) |
| Adapter | 适配器信息 | [types/adapter.md](types/adapter.md) |

## 调用规范

### 前置校验原则

调用 CoreAPI 时，调用方需要做以下前置校验以避免异常：

| 场景 | 校验方式 |
|------|----------|
| 操作会话/工作区 | 先通过 `listWorkspaces()` 或 `loadData()` 确认 ID 存在 |
| 添加 Provider/Model/ApiKey | 先通过 `list*()` 方法确认名称唯一 |
| 修改 Provider | 先通过 `listProviders()` 确认 ID 存在 |
| 添加/修改/删除 ApiKey | 先确认 `SecretAPI.isUnlocked` 为 `true` |
| 使用 Provider 类型 | 先通过 `listAvailableProviderTypes()` 确认类型已注册 |
| 设置配置值 | 优先使用 `set(def, value)` 而非 `set(id, value)` 以获得类型安全 |

### 不应做的校验

Core 内部管理以下状态，调用方**不应**自行检查：

| 状态 | 原因 |
|------|------|
| 容器是否运行 | 容器由 Core 按需启动，`isContainerRunning()` 仅供参考 |
| 工具调用参数格式 | Core 内部的 `ToolCallValidator` 负责验证 |
| 文件操作路径合法性 | Core 内部的 `FileSystemService` 负责路径解析和校验 |
| LLM 请求参数 | Core 内部的 `ResilientChat` 负责重试和回退策略 |

### 异常处理策略

| 异常类型 | 含义 | 处理建议 |
|----------|------|----------|
| `IllegalStateException` | 状态不合法（ID 不存在、密钥库未解锁等） | 检查前置条件，提示用户 |
| `IllegalArgumentException` | 参数不合法（名称重复、类型未知等） | 检查输入数据，提示用户 |

## 快速入门

### 1. 创建适配器

```kotlin
class MyAdapter : Adapter {
    companion object {
        lateinit var core: CoreAPI
            private set
    }

    override fun load(coreVersion: SemVer): AdapterInfo {
        return AdapterInfo(
            name = "my-adapter",
            description = "我的适配器",
            version = SemVer(1, 0, 0),
            source = Url("https://github.com/example/my-adapter")
        )
    }

    override fun start(core: CoreAPI) {
        this.core = core
        // 初始化逻辑
    }

    override fun stop() {
        // 清理逻辑
    }
}
```

### 2. 注册 SPI

创建文件 `META-INF/services/io.github.autotweaker.api.adapter.Adapter`：

```
com.example.MyAdapter
```

### 3. 定义设置项

```kotlin
object MySettings {
    @AutoService(SettingDef::class)
    class MaxRetries : SettingDef<SettingValue.ValInt> {
        override val default = SettingValue.ValInt(5)
        override val description = "最大重试次数"
    }
}
```

### 4. 创建工具

```kotlin
class MyTool : Tool {
    override val meta = Tool.Meta(
        name = "my-tool",
        description = "我的工具",
        functions = listOf(
            Tool.Function(
                name = "execute",
                description = "执行操作",
                parameters = mapOf(
                    "input" to Tool.Function.Property(
                        description = "输入参数",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    )
                )
            )
        )
    )

    override suspend fun execute(input: Tool.ToolInput): Tool.ToolOutput {
        // 通过适配器访问设置服务
        val value = MyAdapter.core.config.settingService.get(MySettings.MaxRetries())
        return Tool.ToolOutput("执行成功", true)
    }
}
```

## 插件加载机制

| 扩展点 | 加载方式 | 注册位置 |
|--------|----------|----------|
| Adapter | ServiceLoader + PluginLoader | `META-INF/services/...Adapter` |
| Tool | ServiceLoader + PluginLoader | `META-INF/services/...Tool` |
| SettingDef | ServiceLoader + PluginLoader | `META-INF/services/...SettingDef` |
| I18nDef | ServiceLoader + PluginLoader | `META-INF/services/...I18nDef` |
| LlmClient | ServiceLoader + PluginLoader | `META-INF/services/...LlmClient` |

- **内置组件**：通过 `ServiceLoader` 从 classpath 加载
- **外部插件**：从 `~/.config/autotweaker/plugins/` 目录加载 JAR 文件
- **优先级**：外部插件优先于内置组件（同名时外部覆盖内置）

- **ClassLoader**：所有插件共享同一个 URLClassLoader（自 v0.1.0-alpha.13 起），Adapter 和 Tool 可直接互访对方的类型和状态
