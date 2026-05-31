# CoreAPI.SessionAPI

会话管理子接口。

## 定义

```kotlin
interface SessionAPI {
    // 会话生命周期
    suspend fun create(config: SessionConfig): UUID
    suspend fun create(workspaceId: UUID, config: SessionConfig): UUID
    suspend fun delete(sessionId: UUID)
    suspend fun getHandle(sessionId: UUID): SessionHandle
    suspend fun updateTitle(sessionId: UUID, title: String)
    suspend fun updateConfig(sessionId: UUID, config: SessionConfig)

    // 会话控制
    suspend fun stop(sessionId: UUID)
    suspend fun pause(sessionId: UUID)
    suspend fun resume(sessionId: UUID)
    suspend fun cancel(sessionId: UUID)
    suspend fun retry(sessionId: UUID)
    suspend fun compact(sessionId: UUID)

    // 消息交互
    suspend fun send(sessionId: UUID, content: String, images: List<Base64>? = null)
    suspend fun approveToolCall(sessionId: UUID, approvals: List<ToolApprove>)

    // 数据加载
    suspend fun loadData(ids: List<UUID>): List<SessionData>
    suspend fun loadContext(sessionId: UUID): SessionContext?
    suspend fun loadMessages(ids: List<UUID>): List<SessionMessage>
    fun getUsageSnapshots(): List<UsageSnapshot>

    // 工作区管理
    val defaultWorkspaceId: UUID
    fun createWorkspace(meta: WorkspaceMeta): WorkspaceData
    fun renameWorkspace(id: UUID, newName: String)
    suspend fun deleteWorkspace(id: UUID)
    fun listWorkspaces(): List<WorkspaceData>

    fun isContainerRunning(): Boolean
}
```

## 会话生命周期

### create

```kotlin
suspend fun create(config: SessionConfig): UUID
suspend fun create(workspaceId: UUID, config: SessionConfig): UUID
```

创建新会话。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `config` | `SessionConfig` | 会话配置 |
| `workspaceId` | `UUID` | 工作区 ID（可选，默认使用默认工作区） |

**返回值：** `UUID` 会话 ID

**前置校验：**

- 使用 `create(config)` 时无需额外校验，自动使用默认工作区
- 使用 `create(workspaceId, config)` 时需先通过 `listWorkspaces()` 确认工作区存在

**异常：**

| 异常 | 条件 | 预防 |
|------|------|------|
| `IllegalStateException("Workspace not found: ...")` | 工作区 ID 不存在 | 先调用 `listWorkspaces()` 确认 |
| `IllegalStateException("Unknown model: ...")` | 模型 ID 不存在 | 先通过 `ConfigAPI.listModels()` 确认 |

**副作用：**

- 若工作区为容器工作区，自动启动 Docker 容器（无需调用方干预）
- 会话数据持久化到数据库
- 工作区更新会话 ID 列表
- 会话立即进入内存，后续 `getHandle()` 可直接获取

### delete

```kotlin
suspend fun delete(sessionId: UUID)
```

删除会话。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `sessionId` | `UUID` | 会话 ID |

**前置校验：**

- 无需额外校验，会话不存在时静默处理

**副作用：**

- 停止会话中的 Agent
- 从内存和数据库中移除会话数据
- 从工作区的会话列表中移除

### getHandle

```kotlin
suspend fun getHandle(sessionId: UUID): SessionHandle
```

获取会话句柄。若会话不在内存中，自动从数据库恢复。

**返回值：** `SessionHandle` 会话句柄

**前置校验：**

- 通过 `loadData()` 确认会话 ID 存在（可选，`create()` 后立即调用不需要）

**异常：**

| 异常 | 条件 | 预防 |
|------|------|------|
| `IllegalStateException("<id> not found")` | 会话 ID 在数据库中不存在 | 先调用 `loadData()` 确认 |
| `IllegalStateException("Workspace not found: ...")` | 会话关联的工作区不存在 | 工作区数据损坏时发生 |

**说明：** `create()` 后立即调用 `getHandle()` 不会抛异常，因为会话已进入内存。异常仅发生在引用不存在或已损坏的会话时。

### updateTitle

```kotlin
suspend fun updateTitle(sessionId: UUID, title: String)
```

更新会话标题。内部通过 `sessionOrRestore` 获取会话，异常同 `getHandle`。

### updateConfig

```kotlin
suspend fun updateConfig(sessionId: UUID, config: SessionConfig)
```

更新会话配置（模型、thinking 等）。内部通过 `sessionOrRestore` 获取会话，异常同 `getHandle`。

**副作用：**

- 更新 Agent 的模型配置
- 若会话正在处理中，模型更新会在当前任务完成后生效

## 会话控制

所有会话控制方法均为 `suspend`，内部通过 `sessionOrRestore` 获取会话，异常同 `getHandle`。

### stop

```kotlin
suspend fun stop(sessionId: UUID)
```

停止会话。

**副作用：**

- 取消当前任务
- 归档当前轮次上下文
- 关闭协程作用域

### pause / resume

```kotlin
suspend fun pause(sessionId: UUID)
suspend fun resume(sessionId: UUID)
```

暂停/恢复会话。

**状态转换：**

- `pause()`: Agent 状态从 `PROCESSING`/`TOOL_CALLING` 转为 `PAUSED`
- `resume()`: Agent 状态从 `PAUSED` 转为 `FREE`

