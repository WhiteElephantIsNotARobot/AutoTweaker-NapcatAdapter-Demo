# Session 类型

## SessionConfig

会话配置。

```kotlin
@Serializable
data class SessionConfig(
    val model: UUID,
    val fallbackModel: List<UUID>?,
    val summarizeModel: UUID,
    val thinking: Boolean,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `model` | `UUID` | 主模型 ID |
| `fallbackModel` | `List<UUID>?` | 备选模型列表 |
| `summarizeModel` | `UUID` | 用于上下文压缩的模型 ID |
| `thinking` | `Boolean` | 是否启用推理 |

---

## SessionContext

会话上下文。

```kotlin
data class SessionContext(
    val systemPrompt: String,
    val index: SessionContextIndex,
    val droppedMessages: List<UUID>?,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `systemPrompt` | `String` | 系统提示词 |
| `index` | `SessionContextIndex` | 上下文索引 |
| `droppedMessages` | `List<UUID>?` | 被丢弃的消息 ID 列表 |

### 伴生方法

| 方法 | 参数 | 返回值 | 说明 |
|------|------|--------|------|
| `emptyContext(systemPrompt)` | `String` | `SessionContext` | 创建空上下文 |

---

## SessionContextIndex

会话上下文索引。

```kotlin
@Serializable
data class SessionContextIndex(
    val compactedRounds: List<CompactedRound>?,
    val historyRounds: List<CompactedRound.CompletedRound>?,
    val currentRound: CurrentRound?,
    val summarizedMessage: UUID?,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `compactedRounds` | `List<CompactedRound>?` | 已压缩的轮次 |
| `historyRounds` | `List<CompletedRound>?` | 历史轮次 |
| `currentRound` | `CurrentRound?` | 当前轮次 |
| `summarizedMessage` | `UUID?` | 总结消息 ID |

### CompactedRound

```kotlin
@Serializable
data class CompactedRound(
    val rounds: List<CompletedRound>,
    val summarizedMessage: UUID,
)
```

### CompactedRound.CompletedRound

```kotlin
@Serializable
data class CompletedRound(
    val userMessage: UUID,
    val turns: List<Turn>?,
    val finalAssistantMessage: UUID?,
)
```

### CurrentRound

```kotlin
@Serializable
data class CurrentRound(
    val userMessage: UUID,
    val turns: List<Turn>?,
    val assistantMessage: UUID?,
    val pendingToolCalls: List<UUID>?,
)
```

### Turn

```kotlin
@Serializable
data class Turn(
    val assistantMessage: UUID,
    val tools: List<Tool>,
)
```

### Turn.Tool

```kotlin
@Serializable
data class Tool(
    val call: UUID,
    val result: UUID,
)
```

---

## SessionData

会话数据。

```kotlin
data class SessionData(
    val id: UUID,
    val title: String?,
    val workspaceId: UUID,
    val config: SessionConfig,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `UUID` | 会话 ID |
| `title` | `String?` | 会话标题 |
| `workspaceId` | `UUID` | 工作区 ID |
| `config` | `SessionConfig` | 会话配置 |

---

## SessionHandle

会话句柄。

```kotlin
data class SessionHandle(
    val id: UUID,
    val context: StateFlow<SessionContext>,
    val output: SharedFlow<SessionOutput>,
    val status: StateFlow<AgentStatus>,
    val data: StateFlow<SessionData>,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `UUID` | 会话 ID |
| `context` | `StateFlow<SessionContext>` | 上下文状态流 |
| `output` | `SharedFlow<SessionOutput>` | 输出事件流 |
| `status` | `StateFlow<AgentStatus>` | Agent 状态流 |
| `data` | `StateFlow<SessionData>` | 会话数据状态流 |

---

## SessionMessage

会话消息密封类。

```kotlin
@Serializable
sealed class SessionMessage {
    abstract val id: UUID
    abstract val timestamp: Instant
}
```

### User

用户消息。

```kotlin
@Serializable
data class User(
    override val id: UUID,
    override val timestamp: Instant,
    val content: String,
    val images: List<Base64>?
) : SessionMessage()
```

### Assistant

助手消息。

```kotlin
@Serializable
data class Assistant(
    override val id: UUID,
    override val timestamp: Instant,
    val reasoning: String?,
    val content: String?,
    val model: UUID,
    val usageSnapshot: UsageSnapshot? = null,
) : SessionMessage()
```

### Tool

工具消息密封类。

```kotlin
@Serializable
sealed class Tool : SessionMessage() {
    abstract val callId: String
}
```

#### Tool.Call

工具调用消息。

```kotlin
@Serializable
data class Call(
    override val id: UUID,
    override val timestamp: Instant,
    override val callId: String,
    val assistantMessage: UUID,
    val name: String,
    val arguments: String,
    val reason: String?,
) : Tool()
```

#### Tool.Result

工具结果消息。

```kotlin
@Serializable
data class Result(
    override val id: UUID,
    override val timestamp: Instant,
    override val callId: String,
    val content: String,
    val status: ToolResultStatus
) : Tool()
```

### Compact

压缩消息。

```kotlin
@Serializable
data class Compact(
    override val id: UUID,
    override val timestamp: Instant,
    val content: String,
    val snapshots: List<UsageSnapshot>? = null,
) : SessionMessage()
```

### UsageRecord

使用量记录消息。

```kotlin
@Serializable
data class UsageRecord(
    override val id: UUID,
    override val timestamp: Instant,
    val snapshot: UsageSnapshot,
) : SessionMessage()
```

---

## SessionOutput

会话输出密封类。

```kotlin
sealed class SessionOutput {
    data class LlmDelta(val delta: StreamDelta) : SessionOutput()
    data class LlmError(
        val content: String?,
        val statusCode: Int?,
        val model: UUID,
        val timestamp: Instant,
    ) : SessionOutput()
    data class Compact(val output: CompactOutput) : SessionOutput()
    data class Tool(val output: ToolOutput) : SessionOutput()
    data class ToolRequest(val requests: List<ToolCallRequest>) : SessionOutput()
    data class Error(val error: AgentError) : SessionOutput()
}
```

| 类型 | 说明 |
|------|------|
| `LlmDelta` | LLM 流式输出增量 |
| `LlmError` | LLM 错误 |
| `Compact` | 上下文压缩输出 |
| `Tool` | 工具输出 |
| `ToolRequest` | 工具调用请求（需要审批） |
| `Error` | Agent 错误 |

---

## ToolCallRequest

工具调用请求。

```kotlin
data class ToolCallRequest(
    val name: String,
    val arguments: String,
    val reason: String? = null,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | `String` | 工具名称 |
| `arguments` | `String` | 参数 JSON |
| `reason` | `String?` | 调用原因 |

---

## WorkspaceData

工作区数据。

```kotlin
@Serializable
data class WorkspaceData(
    val id: UUID = UUID.randomUUID(),
    val meta: WorkspaceMeta,
    val git: Boolean? = null,
    val sessionIds: List<UUID>? = null
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `UUID` | 工作区 ID |
| `meta` | `WorkspaceMeta` | 元数据 |
| `git` | `Boolean?` | 是否是 Git 工作区 |
| `sessionIds` | `List<UUID>?` | 关联的会话 ID 列表 |

---

## WorkspaceMeta

工作区元数据。

```kotlin
@Serializable
data class WorkspaceMeta(
    val name: String,
    val inContainer: Boolean,
    val path: Path
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | `String` | 工作区名称 |
| `inContainer` | `Boolean` | 是否在容器中运行 |
| `path` | `Path` | 工作区路径 |

### 约束

- `path` 必须是已存在的目录

### 异常

| 异常 | 条件 |
|------|------|
| `IllegalStateException("... is not a directory")` | 路径不是目录 |
