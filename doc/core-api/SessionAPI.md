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
    suspend fun loadData(ids: List<UUID>): List<SessionData>?
    suspend fun loadContext(sessionId: UUID): SessionContext?
    suspend fun loadMessages(ids: List<UUID>): List<SessionMessage>?
    fun getUsageSnapshots(): List<UsageSnapshot>

    // 工作区管理
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

**异常：**

| 异常 | 条件 |
|------|------|
| `IllegalStateException("Workspace not found: ...")` | 工作区 ID 不存在 |
| `IllegalStateException("Unknown model: ...")` | 模型 ID 不存在 |
| `IllegalArgumentException("Duplicate CoreTool: ...")` | 存在重复的 CoreTool 名称 |
| `IllegalStateException("SecretManager is locked.")` | 密钥管理器未解锁 |

**副作用：**

- 若工作区为容器工作区（路径推断），自动启动 Docker 容器
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

**副作用：**

- 停止会话中的 Agent
- 从内存和数据库中移除会话数据
- 从工作区的会话列表中移除

### getHandle

```kotlin
suspend fun getHandle(sessionId: UUID): SessionHandle
```

获取会话句柄。若会话不在内存中，自动从数据库恢复（sessionOrRestore）。

**返回值：** `SessionHandle` 会话句柄

**异常：**

| 异常 | 条件 |
|------|------|
| `IllegalStateException("<id> not found")` | 会话 ID 在数据库中不存在 |
| `IllegalStateException("Workspace not found: ...")` | 会话关联的工作区不存在 |

**说明：** `create()` 后立即调用 `getHandle()` 不会抛异常，因为会话已进入内存。异常仅发生在引用不存在或已损坏的会话时。

### updateTitle

```kotlin
suspend fun updateTitle(sessionId: UUID, title: String)
```

更新会话标题。内部通过 `sessionOrRestore` 获取会话。

### updateConfig

```kotlin
suspend fun updateConfig(sessionId: UUID, config: SessionConfig)
```

更新会话配置（模型、thinking 等）。

**副作用：**

- 更新 Agent 的模型配置
- 若会话正在处理中，模型更新会在当前任务完成后生效

## 会话控制

所有会话控制方法均为 `suspend`，内部通过 `sessionOrRestore` 获取会话，异常行为同 `getHandle`。

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

**副作用：**

- 创建用户消息记录
- 触发 Agent 处理流程
- Agent 状态从 `FREE` 转为 `PROCESSING`

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

**状态要求：** Agent 必须处于 `WAITING` 状态

## 数据加载

### loadData / loadContext / loadMessages

```kotlin
suspend fun loadData(ids: List<UUID>): List<SessionData>?
suspend fun loadContext(sessionId: UUID): SessionContext?
suspend fun loadMessages(ids: List<UUID>): List<SessionMessage>?
```

从数据库加载会话数据。

### getUsageSnapshots

```kotlin
fun getUsageSnapshots(): List<UsageSnapshot>
```

获取所有使用量快照。

## 工作区管理

### isContainerRunning

```kotlin
fun isContainerRunning(): Boolean
```

检查容器是否正在运行。容器按需启动，此方法返回当前状态。

### createWorkspace

```kotlin
fun createWorkspace(meta: WorkspaceMeta): WorkspaceData
```

创建工作区。容器状态从工作区路径推断。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `meta` | `WorkspaceMeta` | 工作区元数据（displayName, path） |

**异常：**

| 异常 | 条件 |
|------|------|
| `IllegalStateException("... is not a directory")` | 路径不是目录 |
| `IllegalStateException("Workspace with name ... already exists")` | 工作区显示名称重复 |
| `IllegalStateException("already exists id=...")` | 工作区 ID 重复 |

### renameWorkspace

```kotlin
fun renameWorkspace(id: UUID, newName: String)
```

重命名工作区。

**异常：**

| 异常 | 条件 |
|------|------|
| `IllegalStateException("Workspace not found: ...")` | 工作区 ID 不存在 |

### deleteWorkspace

```kotlin
suspend fun deleteWorkspace(id: UUID)
```

删除工作区。

**异常：**

| 异常 | 条件 |
|------|------|
| `IllegalStateException("Workspace not found: ...")` | 工作区 ID 不存在 |
| `IllegalStateException("Cannot delete default workspace")` | 不能删除默认工作区 |

**副作用：**

- 删除工作区内的所有会话
- 从数据库中移除工作区数据

### listWorkspaces

```kotlin
fun listWorkspaces(): List<WorkspaceData>
```

列出所有工作区。
