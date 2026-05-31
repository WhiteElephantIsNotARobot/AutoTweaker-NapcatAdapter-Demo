# LLM 类型

## ChatMessage

聊天消息密封类。

```kotlin
@Serializable
sealed class ChatMessage {
    abstract val content: String?
    abstract val createdAt: Instant
}
```

### 实现类

#### SystemMessage

系统消息。

```kotlin
data class SystemMessage(
    override val content: String,
    override val createdAt: Instant
) : ChatMessage()
```

#### UserMessage

用户消息。

```kotlin
data class UserMessage(
    override val content: String,
    override val createdAt: Instant,
    val pictures: List<Base64>? = null
) : ChatMessage()
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `pictures` | `List<Base64>?` | 图片列表 |

#### AssistantMessage

助手消息。

```kotlin
data class AssistantMessage(
    override val content: String?,
    override val createdAt: Instant,
    val reasoningContent: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val model: String? = null
) : ChatMessage()
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `reasoningContent` | `String?` | 推理内容 |
| `toolCalls` | `List<ToolCall>?` | 工具调用列表 |
| `model` | `String?` | 模型名称 |

#### ToolMessage

工具结果消息。

```kotlin
data class ToolMessage(
    override val content: String,
    override val createdAt: Instant,
    val toolCallId: String
) : ChatMessage()
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `toolCallId` | `String` | 工具调用 ID |

#### ErrorMessage

错误消息。

```kotlin
data class ErrorMessage(
    override val content: String?,
    override val createdAt: Instant,
    val statusCode: Int?,
) : ChatMessage()
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `statusCode` | `Int?` | HTTP 状态码 |

---

## ChatRequest

聊天请求。

```kotlin
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val thinking: Boolean? = null,
    val stream: Boolean = false,
    val maxTokens: Int? = null,
    val tools: List<Tool>? = null,
    val toolCallRequired: Boolean? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val responseFormat: ResponseFormat? = null
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `model` | `String` | 模型 ID |
| `messages` | `List<ChatMessage>` | 消息列表 |
| `thinking` | `Boolean?` | 是否启用推理 |
| `stream` | `Boolean` | 是否流式输出 |
| `maxTokens` | `Int?` | 最大输出 token 数 |
| `tools` | `List<Tool>?` | 工具列表 |
| `toolCallRequired` | `Boolean?` | 是否必须调用工具 |
| `temperature` | `Double?` | 温度 |
| `topP` | `Double?` | Top P |
| `frequencyPenalty` | `Double?` | 频率惩罚 |
| `presencePenalty` | `Double?` | 存在惩罚 |
| `responseFormat` | `ResponseFormat?` | 响应格式 |

### Tool

```kotlin
data class Tool(
    val name: String,
    val description: String,
    val parameters: JsonElement
)
```

### ResponseFormat

```kotlin
@Serializable
data class ResponseFormat(val type: Type)

enum class Type {
    TEXT, JSON_OBJECT
}
```

---

## ChatResult

聊天结果密封类。

```kotlin
sealed class ChatResult {
    abstract val message: ChatMessage?
    abstract val finishReason: FinishReason?
    abstract val usage: Usage?
}
```

### Chunk

流式输出块。

```kotlin
data class Chunk(
    override val message: ChatMessage.AssistantMessage? = null,
    val toolCalls: List<ChunkToolCall>? = null,
    override val finishReason: FinishReason? = null,
    override val usage: Usage? = null,
) : ChatResult()
```

### ChunkToolCall

工具调用片段。

```kotlin
data class ChunkToolCall(
    val index: Int,
    val id: String? = null,
    val name: String? = null,
    val arguments: String? = null,
)
```

### Assembled

组装后的完整结果。

```kotlin
data class Assembled(
    override val message: ChatMessage,
    override val finishReason: FinishReason? = null,
    override val usage: Usage? = null,
) : ChatResult()
```

### FinishReason

```kotlin
data class FinishReason(
    val reason: String,
    val type: Type
)

