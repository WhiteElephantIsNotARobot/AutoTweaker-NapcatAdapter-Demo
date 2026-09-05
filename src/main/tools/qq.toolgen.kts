import io.github.autotweaker.toolgen.gen
import io.github.autotweaker.toolgen.tool

/**
 * QQ 工具：让 agent 通过 OneBot 11 操作机器人所在 QQ。
 * 33 个函数，无 defaultFunction（没有能代表 qq 整体的默认操作）。
 */
tool("qq") {
    // ---- 环境感知（只读） ----
    function("get_group_list") {
        boolean("no_cache") { required = false }
    }
    function("get_group_info") {
        long("group_id")
    }
    function("get_group_member_info") {
        long("group_id")
        long("user_id")
    }
    function("get_group_member_list") {
        long("group_id")
    }
    function("get_group_msg_history") {
        long("group_id")
        int("count") { required = false }
    }
    function("get_private_msg_history") {
        long("user_id")
        int("count") { required = false }
    }
    function("get_msg") {
        string("message_id")
    }

    // ---- 消息行动 ----
    function("send_private_msg") {
        long("user_id")
        string("message")
    }
    function("send_group_msg") {
        long("group_id")
        string("message")
    }
    function("delete_msg") {
        string("message_id")
    }

    // ---- 群管理 / 社交（敏感，需审批） ----
    function("set_group_kick") {
        long("group_id")
        long("user_id")
        boolean("reject_add_request") { required = false }
    }
    function("set_group_ban") {
        long("group_id")
        long("user_id")
        long("duration")
    }
    function("set_group_whole_ban") {
        long("group_id")
        boolean("enable")
    }
    function("set_group_admin") {
        long("group_id")
        long("user_id")
        boolean("enable")
    }
    function("set_group_card") {
        long("group_id")
        long("user_id")
        string("card")
    }
    function("set_group_special_title") {
        long("group_id")
        long("user_id")
        string("special_title")
    }
    function("set_group_name") {
        long("group_id")
        string("group_name")
    }
    function("set_group_leave") {
        long("group_id")
    }
    function("group_poke") {
        long("group_id")
        long("user_id")
    }
    function("send_like") {
        long("user_id")
        int("times") { required = false }
    }

    // ---- 内容与 OCR ----
    function("ocr_image") {
        string("image")
    }
    function("get_forward_msg") {
        string("message_id")
    }
    function("get_essence_msg_list") {
        long("group_id")
    }
    function("set_essence_msg") {
        string("message_id")
    }
    function("delete_essence_msg") {
        string("message_id")
    }
    function("send_group_notice") {
        long("group_id")
        string("content")
    }

    // ---- 群文件 ----
    function("get_group_root_files") {
        long("group_id")
    }
    function("get_group_files_by_folder") {
        long("group_id")
        string("folder_id")
    }
    function("get_group_file_url") {
        long("group_id")
        string("file_id")
        long("busid")
    }

    // ---- 文件上传 / 下载 ----
    function("upload_group_file") {
        long("group_id")
        string("file")
        string("name") { required = false }
        string("folder") { required = false }
    }
    function("upload_private_file") {
        long("user_id")
        string("file")
        string("name") { required = false }
    }
    function("download_file") {
        string("url")
        string("file_name") { required = false }
        int("timeout_seconds") { required = false }
    }
}.gen(
    "io.github.autotweaker.demo.adapter.napcat.tool.qq",
    "io.github.autotweaker.demo.adapter.napcat.tool.qq.meta",
)
