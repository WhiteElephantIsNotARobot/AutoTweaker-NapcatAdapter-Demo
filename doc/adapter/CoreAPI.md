# CoreAPI

核心 API 接口，提供对各子系统的访问。

## 定义

```kotlin
interface CoreAPI {
    val adapter: AdapterAPI
    val session: SessionAPI
    val config: ConfigAPI
    val secret: SecretAPI
    val i18n: I18nAPI

    fun chat(request: CoreLlmRequest): Flow<CoreLlmResult>
    fun bash(arg: ShellExec): Flow<ShellEvent>
}
```

## 属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `adapter` | `AdapterAPI` | 适配器管理 |
| `session` | `SessionAPI` | 会话管理 |
| `config` | `ConfigAPI` | 配置管理 |
| `secret` | `SecretAPI` | 密钥管理 |
| `i18n` | `I18nAPI` | 国际化管理 |

## 方法

### chat

```kotlin
fun chat(request: CoreLlmRequest): Flow<CoreLlmResult>
```

发起 LLM 聊天请求。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `request` | `CoreLlmRequest` | 聊天请求 |

**返回值：** `Flow<CoreLlmResult>` 流式聊天结果

**异常：**

| 异常 | 条件 |
|------|------|
| `IllegalStateException` | 所有模型重试耗尽 |

**示例：**

```kotlin
val request = CoreLlmRequest(
    model = modelId,
    fallbackModels = listOf(fallbackId),
    messages = listOf(
        ChatMessage.SystemMessage("你是助手", Clock.System.now()),
        ChatMessage.UserMessage("你好", Clock.System.now())
    ),
    stream = true
)

core.chat(request).collect { result ->
    when (val msg = result.result.message) {
        is ChatMessage.AssistantMessage -> println(msg.content)
        else -> {}
    }
}
```

### bash

```kotlin
fun bash(arg: ShellExec): Flow<ShellEvent>
```

执行 Shell 命令。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `arg` | `ShellExec` | Shell 执行参数 |

**返回值：** `Flow<ShellEvent>` 流式 Shell 事件

**异常：**

| 异常 | 条件 |
|------|------|
| `NoContainerRunningException` | 容器模式下容器未运行 |

**示例：**

```kotlin
val exec = ShellExec(
    command = "ls -la",
    directory = Path.of("/home/user"),
    container = false,
    environment = emptyMap(),
    timeout = 30.seconds
)

core.bash(exec).collect { event ->
    when (event) {
        is ShellEvent.Stdout -> print(event.text)
        is ShellEvent.Stderr -> System.err.print(event.text)
        is ShellEvent.Exit -> println("退出码: ${event.result.exitCode}")
    }
}
```

## 子接口索引

| 接口 | 说明 | 文档 |
|------|------|------|
| [AdapterAPI](../core-api/AdapterAPI.md) | 适配器管理 | 列出、启动、停止适配器 |
| [SessionAPI](../core-api/SessionAPI.md) | 会话管理 | 创建/删除/发送/控制会话 |
| [ConfigAPI](../core-api/ConfigAPI.md) | 配置管理 | 环境变量/Provider/Model/ApiKey |
| [SecretAPI](../core-api/SecretAPI.md) | 密钥管理 | 解锁/修改密码 |
| [I18nAPI](../core-api/I18nAPI.md) | 国际化管理 | 翻译模型/状态 |
