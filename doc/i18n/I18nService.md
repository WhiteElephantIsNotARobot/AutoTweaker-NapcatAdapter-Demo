# I18nService

国际化服务接口。

## 定义

```kotlin
interface I18nService {
    fun get(def: I18nDef): String
    fun getDefault(id: String): I18nDef?
    fun set(id: String, text: String, languageCode: Locale)
    fun getAll(): List<I18nEntry>
    fun setLanguage(locale: Locale)
    fun getLanguage(): Locale
}
```

## 方法

### get

```kotlin
fun get(def: I18nDef): String
```

根据定义获取当前语言的翻译文本。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `def` | `I18nDef` | 国际化定义 |

**返回值：** 翻译文本

**语言解析顺序：**

1. 精确匹配当前语言（如 `zh-CN`）
2. 语言匹配（如 `zh`）
3. 英语（`en`）
4. 第一个可用翻译
5. 返回 key（类全限定名）

### getDefault

```kotlin
fun getDefault(id: String): I18nDef?
```

根据 ID 获取国际化定义。

**返回值：** 国际化定义，不存在返回 `null`

### set

```kotlin
fun set(id: String, text: String, languageCode: Locale)
```

设置指定语言的翻译文本。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `id` | `String` | 条目 ID |
| `text` | `String` | 翻译文本 |
| `languageCode` | `Locale` | 语言代码 |

**副作用：**

- 更新内存缓存
- 持久化到 JsonStore

### getAll

```kotlin
fun getAll(): List<I18nEntry>
```

获取所有国际化条目。

**返回值：** `List<I18nEntry>` 包含 key 和 localizations

### setLanguage

```kotlin
fun setLanguage(locale: Locale)
```

设置当前语言。

**副作用：**

- 更新当前语言设置
- 持久化到 JsonStore

### getLanguage

```kotlin
fun getLanguage(): Locale
```

获取当前语言。

## 存储机制

- **存储位置：** JsonStore（命名空间为 `I18nServiceImpl` 的类名）
- **缓存：** 内存缓存
- **初始化：** 首次访问时从 JsonStore 加载

## 并发语义

- `get/set` 操作线程安全（synchronized）
- `setLanguage` 操作线程安全

## 示例

```kotlin
val service = core.i18n.i18nService

// 设置语言
service.setLanguage(Locale.CHINA)

// 获取翻译
val text = service.get(MyI18n.AppName())
println(text)  // "我的应用"

// 动态修改翻译
service.set(
    "com.example.MyI18n\$AppName",
    "我的应用程序",
    Locale.CHINA
)

// 获取所有条目
val entries = service.getAll()
entries.forEach { entry ->
    println("${entry.key}:")
    entry.localizations.forEach { loc ->
        println("  ${loc.languageCode}: ${loc.text}")
    }
}
```
