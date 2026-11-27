package com.nan.nantube.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bình luận đã bấm "Lưu" ở menu ba chấm, tách riêng theo từng tài khoản (null = khách).
 * Lưu qua [NanStorage] (tính năng "saved_comments") nên gỡ app cài lại vẫn còn.
 */
object SavedComments {
    private const val FEATURE = "saved_comments"
    private const val LEGACY_PREFS = "nantube_saved_comments"
    private const val LEGACY_KEY = "saved"

    private var appContext: Context? = null
    private var legacyPending = true
    private val cache = HashMap<String, ArrayList<JSONObject>>()

    @Synchronized
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        NanStorage.init(context)
        // Quyền thư mục NaNTube vừa được cấp: đọc lại từ đó (có thể có dữ liệu của lần cài trước).
        NanStorage.addReloadListener { synchronized(this) { cache.clear() } }
    }

    private fun key(account: Account?) = account?.id ?: "guest"

    @Synchronized
    private fun list(account: Account?): ArrayList<JSONObject> = cache.getOrPut(key(account)) {
        val items = ArrayList<JSONObject>()
        val arr = NanStorage.readJson(FEATURE, account) as? JSONArray
        if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { items.add(it) }
        if (legacyPending) {
            // Dữ liệu từ bản cũ (dùng chung mọi tài khoản): giao cho tài khoản đầu tiên được mở, rồi bỏ bản cũ.
            legacyPending = false
            val old = migrateLegacy()
            if (old.isNotEmpty()) {
                items.addAll(old.filter { o -> items.none { it.optString("id") == o.optString("id") } })
                NanStorage.writeJson(FEATURE, account, JSONArray(items))
            }
        }
        items
    }

    private fun migrateLegacy(): List<JSONObject> {
        val prefs = appContext?.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE) ?: return emptyList()
        val raw = prefs.getString(LEGACY_KEY, null) ?: return emptyList()
        val result = ArrayList<JSONObject>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { result.add(it) }
        }
        prefs.edit().remove(LEGACY_KEY).apply()
        return result
    }

    @Synchronized
    fun contains(account: Account?, id: String) = list(account).any { it.optString("id") == id }

    /** Video này có bình luận đã lưu (của tài khoản này): mở video ra thì tự mở bảng bình luận. */
    @Synchronized
    fun hasVideo(account: Account?, videoId: String) = list(account).any { it.optString("video") == videoId }

    /** Đổi trạng thái lưu, trả về true nếu giờ đang được lưu. */
    @Synchronized
    fun toggle(account: Account?, videoId: String, item: CommentItem): Boolean {
        val items = list(account)
        val had = items.removeAll { it.optString("id") == item.id }
        if (!had) {
            items.add(
                0,
                JSONObject().put("id", item.id).put("video", videoId).put("author", item.author).put("text", item.text)
                    .put("savedAt", System.currentTimeMillis())
            )
        }
        NanStorage.writeJson(FEATURE, account, JSONArray(items))
        return !had
    }
}
