# Shell 类型

## ShellEvent

Shell 事件密封类。

```kotlin
sealed class ShellEvent {
    data class Stdout(val text: String) : ShellEvent()
    data class Stderr(val text: String) : ShellEvent()
    data class Exit(val result: ShellResult) : ShellEvent()
}
```

| 类型 | 说明 |
|------|------|
| `Stdout` | 标准输出 |
| `Stderr` | 标准错误 |
| `Exit` | 命令退出 |

---

## ShellExec

Shell 执行参数。

```kotlin
data class ShellExec(
    val command: String,
    val directory: Path,
    val container: Boolean,
    val environment: Map<String, String>,
    val timeout: Duration
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `command` | `String` | 命令内容 |
| `directory` | `Path` | 工作目录 |
| `container` | `Boolean` | 是否在容器中执行 |
| `environment` | `Map<String, String>` | 环境变量 |
| `timeout` | `Duration` | 超时时间 |

---

## ShellResult

Shell 执行结果。

```kotlin
data class ShellResult(
    val exitCode: Int,
    val timeout: Boolean,
    val duration: Duration,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `exitCode` | `Int` | 退出码 |
| `timeout` | `Boolean` | 是否超时 |
| `duration` | `Duration` | 执行时长 |
