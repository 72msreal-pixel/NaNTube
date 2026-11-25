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
    const val WATCH_LATER = "WL"

    /** Mã danh sách "Video đã thích". */
    const val LIKED = "LL"

    /** Mã danh sách "Âm thanh" của YouTube (âm thanh Shorts đã lưu). */
    const val SOUNDS = "SE"

    /** Video bất kỳ, chỉ để hỏi YouTube danh sách phát của tài khoản (get_add_to_playlist bắt buộc có videoId). */
    const val PROBE_VIDEO = "dQw4w9WgXcQ"

    /**
     * Mọi danh sách phát của tài khoản; [PlaylistOption.saved] cho biết [videoId] đã có trong đó chưa.
     * Thứ tự như bảng "Lưu vào…" của YouTube: Xem sau, rồi "Âm thanh", rồi các danh sách còn lại. YouTube không trả
     * mục "Âm thanh" thì thêm một dòng cùng mã để vẫn lưu vào được.
     */
    suspend fun options(account: Account?, videoId: String): List<PlaylistOption> {
        val all = rawOptions(account, videoId)
        val sounds = all.firstOrNull { isSoundsList(it) } ?: PlaylistOption(SOUNDS, "Âm thanh", "PRIVATE", false)
        val rest = all.filterNot { isSoundsList(it) }
        val watchLater = rest.firstOrNull()?.takeIf { it.id == WATCH_LATER }
        return if (watchLater != null) listOf(watchLater, sounds) + rest.drop(1) else listOf(sounds) + rest
    }

    /** Mục "Âm thanh" của YouTube (mã SE, hoặc đúng tên này khi app đang chạy tiếng Việt). */
    private fun isSoundsList(option: PlaylistOption): Boolean =
        option.id == SOUNDS || option.title.trim().equals("Âm thanh", ignoreCase = true)

    private suspend fun rawOptions(account: Account?, videoId: String): List<PlaylistOption> {
        requireNotNull(account) { "Hãy đăng nhập để lưu video" }
        // Meetube chỉ gửi videoIds cho get_add_to_playlist; tài khoản mã thử TV rồi WEB+Bearer.
        val withFlag = JSONObject()
            .put("videoIds", JSONArray().put(videoId))
            .put("excludeWatchLater", false)
        val plain = JSONObject().put("videoIds", JSONArray().put(videoId))
        val first = try {
            parseOptions(InnerTube.callAny("playlist/get_add_to_playlist", withFlag, account))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: AuthRejected) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        if (first.isNotEmpty()) return first
        return parseOptions(InnerTube.callAny("playlist/get_add_to_playlist", plain, account))
    }

    suspend fun addToWatchLater(account: Account?, videoId: String) {
        val all = options(account, videoId)
        val watchLater = all.firstOrNull { it.id == WATCH_LATER } ?: all.firstOrNull()
        if (watchLater?.saved == true) return
        add(account, watchLater?.id ?: WATCH_LATER, videoId)
    }

    suspend fun add(account: Account?, playlistId: String, videoId: String) =
        edit(
            account, playlistId,
            JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", videoId)
        )

    /**
     * Bỏ thích video (xóa khỏi "Video đã thích"): dùng đúng thao tác bỏ thích của YouTube (like/removelike);
     * không được thì xóa thẳng khỏi danh sách LL.
     */
    suspend fun unlike(account: Account?, videoId: String) {
        requireNotNull(account) { "Hãy đăng nhập để bỏ thích video" }
        try {
            val body = JSONObject().put("target", JSONObject().put("videoId", videoId))
            val json = InnerTube.callAny("like/removelike", body, account)
            json.optJSONObject("error")?.let { error(it.optString("message").ifBlank { "YouTube không bỏ thích được video" }) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            remove(account, LIKED, videoId)
        }
    }

    suspend fun remove(account: Account?, playlistId: String, videoId: String) =
        edit(
            account, playlistId,
            JSONObject().put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID").put("removedVideoId", videoId)
        )

    /**
     * Tạo danh sách mới ([videoId] null = danh sách trống). Trả mã danh sách mới, hoặc chuỗi rỗng nếu YouTube tạo xong
     * nhưng không trả mã và cũng chưa tìm lại được.
     *
     * Lấy mã danh sách từ phản hồi hoặc tìm lại theo tên nếu YouTube không trả mã.
     * Không báo thành công nếu phản hồi chứa lỗi rõ ràng.
     */
    suspend fun create(account: Account?, title: String, videoId: String?): String {
        requireNotNull(account) { "Hãy đăng nhập để lưu video" }
        val name = title.trim()
        require(name.isNotEmpty()) { "Hãy nhập tiêu đề danh sách phát" }
        val json = InnerTube.createPlaylist(account, name, videoId)
        json.optJSONObject("error")?.let {
            error(it.optString("message").ifBlank { "YouTube không tạo được danh sách phát" })
        }
        val status = json.optString("status")
        check(status.isBlank() || status == "STATUS_SUCCEEDED") { "YouTube không tạo được danh sách phát ($status)" }

        val id = (json.opt("playlistId") as? String)?.takeIf { it.isNotBlank() }
        if (id != null) return id
        // Phản hồi không kèm mã: tìm danh sách vừa tạo theo tên (danh sách mới nằm đầu danh sách của tài khoản).
        val probe = videoId ?: PROBE_VIDEO
        val found = try {
            options(account, probe).firstOrNull {
                it.title.trim().equals(name, ignoreCase = true) && it.id != WATCH_LATER && (videoId == null || it.saved)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return found?.id.orEmpty()
    }

    private suspend fun edit(account: Account?, playlistId: String, action: JSONObject) {
        requireNotNull(account) { "Hãy đăng nhập để lưu video" }
        val body = JSONObject().put("playlistId", playlistId).put("actions", JSONArray().put(action))
        val json = InnerTube.callAny("browse/edit_playlist", body, account)
        // Meetube bỏ qua kết quả. Phản hồi của WEB có status = STATUS_SUCCEEDED, phản hồi của TV có thể không có
        // trường này; chỉ báo lỗi khi YouTube nói rõ là thất bại.
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
