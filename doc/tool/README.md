# Tool 接口

Agent 工具接口，用于扩展 Agent 的能力。

## 定义

```kotlin
interface Tool {
    val meta: Meta
    suspend fun execute(input: ToolInput): ToolOutput

    data class Meta(...)
    data class Function(...)
    class ToolInput(...)
    data class RuntimeOutput(...)
    data class ToolOutput(...)
}
```

## 属性与方法

### meta

```kotlin
val meta: Meta
```

工具元数据，包含名称、描述、函数列表。

### execute

```kotlin
suspend fun execute(input: ToolInput): ToolOutput
```

执行工具函数。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `input` | `ToolInput` | 工具执行输入 |

**返回值：** `ToolOutput` 执行结果

## Meta

```kotlin
data class Meta(
    val name: String,
    val description: String,
    val functions: List<Function>,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | `String` | 工具名称（唯一标识） |
| `description` | `String` | 工具描述 |
| `functions` | `List<Function>` | 函数列表 |

## Function

```kotlin
data class Function(
    val name: String,
    val description: String,
    val parameters: Map<String, Property>,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | `String` | 函数名称 |
| `description` | `String` | 函数描述 |
| `parameters` | `Map<String, Property>` | 参数定义 |

## Function.Property

```kotlin
data class Property(
    val description: String,
    val required: Boolean,
    val valueType: ValueType
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `description` | `String` | 参数描述 |
| `required` | `Boolean` | 是否必填 |
| `valueType` | `ValueType` | 值类型 |

## ValueType

参数值类型（密封类）：

| 类型 | 说明 |
|------|------|
| `StringValue(enum?)` | 字符串类型，可选枚举值 |
| `NumberValue(enum?)` | 数字类型，可选枚举值 |
| `IntegerValue(enum?)` | 整数类型，可选枚举值 |
| `BooleanValue` | 布尔类型 |
| `ArrayValue(items)` | 数组类型，指定元素类型 |
| `ObjectValue(properties)` | 对象类型，指定属性类型 |

## ToolInput

```kotlin
class ToolInput(
    val functionName: String,
    val arguments: JsonObject,
    val outputChannel: Channel<RuntimeOutput>? = null,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `functionName` | `String` | 要调用的函数名 |
| `arguments` | `JsonObject` | 函数参数 |
| `outputChannel` | `Channel<RuntimeOutput>?` | 流式输出通道 |

**注意：** `ToolInput` 不包含 `SettingService`。需要访问设置服务的工具应通过适配器的 `CoreAPI` 间接访问。

## RuntimeOutput

```kotlin
data class RuntimeOutput(val content: String)
```

流式输出内容。

## ToolOutput

```kotlin
data class ToolOutput(
    val result: String,
    val success: Boolean
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `result` | `String` | 执行结果文本 |
| `success` | `Boolean` | 是否成功 |

## 注册方式

### 使用 @AutoService（推荐）

```kotlin
@AutoService(Tool::class)
class MyTool : Tool {
    override val meta = Tool.Meta(...)
    override suspend fun execute(input: Tool.ToolInput): Tool.ToolOutput = ...
}
```

### 手动 SPI 注册

创建文件 `META-INF/services/io.github.autotweaker.api.tool.Tool`：

```
com.example.MyTool
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

## 示例

```kotlin
@AutoService(Tool::class)
class FileReadTool : Tool {
    companion object {
        // 通过适配器获取设置服务
        private val settings get() = MyAdapter.core.config.settingService
    }

    override val meta = Tool.Meta(
        name = "file-read",
        description = "读取文件内容",
        functions = listOf(
            Tool.Function(
                name = "read",
                description = "读取指定文件",
                parameters = mapOf(
                    "path" to Tool.Function.Property(
                        description = "文件路径",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.StringValue()
                    ),
                    "encoding" to Tool.Function.Property(
                        description = "文件编码",
                        required = false,
                        valueType = Tool.Function.Property.ValueType.StringValue(
                            enum = listOf("utf-8", "ascii", "latin-1")
                        )
                    )
                )
            )
        )
    )

    override suspend fun execute(input: Tool.ToolInput): Tool.ToolOutput {
        val path = input.arguments["path"]?.jsonPrimitive?.content
            ?: return Tool.ToolOutput("缺少 path 参数", false)
        
        val encoding = input.arguments["encoding"]?.jsonPrimitive?.content ?: "utf-8"
        
        return try {
            val content = java.io.File(path).readText(charset(encoding))
            Tool.ToolOutput(content, true)
        } catch (e: Exception) {
            Tool.ToolOutput("读取失败: ${e.message}", false)
        }
    }
}
```
