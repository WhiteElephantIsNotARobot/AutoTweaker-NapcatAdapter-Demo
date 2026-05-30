package io.github.autotweaker.demo.adapter.napcat.permission

/**
 * 用户角色，权限从高到低排列
 *
 * @property level 权限级别数值，数值越大权限越高
 */
enum class Role(val level: Int) {
    /** 管理员：全部权限 + 指定操作员 */
    ADMIN(3),

    /** 操作员：可用默认工作区、管理用户 */
    OPERATOR(2),

    /** 普通用户：必须指定/创建工作区 */
    USER(1)
}
