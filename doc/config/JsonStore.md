# JsonStore

JSON 键值存储接口。

## 定义

```kotlin
interface JsonStore {
    fun get(): JsonElement?
    fun set(value: JsonElement)
}
```

## 方法

### get

```kotlin
fun get(): JsonElement?
```

获取存储的 JSON 值。

**返回值：** `JsonElement?`，不存在返回 `null`

**异常：**

| 异常 | 条件 |
|------|------|
| 无 | JSON 解析失败返回 `null`（不抛异常） |

### set

```kotlin
fun set(value: JsonElement)
```

写入 JSON 值。

**副作用：**

- upsert 到 H2 数据库 `json_store` 表

## 获取实例

通过 `CoreAPI.ConfigAPI.jsonStore(kClass)` 获取：

```kotlin
val store = core.config.jsonStore(MyClass::class)
```

**命名空间：** `kClass.java.name`（类的全限定名）

## 存储机制

- **数据库：** H2 嵌入式数据库
- **表名：** `json_store`
- **字段：** `namespace` (PK), `content`
- **初始化：** `JsonStoreImpl.init()` 时创建表

## 并发语义

- `get/set` 操作线程安全
- 数据库操作使用事务
- `set()` 使用 `MERGE INTO` 语句（upsert）

## 示例

```kotlin
// 获取存储实例
val store = core.config.jsonStore(MyPlugin::class)

// 写入数据
val data = buildJsonObject {
    put("name", JsonPrimitive("my-plugin"))
    put("version", JsonPrimitive("1.0.0"))
    put("settings", buildJsonObject {
        put("theme", JsonPrimitive("dark"))
        put("language", JsonPrimitive("zh-CN"))
    })
}
store.set(data)

// 读取数据
val stored = store.get()
if (stored != null) {
    val obj = stored.jsonObject
    println(obj["name"]?.jsonPrimitive?.content)  // "my-plugin"
}

// 检查是否存在
if (store.get() == null) {
    println("未存储数据")
}
```

## 使用场景

- 适配器持久化配置
- 插件状态存储
- 用户偏好设置
- 任意 JSON 数据存储