enum class Type {
    STOP, TOOL, ERROR, FILTER, LENGTH
}
```

| 值 | 说明 |
|----|------|
| `STOP` | 正常停止 |
| `TOOL` | 工具调用 |
| `ERROR` | 错误 |
| `FILTER` | 内容过滤 |
| `LENGTH` | 达到长度限制 |

---

## CoreLlmRequest

核心 LLM 请求。

```kotlin
data class CoreLlmRequest(
    val model: UUID,
    val fallbackModels: List<UUID>?,
    val messages: List<ChatMessage>,
    val tools: List<ChatRequest.Tool>? = null,
    val responseFormat: ChatRequest.ResponseFormat? = null,
    val stream: Boolean = false,
    val thinking: Boolean? = null,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `model` | `UUID` | 模型 ID |
| `fallbackModels` | `List<UUID>?` | 备选模型列表 |
| `messages` | `List<ChatMessage>` | 消息列表 |
| `tools` | `List<ChatRequest.Tool>?` | 工具列表 |
| `responseFormat` | `ChatRequest.ResponseFormat?` | 响应格式 |
| `stream` | `Boolean` | 是否流式输出 |
| `thinking` | `Boolean?` | 是否启用推理 |

---

## CoreLlmResult

核心 LLM 结果。

```kotlin
data class CoreLlmResult(
    val result: ChatResult,
    val model: UUID,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `result` | `ChatResult` | 聊天结果 |
| `model` | `UUID` | 使用的模型 ID |

---

## ModelData

模型数据。

```kotlin
@Serializable
data class ModelData(
    val id: UUID,
    val displayName: String,
    val modelInfo: ModelInfo,
    val providerId: UUID,
    val config: Config? = null,
)
```

### ModelInfo

```kotlin
@Serializable
data class ModelInfo(
    val modelId: String,
    val contextWindow: Int,
    val maxOutputTokens: Int,
    val price: TokenPrice,
    val supportsStreaming: Boolean,
    val supportsToolCalls: Boolean,
    val supportsReasoning: Boolean,
    val supportsImage: Boolean,
    val supportsJsonOutput: Boolean,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `modelId` | `String` | 模型标识符 |
| `contextWindow` | `Int` | 上下文窗口大小 |
| `maxOutputTokens` | `Int` | 最大输出 token 数 |
| `price` | `TokenPrice` | 价格 |
| `supportsStreaming` | `Boolean` | 是否支持流式输出 |
| `supportsToolCalls` | `Boolean` | 是否支持工具调用 |
| `supportsReasoning` | `Boolean` | 是否支持推理 |
| `supportsImage` | `Boolean` | 是否支持图片 |
| `supportsJsonOutput` | `Boolean` | 是否支持 JSON 输出 |

### TokenPrice

```kotlin
@Serializable
data class TokenPrice(
    val inputPrice: List<PriceTier>,
    val outputPrice: List<PriceTier>,
)
```

### PriceTier

```kotlin
@Serializable
data class PriceTier(
    val fromTokens: Int,
    val toTokens: Int? = null,
    val price: Price,
    val cachedPrice: Price? = null
)
```

### Config

```kotlin
@Serializable
data class Config(
    val temperature: Double?,
    val maxTokens: Int?,
    val compactContextUsage: Double?,
    val compactTotalTokens: Double?,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `temperature` | `Double?` | 温度 |
| `maxTokens` | `Int?` | 最大输出 token 数 |
| `compactContextUsage` | `Double?` | 触发压缩的上下文使用率阈值 |
| `compactTotalTokens` | `Double?` | 触发压缩的总 token 数阈值 |

---

## Price

价格。

```kotlin
@Serializable
data class Price(
    val amount: BigDecimal,
    val currency: Currency,
    val tokenUnit: Int
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `amount` | `BigDecimal` | 金额 |
| `currency` | `Currency` | 货币 |
| `tokenUnit` | `Int` | 单位（token 数） |

---

## ProviderData

Provider 数据。

```kotlin
@Serializable
data class ProviderData(
    val id: UUID,
    val displayName: String,
    val providerType: String,
    val apiKey: UUID,
    val baseUrl: Url,
    val errorHandlingRules: List<ErrorHandlingRule>
)
```

### ErrorHandlingRule

```kotlin
@Serializable
data class ErrorHandlingRule(
    val statusCode: Int,
    val strategy: RecoveryStrategy
)
```

### RecoveryStrategy

```kotlin
@Serializable
enum class RecoveryStrategy {
    RETRY, FALLBACK, CONTEXT_FALLBACK, PROVIDER_FALLBACK,
}
```

| 值 | 说明 |
|----|------|
| `RETRY` | 重试当前模型 |
| `FALLBACK` | 回退到下一个候选模型 |
| `CONTEXT_FALLBACK` | 回退到更大上下文窗口的模型 |
| `PROVIDER_FALLBACK` | 回退到不同提供商的模型 |

---

## Usage

使用量。

```kotlin
@Serializable
data class Usage(
    val promptTokens: Int,
    val completionTokens: Int,
    val reasoningTokens: Int? = null,
    val cacheHitTokens: Int? = null,
    val imageTokens: Int? = null,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `promptTokens` | `Int` | 输入 token 数 |
| `completionTokens` | `Int` | 输出 token 数 |
| `reasoningTokens` | `Int?` | 推理 token 数 |
| `cacheHitTokens` | `Int?` | 缓存命中 token 数 |
| `imageTokens` | `Int?` | 图片 token 数 |

### 计算属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `totalTokens` | `Int` | 总 token 数（prompt + completion） |
| `cacheMissTokens` | `Int` | 缓存未命中 token 数 |

### 方法

| 方法 | 参数 | 返回值 | 说明 |
|------|------|--------|------|
| `plus(other)` | `Usage` | `Usage` | 合并使用量 |

### 常量

| 常量 | 说明 |
|------|------|
| `ZERO` | 零使用量 |

---

## UsageSnapshot

使用量快照。

```kotlin
@Serializable
data class UsageSnapshot(
    val usage: Usage,
    val model: ModelData.ModelInfo,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `usage` | `Usage` | 使用量 |
| `model` | `ModelData.ModelInfo` | 模型信息 |
