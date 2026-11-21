package com.nan.nantube.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Journal local: isolated by selected profile (including guest), never sent to YouTube. */
internal class WatchHistory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nan_watch_history", Context.MODE_PRIVATE)
    private fun key(account: Account?) = account?.let { "profile:${it.id}" } ?: "guest"
    private fun rows(account: Account?): List<JSONObject> {
        val array = try { JSONArray(prefs.getString(key(account), "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }
    private fun save(account: Account?, rows: List<JSONObject>) {
        prefs.edit().putString(key(account), JSONArray(rows.take(250)).toString()).apply()
    }
    fun position(account: Account?, video: VideoItem): Long = rows(account).firstOrNull { it.optString("id") == video.id }?.optLong("position") ?: 0L
    fun record(account: Account?, video: VideoItem, position: Long, duration: Long) {
        val progress = if (duration > 0) (position.toDouble() / duration).toFloat().coerceIn(0f, 1f) else 0f
        val row = JSONObject().put("id", video.id).put("title", video.title).put("channel", video.channel)
            .put("duration", video.duration).put("views", video.views).put("short", video.isShort).put("music", video.musicTrack)
            .put("art", video.artwork).put("offline", video.offlineUri).put("progress", progress.toDouble())
            .put("position", if (progress >= .98f || video.isShort) 0L else position.coerceAtLeast(0L))
            .put("time", System.currentTimeMillis())
        save(account, listOf(row) + rows(account).filterNot { it.optString("id") == video.id })
    }
    fun remove(account: Account?, id: String) = save(account, rows(account).filterNot { it.optString("id") == id })
    fun page(account: Account?, query: String = "", remote: VideoPage = VideoPage(emptyList(), null)): VideoPage {
        val byId = remote.items.associateBy { it.id }
        val today = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
        val local = rows(account).mapNotNull { r ->
            fun field(key: String) = r.opt(key) as? String
            val id = field("id") ?: return@mapNotNull null
            val title = field("title") ?: return@mapNotNull null
            val channel = field("channel")
            if (query.isNotBlank() && !title.contains(query, true) && channel?.contains(query, true) != true) return@mapNotNull null
            val section = if (r.optLong("time") >= today) "Hôm nay · NaNTube" else "Đã xem trong NaNTube"
            VideoItem(id, title, channel, field("duration"), field("views"), r.optBoolean("short"), section,
                r.optDouble("progress", 0.0).toFloat(), historyFeedbackToken = byId[id]?.historyFeedbackToken,
                historyPanel = byId[id]?.historyPanel, musicTrack = r.optBoolean("music"), artwork = field("art"),
                offlineUri = field("offline"), localWatch = true)
        }
        val localIds = local.mapTo(HashSet()) { it.id }
        return remote.copy(items = local + remote.items.filterNot { it.id in localIds })
    }
}
