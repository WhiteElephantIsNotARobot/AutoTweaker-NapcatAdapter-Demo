# Tool 接口

Agent 工具接口，用于扩展 Agent 的能力。

## 定义

```kotlin
interface Tool<Args : Any> {
    val argsSerializer: KSerializer<Args>
    val name: String
    val description: String

    suspend fun describe(): Map<KProperty1<*, *>, String> = emptyMap()
    suspend fun describeFunctions(): Map<KClass<*>, String> = emptyMap()

    suspend fun execute(input: ToolInput<Args>): ToolOutput
}
```

## 属性

### argsSerializer

```kotlin
val argsSerializer: KSerializer<Args>
```

参数序列化器。使用 `@Serializable` 注解的 data class 的 `serializer()` 即可。

### name

```kotlin
val name: String
```

工具名称（唯一标识）。

### description

```kotlin
val description: String
```

工具描述。

## 方法

### describe

```kotlin
suspend fun describe(): Map<KProperty1<*, *>, String> = emptyMap()
```

为参数属性添加描述。返回属性引用到描述字符串的映射。

### describeFunctions

```kotlin
suspend fun describeFunctions(): Map<KClass<*>, String> = emptyMap()
```

声明多个函数。返回参数类到函数描述的映射。用于一个工具提供多个函数的场景。

### execute

```kotlin
suspend fun execute(input: ToolInput<Args>): ToolOutput
```

执行工具函数。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `input` | `ToolInput<Args>` | 工具执行输入 |

**返回值：** `ToolOutput` 执行结果

## ToolInput

```kotlin
class ToolInput<Args : Any>(
    val args: Args,
    val outputChannel: Channel<RuntimeOutput>? = null,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `args` | `Args` | 类型安全的参数对象 |
| `outputChannel` | `Channel<RuntimeOutput>?` | 流式输出通道 |

## RuntimeOutput

```kotlin
data class RuntimeOutput(val content: String)
```

流式输出内容。

## ToolOutput

```kotlin
data class ToolOutput(
    val result: String,
    val success: Boolean,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `result` | `String` | 执行结果文本 |
| `success` | `Boolean` | 是否成功 |

## 注册方式

### 使用 @AutoService（推荐）

```kotlin
@Serializable
data class MyToolArgs(val input: String)

@AutoService(Tool::class)
class MyTool : Tool<MyToolArgs> {
    override val argsSerializer = MyToolArgs.serializer()
    override val name = "my-tool"
    override val description = "我的工具"

    override suspend fun describe() = mapOf(
        MyToolArgs::input to "输入参数"
    )

    override suspend fun execute(input: Tool.ToolInput<MyToolArgs>): Tool.ToolOutput {
        val value = input.args.input
        return Tool.ToolOutput("结果", true)
    }
}
```

### 手动 SPI 注册

创建文件 `META-INF/services/io.github.autotweaker.api.tool.Tool`：

```
com.example.MyTool
```

## 多函数工具

一个工具可以通过 `describeFunctions()` 提供多个函数。每个函数对应一个 `@Serializable` data class：

```kotlin
@Serializable
data class SendMessageArgs(val userId: Long, val message: String)

@Serializable
data class GetMessageArgs(val messageId: Int)

@Serializable
data class EmptyArgs(val __noop: Unit = Unit) // 无参数函数的占位

@AutoService(Tool::class)
class QqTool : Tool<EmptyArgs> {
    override val argsSerializer = EmptyArgs.serializer()
    override val name = "qq"
    override val description = "QQ 工具"

    override suspend fun describeFunctions() = mapOf(
        SendMessageArgs::class to "发送消息",
        GetMessageArgs::class to "获取消息",
    )

    override suspend fun execute(input: Tool.ToolInput<EmptyArgs>): Tool.ToolOutput {
        // 多函数模式下，Core 会根据 LLM 选择的函数传入对应的 Args 实例
        // 但 execute 的签名仍需一个默认 Args 类型
        // 实际实现中通常通过 outputChannel 或其他方式处理
        return Tool.ToolOutput("ok", true)
    }
}
```

## 流式输出

通过 `outputChannel` 发送运行时输出：

```kotlin
override suspend fun execute(input: Tool.ToolInput<MyToolArgs>): Tool.ToolOutput {
    input.outputChannel?.send(Tool.RuntimeOutput("正在处理..."))
    // 执行操作
    input.outputChannel?.send(Tool.RuntimeOutput("完成"))
    return Tool.ToolOutput("成功", true)
}
```

## 工具激活机制

- 工具默认不激活
- 首次被 LLM 调用时自动激活
- 连续未使用超过阈值（默认 50 次）自动停用
- 未激活的工具以简化形式呈现给 LLM（只有 enable 参数）

## 异常

| 异常 | 条件 |
|------|------|
| 无 | 工具执行异常被捕获，返回 `ToolOutput(error.message, false)` |

## 副作用

- 工具激活/停用会触发 `ToolListUpdate` 输出
- 流式输出通过 `outputChannel` 发送
- 工具执行超时（默认 600 秒）会取消

## 完整示例

```kotlin
@Serializable
data class FileReadArgs(
    val path: String,
    val encoding: String = "utf-8",
)

@AutoService(Tool::class)
class FileReadTool : Tool<FileReadArgs> {
    override val argsSerializer = FileReadArgs.serializer()
    override val name = "file-read"
    override val description = "读取文件内容"

    override suspend fun describe() = mapOf(
        FileReadArgs::path to "文件路径",
        FileReadArgs::encoding to "文件编码（默认 utf-8）"
    )

    override suspend fun execute(input: Tool.ToolInput<FileReadArgs>): Tool.ToolOutput {
        return try {
            val content = java.io.File(input.args.path).readText(charset(input.args.encoding))
            Tool.ToolOutput(content, true)
        } catch (e: Exception) {
            Tool.ToolOutput("读取失败: ${e.message}", false)
        }
    }
}
```
