package com.nan.nantube.data

import org.json.JSONArray
import org.json.JSONObject

/** Một danh sách phát của tài khoản, kèm việc video đang xét đã nằm trong đó chưa. */
data class PlaylistOption(
    val id: String,
    val title: String,
    /** PRIVATE / PUBLIC / UNLISTED (hoặc chữ hiển thị nếu YouTube trả dạng khác). */
    val privacy: String?,
    val saved: Boolean
) {
    val privacyLabel: String?
        get() = when (privacy?.uppercase()) {
            "PRIVATE" -> "Riêng tư"
            "PUBLIC" -> "Công khai"
            "UNLISTED" -> "Không công khai"
            else -> privacy
        }
}

/**
 * Lưu video vào danh sách phát: lấy các danh sách kèm trạng thái "đã lưu chưa", thêm/xóa video, tạo danh sách mới.
 * Cùng luồng với bảng "Lưu vào…" của YouTube (playlist/get_add_to_playlist, browse/edit_playlist, playlist/create)
 * và cùng cách meetube làm: lấy danh sách theo videoId, rồi add/remove từng video.
 */
object PlaylistRepository {

    /** Mọi danh sách phát (gồm Xem sau) của tài khoản; [PlaylistOption.saved] cho biết [videoId] đã có trong đó chưa. */
    suspend fun options(account: Account?, videoId: String): List<PlaylistOption> {
        if (account == null) return emptyList()
        val body = JSONObject()
            .put("videoIds", JSONArray().put(videoId))
            .put("excludeWatchLater", false)
        return parseOptions(InnerTube.call("playlist/get_add_to_playlist", body, account))
    }

    suspend fun add(account: Account?, playlistId: String, videoId: String) =
        edit(
            account, playlistId,
            JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", videoId)
        )

    suspend fun remove(account: Account?, playlistId: String, videoId: String) =
        edit(
            account, playlistId,
            JSONObject().put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID").put("removedVideoId", videoId)
        )

    /** Tạo danh sách mới chứa sẵn [videoId]; [privacy] là PRIVATE / UNLISTED / PUBLIC. Trả mã danh sách mới. */
    suspend fun create(account: Account?, title: String, privacy: String, videoId: String): String {
        val body = JSONObject()
            .put("title", title)
            .put("privacyStatus", privacy)
            .put("videoIds", JSONArray().put(videoId))
        val json = InnerTube.call("playlist/create", body, account)
        return json.optString("playlistId").ifBlank { throw Exception("Không tạo được danh sách phát") }
    }

    private suspend fun edit(account: Account?, playlistId: String, action: JSONObject) {
        val body = JSONObject().put("playlistId", playlistId).put("actions", JSONArray().put(action))
        val json = InnerTube.call("browse/edit_playlist", body, account)
        val status = json.optString("status")
        if (status.isNotBlank() && status != "STATUS_SUCCEEDED") {
            throw Exception("YouTube không cập nhật được danh sách phát")
        }
    }

    private fun parseOptions(root: JSONObject): List<PlaylistOption> {
        val out = LinkedHashMap<String, PlaylistOption>()
        // Dạng chuẩn: mỗi danh sách là một playlistAddToOptionRenderer (containsSelectedVideos = ALL / SOME / NONE).
        for (r in findAllKeys(root, "playlistAddToOptionRenderer")) {
            val id = r.optString("playlistId")
            val title = ytText(r.opt("title"))
            if (id.isBlank() || title == null) continue
            out[id] = PlaylistOption(
                id, title,
                r.optString("privacy").ifBlank { null },
                r.optString("containsSelectedVideos") == "ALL"
            )
        }
        // Dạng view model (bảng mới của YouTube): mỗi dòng là một listItemViewModel có nút bật/tắt.
        if (out.isEmpty()) {
            for (vm in findAllKeys(root, "listItemViewModel")) {
                val id = findKey(vm, "playlistEditEndpoint")?.optString("playlistId").orEmpty()
                val title = ytText(vm.opt("title"))
                if (id.isBlank() || title == null) continue
                out[id] = PlaylistOption(id, title, ytText(vm.opt("subtitle")), isToggled(vm))
            }
        }
        return out.values.toList()
    }

    private fun isToggled(node: Any?): Boolean {
        when (node) {
            is JSONObject -> {
                if (node.optBoolean("isToggled", false)) return true
                val keys = node.keys()
                while (keys.hasNext()) if (isToggled(node.opt(keys.next()))) return true
            }
            is JSONArray -> for (i in 0 until node.length()) if (isToggled(node.opt(i))) return true
        }
        return false
    }
}
