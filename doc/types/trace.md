# Trace 类型

## TraceRecorder

追踪记录器接口，用于记录追踪信息。

```kotlin
interface TraceRecorder {
    fun add(namespace: String, content: String)

    fun exception(e: Throwable)
}

inline fun <T> TraceRecorder.catching(block: () -> T): Result<T>
```

### add

```kotlin
fun add(namespace: String, content: String)
```

添加一条追踪记录。非挂起函数，可在任意上下文调用。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `namespace` | `String` | 命名空间，用于分类追踪记录 |
| `content` | `String` | 追踪内容 |

### exception

```kotlin
fun exception(e: Throwable)
```

记录异常信息到追踪数据库。等价于 `add("e", e.stackTraceToString())`。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `e` | `Throwable` | 异常对象 |

### catching

```kotlin
inline fun <T> TraceRecorder.catching(block: () -> T): Result<T>
```

执行代码块，捕获异常并自动记录到追踪数据库。返回 `Result<T>`，不会抛出异常。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `block` | `() -> T` | 要执行的代码块 |

**示例：**

```kotlin
val trace = core.trace(NapCatAdapter::class)

// 记录 LLM 请求
trace.add("request", "request=$request, model=${request.model}, chatId=$chatId")

// 记录 LLM 响应
trace.add("response", "result=$result, chatId=$chatId")

// 记录异常
try {
    riskyOperation()
} catch (e: Exception) {
    trace.exception(e)
}

// 使用 catching 安全执行
trace.catching {
    externalApi.call()
}.onFailure { e ->
    logger.error("Failed to call external API", e)
}
```
