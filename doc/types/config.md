# Config 类型

## CoreConfig

核心配置密封类。

### CoreConfig.JsonConfig.Env

环境变量配置。

```kotlin
data class Env(
    val id: String,
    val value: String,
    val type: Type
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `String` | 环境变量 ID |
| `value` | `String` | 环境变量值 |
| `type` | `Type` | 类型 |

#### Type

```kotlin
enum class Type {
    BASH_ENV, CONTAINER_ENV
}
```

| 值 | 说明 |
|----|------|
| `BASH_ENV` | Shell 环境变量 |
| `CONTAINER_ENV` | 容器环境变量 |

---

### CoreConfig.ProviderConfig.Provider

Provider 配置。

```kotlin
data class Provider(
    val id: UUID,
    val type: String,
    val keyId: String,
    val baseUrl: Url?,
    val displayName: String,
    val errorHandlingRules: List<ProviderData.ErrorHandlingRule>?
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `UUID` | Provider ID |
| `type` | `String` | Provider 类型（如 "deepseek"） |
| `keyId` | `String` | API Key 名称 |
| `baseUrl` | `Url?` | API 基础 URL |
| `displayName` | `String` | 显示名称 |
| `errorHandlingRules` | `List<ErrorHandlingRule>?` | 错误处理规则 |

---

### CoreConfig.ProviderConfig.Model

模型配置。

```kotlin
data class Model(
    val data: ModelData,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `data` | `ModelData` | 模型数据 |

---

### CoreConfig.ProviderConfig.ApiKey

API Key 配置。

```kotlin
data class ApiKey(
    val name: String,
    val key: String,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | `String` | Key 名称 |
| `key` | `String` | Key 值 |

---

## SettingEntry

设置项条目。

```kotlin
@Serializable
data class SettingEntry(
    val id: String,
    val value: SettingValue,
    val description: String
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `String` | 设置项 ID |
| `value` | `SettingValue` | 设置值 |
| `description` | `String` | 描述 |

---

## SettingValue

设置值密封类。

```kotlin
@Serializable
sealed class SettingValue {
    abstract val value: Any?
    abstract fun parse(raw: String): SettingValue
}
```

### 实现类

| 类型 | Kotlin 类型 | 说明 |
|------|-------------|------|
| `ValByte` | `Byte` | 字节 |
| `ValShort` | `Short` | 短整数 |
| `ValInt` | `Int` | 整数 |
| `ValLong` | `Long` | 长整数 |
| `ValFloat` | `Float` | 单精度浮点 |
| `ValDouble` | `Double` | 双精度浮点 |
| `ValBoolean` | `Boolean` | 布尔 |
| `ValChar` | `Char` | 字符 |
| `ValString` | `String` | 字符串 |

### 方法

| 方法 | 参数 | 返回值 | 说明 |
|------|------|--------|------|
| `parse(raw)` | `String` | `SettingValue` | 从字符串解析值 |

### 示例

```kotlin
val intVal = SettingValue.ValInt(42)
val strVal = SettingValue.ValString("hello")
val boolVal = SettingValue.ValBoolean(true)

// 解析
intVal.parse("100")  // ValInt(100)
```
