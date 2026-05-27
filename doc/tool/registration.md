# 工具注册与执行机制

## 注册方式

通过 SPI 自动发现：

```kotlin
@AutoService(Tool::class)
class MyTool : Tool {
    override val meta = Tool.Meta(...)
    override suspend fun execute(input: Tool.ToolInput): Tool.ToolOutput = ...
}
```

**加载路径：**

1. `ServiceLoader.load(Tool::class.java)` - 从 classpath 加载
2. 外部插件从 `~/.config/autotweaker/plugins/` 加载 JAR

## 优先级

- 外部插件优先于内置组件（同名时外部覆盖内置）
- 工具名称去重（保留第一个）

## 激活机制

### 初始状态

- 工具默认不激活（`active = false`）
- 以简化形式呈现给 LLM（只有 `enable` 参数）

### 自动激活

- LLM 首次调用工具时自动激活
- 激活后返回完整函数列表给 LLM

### 自动停用

- 连续未使用超过阈值（默认 50 次）自动停用
- 停用后重新以简化形式呈现
- 可通过 `AgentToolSettings.DeactivationThreshold` 配置阈值（0 = 禁用）

## 执行流程

```
1. LLM 返回工具调用
2. 验证工具调用参数
3. 检查工具是否激活
   - 未激活：激活工具，返回激活消息
   - 已激活：执行工具
4. 执行工具（Tool.execute）
5. 返回 ToolOutput
```

## 工具审批

工具调用需要用户审批时：

1. Agent 状态转为 `WAITING`
2. 发送 `ToolRequest` 输出
3. 用户调用 `approveToolCall()` 审批
4. 继续执行或拒绝

## 访问设置服务

`ToolInput` 不包含 `SettingService`。需要访问设置服务的工具应通过适配器的 `CoreAPI` 间接访问：

```kotlin
class MyAdapter : Adapter {
    companion object {
        lateinit var core: CoreAPI
            private set
    }

    override fun start(core: CoreAPI) {
        this.core = core
    }
}

@AutoService(Tool::class)
class MyTool : Tool {
    override suspend fun execute(input: Tool.ToolInput): Tool.ToolOutput {
        val settings = MyAdapter.core.config.settingService
        val value = settings.get(MySettings.SomeSetting())
        // ...
    }
}
```

## 流式输出

工具可以通过 `outputChannel` 发送流式输出：

```kotlin
override suspend fun execute(input: Tool.ToolInput): Tool.ToolOutput {
    val channel = input.outputChannel
    channel?.send(Tool.RuntimeOutput("开始处理..."))
    channel?.send(Tool.RuntimeOutput("进度 50%"))
    channel?.send(Tool.RuntimeOutput("完成"))
    return Tool.ToolOutput("执行成功", true)
}
```

## 超时处理

- 默认超时：600 秒（可通过 `AgentToolSettings.TimeoutSeconds` 配置）
- 超时后工具执行被取消
- 返回超时消息（可通过 `AgentToolSettings.TimeoutMessage` 配置）

## 错误处理

工具执行异常被捕获，返回错误信息：

```kotlin
try {
    // 工具逻辑
} catch (e: Exception) {
    Tool.ToolOutput(e.message ?: "Unknown error", false)
}
```

## 示例：完整工具实现

```kotlin
@AutoService(Tool::class)
class CalculatorTool : Tool {
    companion object {
        private val settings get() = MyAdapter.core.config.settingService
    }

    override val meta = Tool.Meta(
        name = "calculator",
        description = "计算器工具",
        functions = listOf(
            Tool.Function(
                name = "add",
                description = "加法",
                parameters = mapOf(
                    "a" to Tool.Function.Property(
                        description = "第一个数",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.NumberValue()
                    ),
                    "b" to Tool.Function.Property(
                        description = "第二个数",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.NumberValue()
                    )
                )
            ),
            Tool.Function(
                name = "multiply",
                description = "乘法",
                parameters = mapOf(
                    "a" to Tool.Function.Property(
                        description = "第一个数",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.NumberValue()
                    ),
                    "b" to Tool.Function.Property(
                        description = "第二个数",
                        required = true,
                        valueType = Tool.Function.Property.ValueType.NumberValue()
                    )
                )
            )
        )
    )

    override suspend fun execute(input: Tool.ToolInput): Tool.ToolOutput {
        val a = input.arguments["a"]?.jsonPrimitive?.double
            ?: return Tool.ToolOutput("缺少参数 a", false)
        val b = input.arguments["b"]?.jsonPrimitive?.double
            ?: return Tool.ToolOutput("缺少参数 b", false)

        val result = when (input.functionName) {
            "add" -> a + b
            "multiply" -> a * b
            else -> return Tool.ToolOutput("未知函数: ${input.functionName}", false)
        }

        return Tool.ToolOutput(result.toString(), true)
    }
}
```
