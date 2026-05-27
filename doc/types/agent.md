# Agent 类型

## AgentStatus

Agent 状态枚举。

```kotlin
enum class AgentStatus {
    FREE, PROCESSING, TOOL_CALLING, WAITING, PAUSED, ERROR
}
```

| 值 | 说明 |
|----|------|
| `FREE` | 空闲 |
| `PROCESSING` | 处理中（LLM 请求） |
| `TOOL_CALLING` | 工具调用中 |
| `WAITING` | 等待用户审批 |
| `PAUSED` | 已暂停 |
| `ERROR` | 错误 |

---

## AgentError

Agent 错误。

```kotlin
data class AgentError(
    val message: String,
    val type: Type,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `message` | `String` | 错误信息 |
| `type` | `Type` | 错误类型 |

### Type

```kotlin
enum class Type {
    LLM, COMPACT,
}
```

| 值 | 说明 |
|----|------|
| `LLM` | LLM 请求错误 |
| `COMPACT` | 上下文压缩错误 |

---

## StreamDelta

流式输出增量。

```kotlin
data class StreamDelta(
    val content: String?,
    val reasoningContent: String?,
    val toolCallFragments: List<ChatResult.ChunkToolCall>?,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `content` | `String?` | 内容增量 |
| `reasoningContent` | `String?` | 推理内容增量 |
| `toolCallFragments` | `List<ChunkToolCall>?` | 工具调用片段 |

---

## ToolApprove

工具调用审批。

```kotlin
data class ToolApprove(
    val callId: String,
    val reason: String? = null,
    val approved: Boolean = true,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `callId` | `String` | 工具调用 ID |
| `reason` | `String?` | 审批原因（拒绝时） |
| `approved` | `Boolean` | 是否批准 |

---

## ToolOutput

工具调用输出。

```kotlin
data class ToolOutput(
    val name: String,
    val callId: String,
    val content: String,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | `String` | 工具名称 |
| `callId` | `String` | 工具调用 ID |
| `content` | `String` | 输出内容 |

---

## ToolResultStatus

工具执行结果状态。

```kotlin
@Serializable
enum class ToolResultStatus {
    SUCCESS,
    FAILURE,
    TIMEOUT,
    CANCELLED,
}
```

| 值 | 说明 |
|----|------|
| `SUCCESS` | 成功 |
| `FAILURE` | 失败 |
| `TIMEOUT` | 超时 |
| `CANCELLED` | 已取消 |

---

## CompactOutput

上下文压缩输出。

```kotlin
data class CompactOutput(
    val status: Status,
    val content: String,
    val usage: Usage?,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `status` | `Status` | 状态 |
| `content` | `String` | 压缩后的内容 |
| `usage` | `Usage?` | 使用量 |

### Status

```kotlin
enum class Status {
    OUTPUTTING, FINISHED, FAILED,
}
```

| 值 | 说明 |
|----|------|
| `OUTPUTTING` | 输出中 |
| `FINISHED` | 完成 |
| `FAILED` | 失败 |
