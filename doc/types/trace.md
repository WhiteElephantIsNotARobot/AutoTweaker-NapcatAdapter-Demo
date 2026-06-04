# Trace 类型

## TraceRecorder

追踪记录器接口，用于记录追踪信息。

```kotlin
interface TraceRecorder {
    suspend fun add(namespace: String, content: String)
}
```

### add

```kotlin
suspend fun add(namespace: String, content: String)
```

添加一条追踪记录。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `namespace` | `String` | 命名空间，用于分类追踪记录 |
| `content` | `String` | 追踪内容 |

**示例：**

```kotlin
val recorder = core.trace(MyAdapter::class)
recorder.add("session", "用户登录成功")
recorder.add("llm", "请求发送到模型")
```
