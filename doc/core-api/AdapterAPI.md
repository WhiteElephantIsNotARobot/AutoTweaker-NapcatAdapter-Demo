# CoreAPI.AdapterAPI

适配器管理子接口。

## 定义

```kotlin
interface AdapterAPI {
    suspend fun list(): List<AdapterInfo>
    suspend fun start(name: String)
    suspend fun alive(name: String): Boolean
    suspend fun stop(name: String)
}
```

> **v0.1.0-alpha.21 起：** 所有方法均为 `suspend fun`。

## 方法

### list

```kotlin
suspend fun list(): List<AdapterInfo>
```

列出所有已注册的适配器信息。

**返回值：** `List<AdapterInfo>` 适配器信息列表

### start

```kotlin
suspend fun start(name: String)
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

### alive

```kotlin
suspend fun alive(name: String): Boolean
```

检查适配器是否存活。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `name` | `String` | 适配器名称 |

**返回值：** `Boolean` 适配器是否存活

### stop

```kotlin
suspend fun stop(name: String)
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

- `start()` 调用适配器的 `start(core)` 方法
- `stop()` 调用适配器的 `stop()` 方法

## 示例

```kotlin
// 列出所有适配器
val adapters = core.adapter.list()
adapters.forEach { println("${it.name} v${it.version}") }

// 检查适配器是否存活
val isAlive = core.adapter.alive("my-adapter")

// 启动适配器
core.adapter.start("my-adapter")

// 停止适配器
core.adapter.stop("my-adapter")
```
