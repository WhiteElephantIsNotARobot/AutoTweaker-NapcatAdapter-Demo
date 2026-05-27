# Adapter 类型

## AdapterInfo

适配器信息。

```kotlin
data class AdapterInfo(
    val name: String,
    val description: String,
    val version: SemVer,
    val source: Url,
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | `String` | 适配器名称（唯一标识） |
| `description` | `String` | 适配器描述 |
| `version` | `SemVer` | 版本号 |
| `source` | `Url` | 源码地址 |
