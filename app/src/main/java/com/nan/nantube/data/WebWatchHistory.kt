package com.nan.nantube.data

import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject

/** Lịch sử trên máy cho phiên web. Không dùng chung với khách hoặc tài khoản đăng nhập bằng mã. */
internal object WebWatchHistory {
    private const val LIMIT = 500
    private val cache = mutableMapOf<String, androidx.compose.runtime.MutableState<List<VideoItem>>>()

    private fun key(account: Account) = "web_watch_history:${account.id}"

    // Tất cả thao tác ở luồng chính, giống AccountStore và HistoryFeed.
    private fun entries(account: Account) = cache.getOrPut(account.id) { mutableStateOf(read(account)) }

    private fun read(account: Account): List<VideoItem> = runCatching {
        val arr = JSONArray(AccountStore.libraryPrefs()?.getString(key(account), "[]") ?: "[]")
        (0 until minOf(arr.length(), LIMIT)).mapNotNull { index ->
            val row = arr.optJSONObject(index) ?: return@mapNotNull null
            val id = row.optString("id")
            val title = row.optString("title")
            if (id.length != 11 || title.isBlank()) return@mapNotNull null
            fun text(field: String) = row.optString(field, "").takeIf { !row.isNull(field) && it.isNotBlank() }
            VideoItem(id, title, text("channel"), text("duration"), text("views"), row.optBoolean("short"),
                progress = row.optDouble("progress", 0.0).toFloat().takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f,
                avatar = text("avatar"), music = row.optBoolean("music"), channelId = text("channelId"),
                live = row.optBoolean("live"), reelParams = text("reelParams"), reelPlayerParams = text("reelPlayerParams"))
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    private fun write(account: Account, value: List<VideoItem>) {
        val state = entries(account)
        if (state.value == value) return
        state.value = value
        val json = JSONArray()
        value.forEach { v ->
            json.put(JSONObject().put("id", v.id).put("title", v.title).put("channel", v.channel)
                .put("duration", v.duration).put("views", v.views).put("short", v.isShort)
                .put("progress", v.progress.toDouble()).put("avatar", v.avatar).put("music", v.music)
                .put("channelId", v.channelId).put("live", v.live)
                .put("reelParams", v.reelParams).put("reelPlayerParams", v.reelPlayerParams))
        }
        AccountStore.libraryPrefs()?.edit()?.putString(key(account), json.toString())?.apply()
    }

    fun record(account: Account, video: VideoItem, progress: Float, newPlayback: Boolean) {
        val old = entries(account).value
        val record = video.copy(progress = progress, section = null, historyFeedbackToken = null, historyPanel = null)
        if (newPlayback) {
            write(account, (listOf(record) + old.filterNot { it.id == video.id }).take(LIMIT))
        } else if (old.any { it.id == video.id }) {
            // Nếu người dùng đã xóa mục này, lần lưu tiến độ của cùng phiên không thêm nó trở lại.
            write(account, old.map { if (it.id == video.id) record else it })
        }
    }

    fun remove(account: Account, id: String): Boolean {
        if (account.kind != LoginKind.WEB) return false
        val old = entries(account).value
        val kept = old.filterNot { it.id == id }
        if (old.size == kept.size) return false
        write(account, kept)
        return true
    }

    fun merge(account: Account?, remote: List<VideoItem>, query: String?): List<VideoItem> {
        if (account?.kind != LoginKind.WEB) return remote
        val local = entries(account).value.filter { v ->
            query.isNullOrBlank() || v.title.contains(query, ignoreCase = true) ||
                v.channel?.contains(query, ignoreCase = true) == true
        }
        if (local.isEmpty()) return remote
        val byId = remote.associateBy { it.id }
        val ids = local.mapTo(HashSet()) { it.id }
        return local.map { v ->
            // Giữ thao tác xóa phía YouTube nếu video này cũng có trong FEhistory.
            byId[v.id]?.let { item ->
                v.copy(historyFeedbackToken = item.historyFeedbackToken, historyPanel = item.historyPanel,
                    avatar = v.avatar ?: item.avatar, channelId = v.channelId ?: item.channelId,
                    channel = v.channel ?: item.channel, duration = v.duration ?: item.duration,
                    isShort = v.isShort || item.isShort, music = v.music || item.music)
            } ?: v
        } + remote.filterNot { it.id in ids }
    }
}
