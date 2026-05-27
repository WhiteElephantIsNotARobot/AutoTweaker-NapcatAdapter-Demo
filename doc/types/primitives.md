# 基础类型

## Base64

Base64 编码字符串的值类。

```kotlin
@Serializable
@JvmInline
value class Base64(val value: String)
```

### 约束

- 长度必须是 4 的倍数（空字符串除外）
- 只允许字符：`[A-Za-z0-9+/=]`

### 异常

| 异常 | 条件 |
|------|------|
| `IllegalArgumentException("Invalid Base64 string")` | 构造时违反约束 |

### 方法

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `decode()` | `ByteArray` | 解码为字节数组 |

### 伴生方法

| 方法 | 参数 | 返回值 | 说明 |
|------|------|--------|------|
| `encode(bytes)` | `ByteArray` | `Base64` | 编码字节数组 |
| `isValid(input)` | `String` | `Boolean` | 检查是否有效 |

### 示例

```kotlin
// 构造
val b64 = Base64("YQ==")

// 编码
val encoded = Base64.encode("hello".toByteArray())

// 解码
val decoded = b64.decode()  // ByteArray

// 验证
Base64.isValid("YWJj")  // true
Base64.isValid("abc")   // false
```

---

## SemVer

语义化版本号。

```kotlin
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String> = emptyList(),
    val buildMetadata: List<String> = emptyList()
) : Comparable<SemVer>
```

### 约束

- `major`、`minor`、`patch` 必须 >= 0
- `preRelease` 标识符不允许前导零（除 "0"）
- `preRelease` 和 `buildMetadata` 标识符只允许 `[0-9A-Za-z-]`

### 异常

| 异常 | 条件 |
|------|------|
| `IllegalArgumentException("Major version must be non-negative")` | major < 0 |
| `IllegalArgumentException("Minor version must be non-negative")` | minor < 0 |
| `IllegalArgumentException("Patch version must be non-negative")` | patch < 0 |
| `IllegalArgumentException("Pre-release identifier must not be empty")` | 空标识符 |
| `IllegalStateException("Invalid SemVer: ...")` | `parse()` 格式错误 |

### 方法

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `compareTo(other)` | `Int` | 比较版本号 |
| `toString()` | `String` | 转换为字符串 |

### 伴生方法

| 方法 | 参数 | 返回值 | 说明 |
|------|------|--------|------|
| `parse(text)` | `String` | `SemVer` | 解析版本号字符串 |

### 示例

```kotlin
// 构造
val v1 = SemVer(1, 0, 0)
val v2 = SemVer(1, 0, 0, listOf("alpha", "1"))

// 解析
val v3 = SemVer.parse("1.2.3-beta.1+build.123")

// 比较
v1.compareTo(v2)  // 1 (v1 > v2，因为无 preRelease > 有 preRelease)

// 转换
v3.toString()  // "1.2.3-beta.1+build.123"
```

---

## Unicode

Unicode 转义序列的值类。

```kotlin
@JvmInline
value class Unicode(val value: String)
```

### 约束

- 格式必须是 `\u` + 4位十六进制（如 `A`）
- 长度固定为 6

### 异常

| 异常 | 条件 |
|------|------|
| `IllegalArgumentException("Invalid Unicode escape sequence: ...")` | 格式错误 |

### 方法

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `toChar()` | `Char` | 转换为字符 |
| `codePoint()` | `Int` | 获取码点值 |

### 伴生方法

| 方法 | 参数 | 返回值 | 说明 |
|------|------|--------|------|
| `fromChar(char)` | `Char` | `Unicode` | 从字符创建 |
| `fromCodePoint(codePoint)` | `Int` | `Unicode` | 从码点创建 |
| `isValid(input)` | `String` | `Boolean` | 检查是否有效 |

### 示例

```kotlin
// 构造
val u = Unicode("\\u0041")

// 转换
u.toChar()      // 'A'
u.codePoint()   // 65

// 创建
Unicode.fromChar('A')       // Unicode("\\u0041")
Unicode.fromCodePoint(65)   // Unicode("\\u0041")

// 验证
Unicode.isValid("\\u0041")  // true
Unicode.isValid("abc")      // false
```

---

## Url

URL 值类。

```kotlin
@JvmInline
@Serializable
value class Url private constructor(val value: String)
```

### 约束

- 必须是 `http` 或 `https` 协议
- 必须是绝对路径
- 自动 trim 空格和尾部 `/`

### 异常

| 异常 | 条件 |
|------|------|
| `IllegalArgumentException("URL must not be blank")` | 空白字符串 |
| `IllegalArgumentException("Invalid URL: ...")` | 格式错误、非 http/https、相对路径 |

### 伴生方法

| 方法 | 参数 | 返回值 | 说明 |
|------|------|--------|------|
| `invoke(raw)` | `String` | `Url` | 构造 URL |

### 示例

```kotlin
// 构造
val url = Url("https://api.example.com/v1")

// 自动处理
Url("https://example.com/")     // "https://example.com"
Url("  https://example.com  ")  // "https://example.com"

// 异常
Url("")           // IllegalArgumentException("URL must not be blank")
Url("ftp://...")  // IllegalArgumentException("Invalid URL")
Url("/relative")  // IllegalArgumentException("Invalid URL")
```
