# CoreAPI.SecretAPI

密钥管理子接口。

## 定义

```kotlin
interface SecretAPI {
    val isUnlocked: StateFlow<Boolean>
    fun isPasswordEmpty(): Boolean
    suspend fun unlock(password: String)
    suspend fun changePassword(oldPassword: String, newPassword: String)
}
```

## 属性

### isUnlocked

```kotlin
val isUnlocked: StateFlow<Boolean>
```

密钥库解锁状态流。`true` 表示已解锁，`false` 表示未解锁。

> **重要：** `ConfigAPI` 中的 `addApiKey()` 和 `removeApiKey()` 操作要求密钥库已解锁。调用前必须检查此状态。

## 方法

### isPasswordEmpty

```kotlin
fun isPasswordEmpty(): Boolean
```

检查是否设置了密码（空密码 vs 有密码）。可用于判断是否需要首次设置密码。

### unlock

```kotlin
suspend fun unlock(password: String)
```

使用密码解锁密钥库。内部使用 `Dispatchers.IO` 执行 GPG 命令。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `password` | `String` | 解锁密码 |

**异常：**

| 异常 | 条件 | 预防 |
|------|------|------|
| `IllegalStateException("Invalid password")` | 密码错误 | 提示用户重新输入 |
| `IllegalStateException("GPG command failed: ...")` | GPG 命令执行失败 | 检查 GPG 安装和配置 |

**副作用：**

- 若密钥库为空，自动生成 GPG 密钥对
- 设置密码后，后续操作需要先解锁

### changePassword

```kotlin
suspend fun changePassword(oldPassword: String, newPassword: String)
```

修改密码。内部使用 `Mutex` 保证并发安全。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `oldPassword` | `String` | 旧密码 |
| `newPassword` | `String` | 新密码 |

**前置校验：**

- 确认 `isUnlocked.value == true`

**异常：**

| 异常 | 条件 | 预防 |
|------|------|------|
| `IllegalStateException("SecretManager is locked.")` | 未解锁 | 先调用 `unlock()` |
| `IllegalStateException("Invalid password")` | 旧密码错误 | 提示用户确认旧密码 |

**副作用：**

- 删除旧 GPG 密钥
- 生成新 GPG 密钥
- 重新加密所有已存储的密钥

## 实现细节

密钥库使用 GPG 加密存储：

- 密钥存储位置：`~/.config/autotweaker/secret/secrets/`
- GPG 配置位置：`~/.config/autotweaker/secret/.gnupg/`
- 验证文件：`~/.config/autotweaker/secret/.verify`

## 示例

```kotlin
val secret = core.secret

// 检查状态
if (!secret.isUnlocked.value) {
    secret.unlock("my-password")
}

// 监听解锁状态变化
secret.isUnlocked.collect { unlocked ->
    println("Secret unlocked: $unlocked")
}

// 修改密码
secret.changePassword("old-password", "new-password")
```
