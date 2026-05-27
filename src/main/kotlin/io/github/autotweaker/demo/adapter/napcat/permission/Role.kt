package io.github.autotweaker.demo.adapter.napcat.permission

/**
 * 用户角色，权限从高到低排列
 */
enum class Role {
    /** 管理员：全部权限 + 指定操作员 */
    ADMIN,

    /** 操作员：可用默认工作区、管理用户 */
    OPERATOR,

    /** 普通用户：必须指定/创建工作区 */
    USER
}
