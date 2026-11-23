package com.nan.nantube.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Short đã phát gần đây (như ShortsSeen của meetube): nguồn đề xuất hay trả lặp lại, nên mỗi lần mở app
 * các video nằm trong danh sách này bị loại ra để luôn ra Short mới. Giữ 3000 video, 7 ngày, qua các lần mở app.
 */
object ShortsSeen {
    private const val PREFS = "nantube_shorts_seen"
    private const val KEY = "seen"
    private const val MAX = 3000
    private const val KEEP_MS = 7L * 24 * 60 * 60 * 1000

    private val seen = LinkedHashMap<String, Long>()
    private var prefs: SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val cutoff = System.currentTimeMillis() - KEEP_MS
        for (entry in p.getString(KEY, "").orEmpty().split(',')) {
            val at = entry.substringAfter(':', "").toLongOrNull() ?: continue
            val id = entry.substringBefore(':')
            if (at > cutoff && id.isNotEmpty()) seen.putIfAbsent(id, at)
        }
    }

    @Synchronized
    fun add(id: String?) {
        if (id.isNullOrEmpty()) return
        seen.remove(id)
        seen[id] = System.currentTimeMillis()
        while (seen.size > MAX) seen.remove(seen.keys.first())
        prefs?.edit()?.putString(KEY, seen.entries.joinToString(",") { "${it.key}:${it.value}" })?.apply()
    }

    @Synchronized
    fun contains(id: String?): Boolean = id != null && seen.containsKey(id)
}
