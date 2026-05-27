# SettingDef

设置项定义接口。

## 定义

```kotlin
interface SettingDef<V : SettingValue> {
    val default: V
    val description: String
}
```

## 属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `default` | `V` | 默认值 |
| `description` | `String` | 设置项描述 |

## 注册方式

### 使用 @AutoService（推荐）

```kotlin
object MySettings {
    @AutoService(SettingDef::class)
    class MaxRetries : SettingDef<SettingValue.ValInt> {
        override val default = SettingValue.ValInt(5)
        override val description = "最大重试次数"
    }
}
```

### 手动 SPI 注册

创建文件 `META-INF/services/io.github.autotweaker.api.config.SettingDef`：

```
com.example.MySettings$MaxRetries
```

## ID 生成规则

SettingDef 的 ID 自动生成为实现类的全限定名：

```kotlin
val id = def::class.qualifiedName  // 例如: "com.example.MySettings$MaxRetries"
```

## SettingValue 类型

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

## 约束

- 实现类必须有无参构造函数（SPI 要求）
- 不允许匿名类（`qualifiedName` 为 null 会抛异常）
- 同一 ID 不允许重复注册

## 异常

| 异常 | 条件 |
|------|------|
| `IllegalStateException("Anonymous SettingDef not allowed: ...")` | 使用匿名类实现 |

## 副作用

- 注册时通过 `ServiceLoader` + `PluginLoader` 自动发现
- 首次 `set()` 时自动持久化到 H2 数据库
- 后续 `get()` 从内存缓存读取

## 内置设置项

### SessionSettings

| 设置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `SystemPrompt` | `ValString` | 从文件加载 | 系统提示词 |

### AgentToolSettings

| 设置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `Cancelled` | `ValString` | "工具调用已取消" | 工具调用被取消时的 ToolResult |
| `Rejected` | `ValString` | "工具调用已被用户拒绝..." | 工具调用被拒绝时的 ToolResult |
| `RejectedWithFeedback` | `ValString` | "工具未被执行，用户拒绝了..." | 工具调用被拒绝并留言时的 ToolResult |
| `PropertyMissing` | `ValString` | "%s工具需要属性：%s" | 缺少属性时的 ToolResult |
| `PropertyError` | `ValString` | "%s工具的属性%s必须为%s类型" | 属性类型错误时的 ToolResult |
| `FunctionNameError` | `ValString` | "%s工具不存在..." | 函数名错误时的 ToolResult |
| `JsonError` | `ValString` | "调用参数不是一个有效的JSON对象：%s" | JSON 解析错误时的 ToolResult |
| `ReasonDescription` | `ValString` | "简要描述调用此工具的目的" | reason 属性描述 |
| `TimeoutSeconds` | `ValInt` | 600 | 工具调用超时时间（秒） |
| `TimeoutMessage` | `ValString` | "工具调用超时（%s秒）" | 超时后的 ToolResult |
| `EnableDescription` | `ValString` | "激活此工具以开始使用..." | 未激活工具的 enable 属性描述 |
| `ActiveMessage` | `ValString` | "工具%s已激活，包含%s个function..." | 激活工具后的 ToolResult |
| `DeactivationThreshold` | `ValInt` | 50 | 连续未使用多少次后自动停用（0=禁用） |

### ResilientChatSettings

| 设置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `MaxRetries` | `ValInt` | 5 | 单轮请求最大重试次数 |
| `LlmChatRetries` | `ValInt` | 3 | 重试/回退策略耗尽后重头开始的最大次数 |
| `RetryBaseDelaySeconds` | `ValInt` | 1 | 重试基础等待时间（秒） |
| `MaxRetryDelaySeconds` | `ValInt` | 60 | 重试最大等待时间（秒） |
| `RetryJitterEnabled` | `ValBoolean` | true | 是否启用随机抖动 |

### BashSettings

| 设置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `Description` | `ValString` | "运行一条bash命令..." | bash 工具描述 |
| `RunFuncDescription` | `ValString` | 从文件加载 | bash_run 函数描述 |
| `DefaultTimeoutSeconds` | `ValInt` | 60 | 默认超时时间 |
| `ResultTemplate` | `ValString` | "命令已执行，退出码：%s..." | 执行结果模板 |

### ContainerSettings

| 设置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `DockerImage` | `ValString` | "buildpack-deps:stable" | Docker 镜像 |
| `ContainerName` | `ValString` | "autotweaker-workspace" | 容器名称 |

## 示例

```kotlin
// 定义设置项
object MySettings {
    @AutoService(SettingDef::class)
    class ApiEndpoint : SettingDef<SettingValue.ValString> {
        override val default = SettingValue.ValString("https://api.example.com")
        override val description = "API 端点"
    }

    @AutoService(SettingDef::class)
    class MaxRetries : SettingDef<SettingValue.ValInt> {
        override val default = SettingValue.ValInt(3)
        override val description = "最大重试次数"
    }
}

// 使用设置项
val settings = core.config.settingService

// 读取（不存在返回默认值）
val endpoint = settings.get(MySettings.ApiEndpoint())
println(endpoint.value)  // "https://api.example.com"

// 写入
settings.set(MySettings.ApiEndpoint(), SettingValue.ValString("https://new-api.example.com"))

// 通过 ID 操作
settings.set("com.example.MySettings\$MaxRetries", SettingValue.ValInt(5))

// 获取所有设置项
val all = settings.getAll()
all.forEach { println("${it.id}: ${it.value} - ${it.description}") }
```
