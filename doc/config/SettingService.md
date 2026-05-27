# SettingService

设置读写服务接口。

## 定义

```kotlin
interface SettingService {
    fun <V : SettingValue> get(def: SettingDef<V>): V
    fun getDefault(id: String): SettingDef<*>?
    fun <V : SettingValue> set(def: SettingDef<V>, value: V)
    fun set(id: String, value: SettingValue)
    fun setDescription(id: String, description: String)
    fun getAll(): List<SettingEntry>
}
```

## 方法

### get

```kotlin
fun <V : SettingValue> get(def: SettingDef<V>): V
```

根据定义获取设置值。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `def` | `SettingDef<V>` | 设置项定义 |

**返回值：** 设置值，不存在返回 `def.default`

**ID 生成：** `def::class.qualifiedName`

### getDefault

```kotlin
fun getDefault(id: String): SettingDef<*>?
```

根据 ID 获取设置定义。

**返回值：** 设置定义，不存在返回 `null`

### set (by def)

```kotlin
fun <V : SettingValue> set(def: SettingDef<V>, value: V)
```

通过定义设置值。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `def` | `SettingDef<V>` | 设置项定义 |
| `value` | `V` | 设置值 |

**异常：**

| 异常 | 条件 |
|------|------|
| `IllegalArgumentException("Unknown setting: ...")` | ID 未注册 |

**副作用：**

- upsert 到 H2 数据库 `core_settings` 表
- 更新内存缓存

### set (by id)

```kotlin
fun set(id: String, value: SettingValue)
```

通过 ID 设置值。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `id` | `String` | 设置项 ID |
| `value` | `SettingValue` | 设置值 |

**异常：**

| 异常 | 条件 |
|------|------|
| `IllegalArgumentException("Unknown setting: ...")` | ID 未注册 |
| `IllegalArgumentException("Type mismatch for '...': expected ..., got ...")` | 值类型与定义不匹配 |

### setDescription

```kotlin
fun setDescription(id: String, description: String)
```

更新设置项描述。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `id` | `String` | 设置项 ID |
| `description` | `String` | 新描述 |

**异常：**

| 异常 | 条件 |
|------|------|
| `IllegalArgumentException("Unknown setting: ...")` | ID 未注册 |

### getAll

```kotlin
fun getAll(): List<SettingEntry>
```

获取所有设置项。

**返回值：** `List<SettingEntry>` 包含 ID、值、描述

## 存储机制

- **数据库：** H2 嵌入式数据库
- **表名：** `core_settings`
- **字段：** `key_name` (PK), `val_json`, `description`
- **缓存：** `ConcurrentHashMap` 内存缓存
- **初始化：** `Settings.init()` 时创建表并加载缓存

## 并发语义

- `get/set` 操作线程安全（ConcurrentHashMap）
- 数据库操作使用事务
- `getAll()` 合并数据库存储和内存注册表

## 示例

```kotlin
val settings = core.config.settingService

// 读取设置
val retries = settings.get(MySettings.MaxRetries())
println(retries.value)  // 5

// 写入设置
settings.set(MySettings.MaxRetries(), SettingValue.ValInt(10))

// 通过 ID 操作
settings.set("com.example.MySettings\$MaxRetries", SettingValue.ValInt(10))

// 更新描述
settings.setDescription("com.example.MySettings\$MaxRetries", "新的描述")

// 获取所有设置
val all = settings.getAll()
all.forEach { entry ->
    println("${entry.id}: ${entry.value} - ${entry.description}")
}

// 获取设置定义
val def = settings.getDefault("com.example.MySettings\$MaxRetries")
```
