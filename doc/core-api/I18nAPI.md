# CoreAPI.I18nAPI

国际化管理子接口。

## 定义

```kotlin
interface I18nAPI {
    val i18nService: I18nService
    fun setTranslationModel(modelId: UUID?)
    fun getTranslationModel(): UUID?
    fun startTranslation()
    fun getTranslationStatus(): StateFlow<TranslationStatus>
}
```

## 属性

### i18nService

```kotlin
val i18nService: I18nService
```

国际化服务实例。

## 方法

### setTranslationModel

```kotlin
fun setTranslationModel(modelId: UUID?)
```

设置翻译使用的模型。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `modelId` | `UUID?` | 模型 ID，`null` 表示使用默认模型 |

### getTranslationModel

```kotlin
fun getTranslationModel(): UUID?
```

获取当前翻译模型 ID。

**返回值：** 模型 ID，未设置返回 `null`

### startTranslation

```kotlin
fun startTranslation()
```

启动翻译任务。

**副作用：**

- 异步执行翻译任务
- 使用配置的翻译模型
- 翻译状态通过 `getTranslationStatus()` 监听

### getTranslationStatus

```kotlin
fun getTranslationStatus(): StateFlow<TranslationStatus>
```

获取翻译状态流。

**返回值：** `StateFlow<TranslationStatus>`

**TranslationStatus 枚举值：**

| 值 | 说明 |
|----|------|
| `IDLE` | 空闲 |
| `TRANSLATING` | 翻译中 |

## 翻译配置

翻译行为可通过 `SettingService` 配置：

| 设置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `TranslateSettings.BatchSize` | `ValInt` | 40 | 每批次翻译条目数 |
| `TranslateSettings.MaxConcurrent` | `ValInt` | 3 | 最大并发批次数 |
| `TranslateSettings.Thinking` | `ValBoolean` | false | 是否启用思考 |
| `TranslateSettings.SystemPrompt` | `ValString` | ... | 翻译系统提示模板 |
| `TranslateSettings.UserPrompt` | `ValString` | ... | 翻译用户提示模板 |

## 示例

```kotlin
val i18n = core.i18n

// 设置翻译模型
i18n.setTranslationModel(modelId)

// 启动翻译
i18n.startTranslation()

// 监听翻译状态
scope.launch {
    i18n.getTranslationStatus().collect { status ->
        when (status) {
            TranslationStatus.IDLE -> println("翻译完成")
            TranslationStatus.TRANSLATING -> println("翻译中...")
        }
    }
}

// 使用国际化服务
val service = i18n.i18nService
service.setLanguage(Locale.CHINA)
val text = service.get(MyI18nDef.SomeText())
```
