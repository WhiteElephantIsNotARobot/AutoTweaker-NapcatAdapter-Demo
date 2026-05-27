# CoreAPI.AdapterAPI

适配器管理子接口。

## 定义

```kotlin
interface AdapterAPI {
    fun listAdapter(): List<AdapterInfo>
    fun startAdapter(name: String)
    fun stopAdapter(name: String)
}
```

## 方法

### listAdapter

```kotlin
fun listAdapter(): List<AdapterInfo>
```

列出所有已注册的适配器信息。

**返回值：** `List<AdapterInfo>` 适配器信息列表

### startAdapter

```kotlin
fun startAdapter(name: String)
```

按名称启动适配器。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `name` | `String` | 适配器名称（`AdapterInfo.name`） |

**异常：**

| 异常 | 条件 |
|------|------|
| `IllegalStateException("Unknown adapter: ...")` | 适配器名称不存在 |

### stopAdapter

```kotlin
fun stopAdapter(name: String)
```

按名称停止适配器。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `name` | `String` | 适配器名称 |

**异常：**

| 异常 | 条件 |
|------|------|
| `IllegalStateException("Unknown adapter: ...")` | 适配器名称不存在 |

## 副作用

- `startAdapter()` 调用适配器的 `start(core)` 方法
- `stopAdapter()` 调用适配器的 `stop()` 方法

## 示例

```kotlin
// 列出所有适配器
val adapters = core.adapter.listAdapter()
adapters.forEach { println("${it.name} v${it.version}") }

// 启动适配器
core.adapter.startAdapter("my-adapter")

// 停止适配器
core.adapter.stopAdapter("my-adapter")
```
