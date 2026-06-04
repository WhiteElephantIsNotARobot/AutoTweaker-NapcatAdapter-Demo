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
    val trace: TraceAPI

    fun chat(request: CoreLlmRequest): Flow<CoreLlmResult>
    fun bash(arg: ShellExec): Flow<ShellEvent>
    fun trace(kClass: KClass<*>): TraceRecorder
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
| `trace` | `TraceAPI` | 追踪记录管理 |

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

**前置校验：**

- 确保至少有一个可用的 LLM Provider 和模型（通过 `ConfigAPI.listProviders()` 和 `ConfigAPI.listModels()` 确认）
- 确保 `request.model` 对应的模型 ID 存在
- 若设置了 `fallbackModels`，确保这些模型 ID 也存在

**异常：**

| 异常 | 条件 |
|------|------|
| `IllegalStateException("All LLM chat retries exhausted without success")` | 所有候选模型和重试都失败 |

**说明：** 非流式请求不会抛异常，而是返回 `ChatMessage.ErrorMessage`。流式请求中的错误会通过 `ErrorMessage` 发射到 Flow 中。`IllegalStateException` 仅在所有重试策略均失败时抛出。

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
        is ChatMessage.ErrorMessage -> System.err.println("错误: ${msg.content}")
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
| `IllegalStateException("No container is running. Start a container first.")` | 容器模式（`container = true`）下容器未运行 |

> **注意：** 调用方**不应**在调用 `bash()` 前检查容器是否运行。当 `container = false` 时，命令直接在宿主机执行，不涉及容器。当 `container = true` 时，Core 会自行处理容器状态，如果容器未运行会抛出异常，调用方只需捕获并处理即可。

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

### trace

```kotlin
fun trace(kClass: KClass<*>): TraceRecorder
```

获取指定类的追踪记录器。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `kClass` | `KClass<*>` | 追踪来源的 Kotlin 类 |

**返回值：** `TraceRecorder` 追踪记录器实例

**示例：**

```kotlin
val recorder = core.trace(MyAdapter::class)
recorder.add("my-namespace", "操作完成")
```

---

## 子接口索引

| 接口 | 说明 | 文档 |
|------|------|------|
| [AdapterAPI](../core-api/AdapterAPI.md) | 适配器管理 | 列出、启动、停止适配器 |
| [SessionAPI](../core-api/SessionAPI.md) | 会话管理 | 创建/删除/发送/控制会话 |
| [ConfigAPI](../core-api/ConfigAPI.md) | 配置管理 | 环境变量/Provider/Model/ApiKey |
| [SecretAPI](../core-api/SecretAPI.md) | 密钥管理 | 解锁/修改密码 |
| [I18nAPI](../core-api/I18nAPI.md) | 国际化管理 | 翻译模型/状态 |
| [TraceAPI](#traceapi) | 追踪记录管理 | 查询/删除追踪记录 |

---

## TraceAPI

追踪记录管理接口。

```kotlin
interface TraceAPI {
    suspend fun origins(): List<String>
    suspend fun namespaces(origin: String): List<String>
    suspend fun entries(origin: String, namespace: String, range: UIntRange): List<Instant>
    suspend fun get(origin: String, namespace: String, timestamp: Instant): String?
    suspend fun delete(origin: String, namespace: String, timestamp: Instant)
}
```

### origins

```kotlin
suspend fun origins(): List<String>
```

获取所有追踪来源。

**返回值：** `List<String>` 来源列表

### namespaces

```kotlin
suspend fun namespaces(origin: String): List<String>
```

获取指定来源的所有命名空间。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `origin` | `String` | 追踪来源 |

**返回值：** `List<String>` 命名空间列表

### entries

```kotlin
suspend fun entries(origin: String, namespace: String, range: UIntRange): List<Instant>
```

获取指定来源和命名空间的追踪条目时间戳列表。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `origin` | `String` | 追踪来源 |
| `namespace` | `String` | 命名空间 |
| `range` | `UIntRange` | 索引范围 |

**返回值：** `List<Instant>` 时间戳列表

### get

```kotlin
suspend fun get(origin: String, namespace: String, timestamp: Instant): String?
```

获取指定条目的内容。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `origin` | `String` | 追踪来源 |
| `namespace` | `String` | 命名空间 |
| `timestamp` | `Instant` | 条目时间戳 |

**返回值：** `String?` 条目内容，不存在则返回 null

### delete

```kotlin
suspend fun delete(origin: String, namespace: String, timestamp: Instant)
```

删除指定条目。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `origin` | `String` | 追踪来源 |
| `namespace` | `String` | 命名空间 |
| `timestamp` | `Instant` | 条目时间戳 |
