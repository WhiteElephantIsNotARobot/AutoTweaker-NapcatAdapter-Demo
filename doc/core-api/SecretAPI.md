# CoreAPI.SecretAPI

密钥管理子接口。

## 定义

```kotlin
interface SecretAPI {
    fun isUnlocked(): Boolean
    fun isPasswordEmpty(): Boolean
    fun unlock(password: String)
    fun changePassword(oldPassword: String, newPassword: String)
}
```

## 方法

### isUnlocked

```kotlin
fun isUnlocked(): Boolean
```

检查密钥库是否已解锁。

### isPasswordEmpty

```kotlin
fun isPasswordEmpty(): Boolean
```

检查是否设置了密码（空密码 vs 有密码）。

### unlock

```kotlin
fun unlock(password: String)
```

使用密码解锁密钥库。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `password` | `String` | 解锁密码 |

**异常：**

| 异常 | 条件 |
|------|------|
| `IllegalStateException("Invalid password")` | 密码错误 |
| `IllegalStateException("GPG command failed: ...")` | GPG 命令执行失败 |

**副作用：**

- 若密钥库为空，自动生成 GPG 密钥对
- 设置密码后，后续操作需要先解锁

### changePassword

```kotlin
fun changePassword(oldPassword: String, newPassword: String)
```

修改密码。

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `oldPassword` | `String` | 旧密码 |
| `newPassword` | `String` | 新密码 |

**异常：**

| 异常 | 条件 |
|------|------|
| `IllegalStateException("SecretManager is locked.")` | 未解锁 |
| `IllegalStateException("Invalid password")` | 旧密码错误 |

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
if (!secret.isUnlocked()) {
    secret.unlock("my-password")
}

// 修改密码
secret.changePassword("old-password", "new-password")
```
