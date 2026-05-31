# CoreAPI.ConfigAPI

配置管理子接口。

## 定义

```kotlin
interface ConfigAPI {
    val settingService: SettingService
    fun jsonStore(kClass: KClass<*>): JsonStore

    // 环境变量
    suspend fun listEnv(type: CoreConfig.JsonConfig.Env.Type): List<String>
    suspend fun getEnv(type: CoreConfig.JsonConfig.Env.Type, id: String): String?
    suspend fun setEnv(env: List<CoreConfig.JsonConfig.Env>)
    suspend fun removeEnv(type: CoreConfig.JsonConfig.Env.Type, id: String)

    // Provider 管理
    fun listProviders(): List<CoreConfig.ProviderConfig.Provider>
    fun listAvailableProviderTypes(): List<String>
    fun getProviderMeta(type: String): LlmClient.ProviderInfo
    fun addProvider(provider: CoreConfig.ProviderConfig.Provider)
    fun removeProvider(id: UUID)
    fun setProviderType(id: UUID, type: String)
    fun setProviderKey(id: UUID, keyName: String)
    fun setProviderUrl(id: UUID, url: Url)
    fun setProviderRule(id: UUID, rules: List<ProviderData.ErrorHandlingRule>)
    fun setProviderDisplayName(id: UUID, displayName: String)

    // Model 管理
    fun listModels(): List<CoreConfig.ProviderConfig.Model>
    fun listModelIds(): List<UUID>
    fun getModelMeta(id: UUID): ModelData.ModelInfo?
    fun addModel(model: CoreConfig.ProviderConfig.Model)
    fun removeModel(id: UUID)
    fun updateModelData(id: UUID, model: CoreConfig.ProviderConfig.Model)

    // API Key 管理
    suspend fun addApiKey(key: CoreConfig.ProviderConfig.ApiKey)
    fun removeApiKey(name: String)
    fun listApiKeyNames(): List<String>
}
```

## 属性

### settingService

```kotlin
val settingService: SettingService
```

设置服务实例，用于读写配置项。

### jsonStore

```kotlin
fun jsonStore(kClass: KClass<*>): JsonStore
```

获取 JSON 键值存储，以类的全限定名作为命名空间。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `kClass` | `KClass<*>` | 用于生成命名空间的类 |

## 环境变量

### listEnv

```kotlin
suspend fun listEnv(type: CoreConfig.JsonConfig.Env.Type): List<String>
```

列出指定类型的所有环境变量 ID。内部使用 `Mutex` 保证并发安全。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `type` | `Env.Type` | 环境变量类型（`BASH_ENV` 或 `CONTAINER_ENV`） |

### getEnv

```kotlin
suspend fun getEnv(type: CoreConfig.JsonConfig.Env.Type, id: String): String?
```

获取环境变量值。

**返回值：** 环境变量值，不存在返回 `null`

### setEnv

```kotlin
suspend fun setEnv(env: List<CoreConfig.JsonConfig.Env>)
```

批量设置环境变量。已存在的 ID 会被更新。

### removeEnv

```kotlin
suspend fun removeEnv(type: CoreConfig.JsonConfig.Env.Type, id: String)
```

删除环境变量。ID 不存在时静默处理。

## Provider 管理

### listProviders

```kotlin
fun listProviders(): List<CoreConfig.ProviderConfig.Provider>
```

列出所有已配置的 Provider。

### listAvailableProviderTypes

```kotlin
fun listAvailableProviderTypes(): List<String>
```

列出所有可用的 Provider 类型（已注册的 LlmClient）。用于确认类型是否可用。

### getProviderMeta

```kotlin
fun getProviderMeta(type: String): LlmClient.ProviderInfo
```

获取 Provider 类型的元数据（支持的模型列表、默认 URL 等）。

**前置校验：**

- 通过 `listAvailableProviderTypes()` 确认类型已注册

**异常：**

| 异常 | 条件 | 预防 |
|------|------|------|
| `IllegalArgumentException("Unknown LLM provider: ...")` | Provider 类型未注册 | 先调用 `listAvailableProviderTypes()` |

### addProvider

```kotlin
fun addProvider(provider: CoreConfig.ProviderConfig.Provider)
```

添加 Provider。

