# Adapter 接口

## 定义

```kotlin
interface Adapter {
    suspend fun load(coreVersion: SemVer): AdapterInfo
    suspend fun start(core: CoreAPI)
    suspend fun stop()
}
```

## 方法

| 方法 | 参数 | 返回值 | 说明 |
|------|------|--------|------|
| `load` | `coreVersion: SemVer` | `AdapterInfo` | 加载适配器，传入核心版本，返回适配器信息 |
| `start` | `core: CoreAPI` | `Unit` | 启动适配器，传入核心 API 实例 |
| `stop` | - | `Unit` | 停止适配器，清理资源 |

> **v0.1.0-alpha.21 起：** 三个方法均为 `suspend fun`，可在实现中直接调用挂起函数。

## 生命周期

```
1. load(coreVersion)  → 返回 AdapterInfo
2. start(core)        → 初始化，保存 CoreAPI 引用
3. [运行中]
4. stop()             → 清理资源
```

## 注册方式

### 1. 实现接口

```kotlin
class MyAdapter : Adapter {
    override suspend fun load(coreVersion: SemVer): AdapterInfo {
        return AdapterInfo(
            name = "my-adapter",
            description = "示例适配器",
            version = SemVer(1, 0, 0),
            source = Url("https://github.com/example/my-adapter")
        )
    }

    override suspend fun start(core: CoreAPI) {
        // 初始化逻辑
    }

    override suspend fun stop() {
        // 清理逻辑
    }
}
```

### 2. SPI 注册

创建文件 `META-INF/services/io.github.autotweaker.api.adapter.Adapter`：

```
com.example.MyAdapter
```

### 3. 使用 Google AutoService（推荐）

```kotlin
@AutoService(Adapter::class)
class MyAdapter : Adapter {
    // ...
}
```

## 访问 CoreAPI

适配器在 `start()` 时接收 `CoreAPI` 实例。推荐通过伴生对象保存引用，供内部工具等组件访问：

```kotlin
class MyAdapter : Adapter {
    companion object {
        lateinit var core: CoreAPI
            private set
    }

    override suspend fun start(core: CoreAPI) {
        this.core = core
    }
}
```

## 异常

| 情况 | 异常 |
|------|------|
| 适配器名称重复 | 后加载的适配器覆盖先加载的 |
| `load()` 抛出异常 | 适配器不会被注册 |
| `start()` 抛出异常 | 适配器注册但未启动 |

## 副作用

- `start()` 时，适配器被添加到内部注册表
- `stop()` 时，适配器从注册表中移除
- 适配器启动后可通过 `CoreAPI.AdapterAPI` 管理
