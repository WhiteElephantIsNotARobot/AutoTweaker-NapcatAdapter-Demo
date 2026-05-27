# I18nDef

国际化条目定义接口。

## 定义

```kotlin
interface I18nDef {
    val localizations: List<LocalizedString>
}
```

## 属性

### localizations

```kotlin
val localizations: List<LocalizedString>
```

多语言翻译列表。

## 注册方式

### 使用 @AutoService（推荐）

```kotlin
object MyI18n {
    @AutoService(I18nDef::class)
    class Greeting : I18nDef {
        override val localizations = listOf(
            LocalizedString(Locale.ENGLISH, "Hello"),
            LocalizedString(Locale.CHINA, "你好"),
            LocalizedString(Locale.JAPAN, "こんにちは")
        )
    }
}
```

### 手动 SPI 注册

创建文件 `META-INF/services/io.github.autotweaker.api.i18n.I18nDef`：

```
com.example.MyI18n$Greeting
```

## ID 生成规则

I18nDef 的 ID 自动生成为实现类的全限定名：

```kotlin
val key = def::class.qualifiedName  // 例如: "com.example.MyI18n$Greeting"
```

## LocalizedString

```kotlin
data class LocalizedString(
    val languageCode: Locale,
    val text: String
)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `languageCode` | `Locale` | 语言代码 |
| `text` | `String` | 翻译文本 |

## 约束

- 实现类必须有无参构造函数（SPI 要求）
- 不允许匿名类（`qualifiedName` 为 null 会抛异常）

## 异常

| 异常 | 条件 |
|------|------|
| `IllegalStateException("Anonymous I18nDef not allowed: ...")` | 使用匿名类实现 |

## 副作用

- 注册时通过 `ServiceLoader` + `PluginLoader` 自动发现
- 翻译数据存储在 JsonStore 中
- 可通过 `I18nService` 动态修改翻译

## 示例

```kotlin
object MyI18n {
    @AutoService(I18nDef::class)
    class AppName : I18nDef {
        override val localizations = listOf(
            LocalizedString(Locale.ENGLISH, "My Application"),
            LocalizedString(Locale.CHINA, "我的应用")
        )
    }

    @AutoService(I18nDef::class)
    class WelcomeMessage : I18nDef {
        override val localizations = listOf(
            LocalizedString(Locale.ENGLISH, "Welcome, %s!"),
            LocalizedString(Locale.CHINA, "欢迎，%s！")
        )
    }
}

// 使用
val service = core.i18n.i18nService
service.setLanguage(Locale.CHINA)
val text = service.get(MyI18n.AppName())  // "我的应用"
```
