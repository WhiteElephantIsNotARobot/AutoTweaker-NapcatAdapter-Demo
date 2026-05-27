# LlmClient

LLM 聊天客户端接口。

## 定义

```kotlin
interface LlmClient {
    val providerInfo: ProviderInfo

    data class ProviderInfo(
        val name: String,
        val baseUrl: Url,
        val models: List<ModelData.ModelInfo>,
        val errorHandlingRules: List<ProviderData.ErrorHandlingRule>
    )

    suspend fun chat(request: ChatRequest, apiKey: String, baseUrl: Url? = null): Flow<ChatResult>
}
```

## 属性

### providerInfo

```kotlin
val providerInfo: ProviderInfo
```

提供商信息。

## ProviderInfo

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | `String` | 提供商名称（唯一标识） |
| `baseUrl` | `Url` | API 基础 URL |
| `models` | `List<ModelData.ModelInfo>` | 支持的模型列表 |
| `errorHandlingRules` | `List<ProviderData.ErrorHandlingRule>` | 错误处理规则 |

## 方法

### chat

```kotlin
suspend fun chat(request: ChatRequest, apiKey: String, baseUrl: Url? = null): Flow<ChatResult>
```

发起聊天请求。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `request` | `ChatRequest` | 聊天请求 |
| `apiKey` | `String` | API Key |
| `baseUrl` | `Url?` | 覆盖基础 URL（可选） |

**返回值：** `Flow<ChatResult>` 流式聊天结果

**异常：**

| 异常 | 条件 |
|------|------|
| 网络异常 | 连接失败、超时等 |
| API 异常 | 认证失败、配额耗尽等 |

## 注册方式

### 使用 @AutoService（推荐）

```kotlin
@AutoService(LlmClient::class)
class MyLlmClient : LlmClient {
    override val providerInfo = LlmClient.ProviderInfo(
        name = "my-provider",
        baseUrl = Url("https://api.my-provider.com"),
        models = listOf(
            ModelData.ModelInfo(
                modelId = "my-model",
                contextWindow = 128000,
                maxOutputTokens = 4096,
                price = ModelData.ModelData.TokenPrice(...),
                supportsStreaming = true,
                supportsToolCalls = true,
                supportsReasoning = false,
                supportsImage = false,
                supportsJsonOutput = true
            )
        ),
        errorHandlingRules = listOf(
            ProviderData.ErrorHandlingRule(429, RecoveryStrategy.RETRY),
            ProviderData.ErrorHandlingRule(500, RecoveryStrategy.FALLBACK)
        )
    )

    override suspend fun chat(request: ChatRequest, apiKey: String, baseUrl: Url?): Flow<ChatResult> = flow {
        // 实现聊天逻辑
    }
}
```

### 手动 SPI 注册

创建文件 `META-INF/services/io.github.autotweaker.api.llm.LlmClient`：

```
com.example.MyLlmClient
```

## 错误处理规则

```kotlin
data class ErrorHandlingRule(
    val statusCode: Int,
    val strategy: RecoveryStrategy
)
```

### RecoveryStrategy

| 策略 | 说明 |
|------|------|
| `RETRY` | 重试当前模型 |
| `FALLBACK` | 回退到下一个候选模型 |
| `CONTEXT_FALLBACK` | 回退到更大上下文窗口的模型 |
| `PROVIDER_FALLBACK` | 回退到不同提供商的模型 |

## 加载机制

- **内置提供商：** 通过 `ServiceLoader` 从 classpath 加载
- **外部提供商：** 从 `~/.config/autotweaker/plugins/` 加载 JAR
- **优先级：** 外部提供商优先于内置（同名时外部覆盖内置）

## 内置提供商

| 提供商 | 说明 |
|--------|------|
| `deepseek` | DeepSeek API |
| `mimo` | MiMo API |

## 副作用

- 聊天请求通过 `ResilientChat` 封装，支持重试和回退
- 使用量统计通过 `UsageStore` 收集
- 错误处理根据 `ErrorHandlingRule` 自动执行

## 示例

```kotlin
// 获取可用提供商
val types = core.config.listAvailableProviderTypes()
println(types)  // ["deepseek", "mimo", "my-provider"]

// 获取提供商元数据
val meta = core.config.getProviderMeta("my-provider")
println(meta.models.map { it.modelId })  // ["my-model"]

// 使用 CoreAPI 发起聊天
val request = CoreLlmRequest(
    model = modelId,
    messages = listOf(
        ChatMessage.SystemMessage("你是助手", Clock.System.now()),
        ChatMessage.UserMessage("你好", Clock.System.now())
    ),
    stream = true
)

core.chat(request).collect { result ->
    when (val msg = result.result.message) {
        is ChatMessage.AssistantMessage -> {
            print(msg.content)
        }
        is ChatMessage.ErrorMessage -> {
            System.err.println("错误: ${msg.content}")
        }
        else -> {}
    }
}
```
