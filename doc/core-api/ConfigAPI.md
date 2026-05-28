# CoreAPI.ConfigAPI

配置管理子接口。

## 定义

```kotlin
interface ConfigAPI {
    val settingService: SettingService
    fun jsonStore(kClass: KClass<*>): JsonStore

    // 环境变量
    fun listEnv(type: CoreConfig.JsonConfig.Env.Type): List<String>
    fun getEnv(type: CoreConfig.JsonConfig.Env.Type, id: String): String?
    fun setEnv(env: List<CoreConfig.JsonConfig.Env>)
    fun removeEnv(type: CoreConfig.JsonConfig.Env.Type, id: String)

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
    fun addApiKey(key: CoreConfig.ProviderConfig.ApiKey)
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
fun listEnv(type: CoreConfig.JsonConfig.Env.Type): List<String>
```

列出指定类型的所有环境变量 ID。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `type` | `Env.Type` | 环境变量类型（`BASH_ENV` 或 `CONTAINER_ENV`） |

### getEnv

```kotlin
fun getEnv(type: CoreConfig.JsonConfig.Env.Type, id: String): String?
```

获取环境变量值。

**返回值：** 环境变量值，不存在返回 `null`

### setEnv

```kotlin
fun setEnv(env: List<CoreConfig.JsonConfig.Env>)
```

批量设置环境变量。

### removeEnv

```kotlin
fun removeEnv(type: CoreConfig.JsonConfig.Env.Type, id: String)
```

删除环境变量。

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

列出所有可用的 Provider 类型（已注册的 LlmClient）。

### getProviderMeta

```kotlin
fun getProviderMeta(type: String): LlmClient.ProviderInfo
```

获取 Provider 类型的元数据。

**异常：**

| 异常 | 条件 |
|------|------|
| `IllegalArgumentException("Unknown LLM provider: ...")` | Provider 类型未注册 |

### addProvider

```kotlin
fun addProvider(provider: CoreConfig.ProviderConfig.Provider)
```

添加 Provider。

**异常：**

| 异常 | 条件 |
|------|------|
| `IllegalArgumentException("Unknown LLM provider: ...")` | Provider 类型未注册 |
| `IllegalArgumentException("Key ... not found")` | Key 名称不存在 |
| `error("already exists id=...")` | Provider ID 重复 |
| `error("Workspace with name ... already exists")` | Provider 显示名称重复 |

### removeProvider

```kotlin
fun removeProvider(id: UUID)
```

删除 Provider。

**异常：**

| 异常 | 条件 |
|------|------|
| `error("ProviderData ... not found")` | Provider ID 不存在 |

### setProviderType

```kotlin
fun setProviderType(id: UUID, type: String)
```

更新 Provider 类型。

**异常：**

| 异常 | 条件 |
|------|------|
| `IllegalArgumentException("Unknown LLM provider: ...")` | 新类型未注册 |
| `error("ProviderData ... not found")` | Provider ID 不存在 |

### setProviderKey

```kotlin
fun setProviderKey(id: UUID, keyName: String)
```

更新 Provider 的 API Key。

**异常：**

| 异常 | 条件 |
|------|------|
| `error("Key ... not found")` | Key 名称不存在 |
| `error("ProviderData ... not found")` | Provider ID 不存在 |

### setProviderUrl

```kotlin
fun setProviderUrl(id: UUID, url: Url)
```

更新 Provider 的 URL。

**异常：**

| 异常 | 条件 |
|------|------|
| `error("ProviderData ... not found")` | Provider ID 不存在 |

### setProviderRule

```kotlin
fun setProviderRule(id: UUID, rules: List<ProviderData.ErrorHandlingRule>)
```

更新 Provider 的错误处理规则。

**异常：**

| 异常 | 条件 |
|------|------|
| `error("ProviderData ... not found")` | Provider ID 不存在 |

### setProviderDisplayName

```kotlin
fun setProviderDisplayName(id: UUID, displayName: String)
```

更新 Provider 的显示名称。

**异常：**

| 异常 | 条件 |
|------|------|
| `error("ProviderData ... not found")` | Provider ID 不存在 |
| `IllegalArgumentException` | 显示名称已存在 |

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

**异常：**

| 异常 | 条件 |
|------|------|
| `IllegalArgumentException` | 模型显示名称已存在 |
| `error("already exists id=...")` | 模型 ID 重复 |

### removeModel

```kotlin
fun removeModel(id: UUID)
```

删除模型。

### updateModelData

```kotlin
fun updateModelData(id: UUID, model: CoreConfig.ProviderConfig.Model)
```

更新模型数据。

**异常：**

| 异常 | 条件 |
|------|------|
| `IllegalArgumentException` | 模型显示名称已存在（排除自身） |

## API Key 管理

### addApiKey

```kotlin
fun addApiKey(key: CoreConfig.ProviderConfig.ApiKey)
```

添加 API Key。

**异常：**

| 异常 | 条件 |
|------|------|
| `error("Key ... already exists")` | Key 名称已存在 |
| `check("SecretManager is locked.")` | 密钥管理器未解锁 |

### removeApiKey

```kotlin
fun removeApiKey(name: String)
```

删除 API Key。

**异常：**

| 异常 | 条件 |
|------|------|
| `error("Key ... not found")` | Key 名称不存在 |
| `check("SecretManager is locked.")` | 密钥管理器未解锁 |

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

// API Key 管理
config.addApiKey(CoreConfig.ProviderConfig.ApiKey("my-key", "sk-..."))

// 设置服务
val settings = config.settingService
val retries = settings.get(MySettings.MaxRetries())

// JSON 存储
val store = config.jsonStore(MyClass::class)
store.set(buildJsonObject { put("key", JsonPrimitive("value")) })
```