**前置校验：**

- 通过 `listAvailableProviderTypes()` 确认 `type` 已注册
- 通过 `listProviders()` 确保 `displayName` 不重复
- 通过 `listApiKeyNames()` 确认 `keyId` 对应的 API Key 存在

**异常：**

| 异常 | 条件 | 预防 |
|------|------|------|
| `IllegalArgumentException("Unknown LLM provider: ...")` | Provider 类型未注册 | 先调用 `listAvailableProviderTypes()` |
| `IllegalArgumentException("Key ... not found")` | API Key 名称不存在 | 先调用 `listApiKeyNames()` |
| `IllegalArgumentException` | `displayName` 与现有 Provider 重复 | 先调用 `listProviders()` 检查 |

### removeProvider

```kotlin
fun removeProvider(id: UUID)
```

删除 Provider。

**前置校验：**

- 通过 `listProviders()` 确认 `id` 存在

**异常：**

| 异常 | 条件 | 预防 |
|------|------|------|
| `IllegalStateException("ProviderData ... not found")` | Provider ID 不存在 | 先调用 `listProviders()` |

### setProviderType

```kotlin
fun setProviderType(id: UUID, type: String)
```

更新 Provider 类型。

**前置校验：**

- 通过 `listProviders()` 确认 `id` 存在
- 通过 `listAvailableProviderTypes()` 确认新 `type` 已注册

**异常：**

| 异常 | 条件 | 预防 |
|------|------|------|
| `IllegalArgumentException("Unknown LLM provider: ...")` | 新类型未注册 | 先调用 `listAvailableProviderTypes()` |
| `IllegalStateException("ProviderData ... not found")` | Provider ID 不存在 | 先调用 `listProviders()` |

### setProviderKey

```kotlin
fun setProviderKey(id: UUID, keyName: String)
```

更新 Provider 的 API Key。

**前置校验：**

- 通过 `listProviders()` 确认 `id` 存在
- 通过 `listApiKeyNames()` 确认 `keyName` 存在

**异常：**

| 异常 | 条件 | 预防 |
|------|------|------|
| `IllegalStateException("Key ... not found")` | Key 名称不存在 | 先调用 `listApiKeyNames()` |
| `IllegalStateException("ProviderData ... not found")` | Provider ID 不存在 | 先调用 `listProviders()` |

### setProviderUrl

```kotlin
fun setProviderUrl(id: UUID, url: Url)
```

更新 Provider 的 URL。

**前置校验：**

- 通过 `listProviders()` 确认 `id` 存在

**异常：**

| 异常 | 条件 | 预防 |
|------|------|------|
| `IllegalStateException("ProviderData ... not found")` | Provider ID 不存在 | 先调用 `listProviders()` |

### setProviderRule

```kotlin
fun setProviderRule(id: UUID, rules: List<ProviderData.ErrorHandlingRule>)
```

更新 Provider 的错误处理规则。

**前置校验：**

- 通过 `listProviders()` 确认 `id` 存在

**异常：**

| 异常 | 条件 | 预防 |
|------|------|------|
| `IllegalStateException("ProviderData ... not found")` | Provider ID 不存在 | 先调用 `listProviders()` |

### setProviderDisplayName

```kotlin
fun setProviderDisplayName(id: UUID, displayName: String)
```

更新 Provider 的显示名称。

**前置校验：**

- 通过 `listProviders()` 确认 `id` 存在
- 确保 `displayName` 不与其他 Provider 重复

**异常：**

| 异常 | 条件 | 预防 |
|------|------|------|
| `IllegalStateException("ProviderData ... not found")` | Provider ID 不存在 | 先调用 `listProviders()` |
| `IllegalArgumentException` | 显示名称已存在 | 先调用 `listProviders()` 检查 |

## Model 管理

### listModels

```kotlin
fun listModels(): List<CoreConfig.ProviderConfig.Model>
```

列出所有已配置的模型。

### listModelIds

```kotlin
fun listModelIds(): List<UUID>
```

列出所有模型 ID。

### getModelMeta

```kotlin
fun getModelMeta(id: UUID): ModelData.ModelInfo?
```

获取模型元数据。

**返回值：** 模型信息，不存在返回 `null`

### addModel