### cancel

```kotlin
suspend fun cancel(sessionId: UUID)
```

取消当前工具调用或上下文压缩。

### retry

```kotlin
suspend fun retry(sessionId: UUID)
```

重试上一次请求（仅在 `ERROR` 状态有效）。

### compact

```kotlin
suspend fun compact(sessionId: UUID)
```

手动触发上下文压缩。

**副作用：**

- 调用 LLM 总结历史消息
- 生成 `SummarizedMessage`
- 更新会话上下文

## 消息交互

### send

```kotlin
suspend fun send(sessionId: UUID, content: String, images: List<Base64>? = null)
```

发送用户消息。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `sessionId` | `UUID` | 会话 ID |
| `content` | `String` | 消息内容 |
| `images` | `List<Base64>?` | 图片列表（可选） |

**前置校验：**

- 确保 `sessionId` 有效（通过 `loadData()` 或刚从 `create()` 获取）
- `images` 中的 `Base64` 对象会在构造时自动校验格式

**副作用：**

- 创建用户消息记录
- 触发 Agent 处理流程
- Agent 状态从 `FREE` 转为 `PROCESSING`

**说明：** `send` 是 `suspend` 方法。若 Agent 当前正忙（`PROCESSING`/`TOOL_CALLING` 等），调用会挂起等待 Agent 进入可处理状态后再继续，不会丢弃消息。

### approveToolCall

```kotlin
suspend fun approveToolCall(sessionId: UUID, approvals: List<ToolApprove>)
```

审批工具调用请求。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `sessionId` | `UUID` | 会话 ID |
| `approvals` | `List<ToolApprove>` | 审批列表 |

**说明：** 若 Agent 当前不处于 `WAITING` 状态，审批数据会在 Agent 内部队列中排队，待 Agent 进入 `WAITING` 状态后自动处理，不会丢弃输入数据。

## 数据加载

### loadData / loadContext / loadMessages

```kotlin
suspend fun loadData(ids: List<UUID>): List<SessionData>
suspend fun loadContext(sessionId: UUID): SessionContext?
suspend fun loadMessages(ids: List<UUID>): List<SessionMessage>
```

从数据库加载会话数据。不会抛异常，ID 不存在时返回空列表（`loadContext` 仍返回 `null`）。

### getUsageSnapshots

```kotlin
fun getUsageSnapshots(): List<UsageSnapshot>
```

获取所有使用量快照。

## 工作区管理

### defaultWorkspaceId

```kotlin
val defaultWorkspaceId: UUID
```

默认工作区的 ID。`listWorkspaces()` 返回的工作区列表中不标识哪个是默认工作区，通过此属性可获取默认工作区 ID。

### isContainerRunning

```kotlin
fun isContainerRunning(): Boolean
```

检查容器是否正在运行。

> **注意：** 这是一个只读查询方法。容器由 Core 按需启动和管理，调用方**不应**基于此方法的返回值决定是否启动容器。容器会在创建容器工作区的会话时自动启动。

### createWorkspace

```kotlin
fun createWorkspace(meta: WorkspaceMeta): WorkspaceData
```

创建工作区。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `meta` | `WorkspaceMeta` | 工作区元数据（displayName, path） |

**前置校验：**

- 通过 `listWorkspaces()` 确保 `displayName` 不重复
- 确保 `path` 指向一个已存在的目录

**异常：**

| 异常 | 条件 | 预防 |
|------|------|------|
| `IllegalArgumentException("Failed requirement.")` | `displayName` 与现有工作区重复 | 先调用 `listWorkspaces()` 检查 |
| `IllegalStateException("... is not a directory")` | `path` 不是目录 | 确保路径存在且是目录 |

**关于容器：** `WorkspaceMeta` 中不包含容器相关字段。Core 根据工作区路径自动判断是否使用容器模式，调用方无需也无法手动指定。

### renameWorkspace

```kotlin
fun renameWorkspace(id: UUID, newName: String)
```

重命名工作区。

**前置校验：**

- 通过 `listWorkspaces()` 确认 `id` 存在
- 确保 `newName` 不与其他工作区重复

**异常：**

| 异常 | 条件 | 预防 |
|------|------|------|
| `IllegalStateException("Workspace not found: ...")` | 工作区 ID 不存在 | 先调用 `listWorkspaces()` |
| `IllegalArgumentException("Failed requirement.")` | 新名称与其他工作区重名 | 先调用 `listWorkspaces()` 检查 |

### deleteWorkspace

```kotlin
suspend fun deleteWorkspace(id: UUID)
```

删除工作区。

**前置校验：**

- 通过 `listWorkspaces()` 确认 `id` 存在
- 通过 `defaultWorkspaceId` 确认不是默认工作区

**异常：**

| 异常 | 条件 | 预防 |
|------|------|------|
| `IllegalStateException("Workspace not found: ...")` | 工作区 ID 不存在 | 先调用 `listWorkspaces()` |
| `IllegalStateException("Cannot delete default workspace")` | 尝试删除默认工作区 | 先检查 `id != defaultWorkspaceId` |

**副作用：**

- 删除工作区内的所有会话
- 从数据库中移除工作区数据

### listWorkspaces

```kotlin
fun listWorkspaces(): List<WorkspaceData>
```

列出所有工作区。