```kotlin
fun addModel(model: CoreConfig.ProviderConfig.Model)
```

添加模型。

**前置校验：**

- 确保同一 Provider 下 `displayName` 不重复（通过 `listModels()` 检查）

**异常：**

| 异常 | 条件 | 预防 |
|------|------|------|
| `IllegalArgumentException` | 同 Provider 下 `displayName` 重复 | 先调用 `listModels()` 检查 |

### removeModel

```kotlin
fun removeModel(id: UUID)
```

删除模型。ID 不存在时的行为取决于内部实现。

### updateModelData

```kotlin
fun updateModelData(id: UUID, model: CoreConfig.ProviderConfig.Model)
```

更新模型数据。

**前置校验：**

- 通过 `listModels()` 确认 `id` 存在
- 确保更新后的 `displayName` 不与同 Provider 下其他模型重复

**异常：**

| 异常 | 条件 | 预防 |
|------|------|------|
| `IllegalArgumentException` | 同 Provider 下 `displayName` 重复（排除自身） | 先调用 `listModels()` 检查 |

## API Key 管理

> **注意：** 所有 API Key 操作都需要 SecretAPI 已解锁。操作前请确认 `core.secret.isUnlocked.value == true`。

### addApiKey

```kotlin
suspend fun addApiKey(key: CoreConfig.ProviderConfig.ApiKey)
```

添加 API Key。内部通过 `SecretStore` 操作加密存储。

**前置校验：**

- 确认 `SecretAPI.isUnlocked` 为 `true`
- 通过 `listApiKeyNames()` 确保名称不重复

**异常：**

| 异常 | 条件 | 预防 |
|------|------|------|
| `IllegalStateException("Key ... already exists")` | Key 名称已存在 | 先调用 `listApiKeyNames()` |
| `IllegalStateException("SecretManager is locked.")` | 密钥管理器未解锁 | 先调用 `core.secret.unlock()` |

### removeApiKey

```kotlin
fun removeApiKey(name: String)
```

删除 API Key。

**前置校验：**

- 确认 `SecretAPI.isUnlocked` 为 `true`
- 通过 `listApiKeyNames()` 确认名称存在
- 通过 `listProviders()` 确认没有 Provider 正在使用该 Key

**异常：**

| 异常 | 条件 | 预防 |
|------|------|------|
| `IllegalStateException("Key ... not found")` | Key 名称不存在 | 先调用 `listApiKeyNames()` |
| `IllegalStateException("Key ... is currently in use")` | 有 Provider 正在使用该 Key | 先调用 `listProviders()` 检查 `keyId` |
| `IllegalStateException("SecretManager is locked.")` | 密钥管理器未解锁 | 先调用 `core.secret.unlock()` |

### listApiKeyNames

```kotlin
fun listApiKeyNames(): List<String>
```

列出所有 API Key 名称。

## 示例

```kotlin
val config = core.config

// 环境变量
config.setEnv(listOf(
    CoreConfig.JsonConfig.Env("MY_VAR", "value", Env.Type.BASH_ENV)
))
val value = config.getEnv(Env.Type.BASH_ENV, "MY_VAR")

// Provider 管理
val providers = config.listProviders()
val types = config.listAvailableProviderTypes()
config.addProvider(CoreConfig.ProviderConfig.Provider(
    id = UUID.randomUUID(),
    type = "deepseek",
    keyId = "my-key",
    baseUrl = Url("https://api.deepseek.com"),
    displayName = "DeepSeek",
    errorHandlingRules = null
))

// Model 管理
config.addModel(CoreConfig.ProviderConfig.Model(
    data = ModelData(
        id = UUID.randomUUID(),
        displayName = "DeepSeek Chat",
        modelInfo = ModelData.ModelInfo(...),
        providerId = providerId
    )
))

// API Key 管理（需要先解锁）
if (!core.secret.isUnlocked.value) {
    core.secret.unlock("password")
}
config.addApiKey(CoreConfig.ProviderConfig.ApiKey("my-key", "sk-..."))

// 设置服务
val settings = config.settingService
val retries = settings.get(MySettings.MaxRetries())

// JSON 存储
val store = config.jsonStore(MyClass::class)
store.set(buildJsonObject { put("key", JsonPrimitive("value")) })
```
