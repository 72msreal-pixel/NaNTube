package com.nan.nantube.data

import android.content.Context
import android.content.SharedPreferences

/** Nhớ chỗ đang xem dở của từng video (theo VideoStateController của meetube): xem hết mới phát lại từ đầu. */
object WatchPositions {
    private const val PREFS = "nantube_watch_positions"
    private const val SHORT_MAX_MS = 6 * 60 * 1000L
    private const val ENDED_MS = 3_000L
    private const val FINISHED_MS = 1_000L
    private const val MAX_ENTRIES = 300
    private const val TRIM_COUNT = 50
    private const val SERVER_MAX_PROGRESS = 0.95f

    private class Entry(val positionMs: Long, val durationMs: Long, val savedAt: Long)

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(prefs: SharedPreferences, id: String): Entry? {
        val parts = prefs.getString(id, null)?.split('|') ?: return null
        if (parts.size != 3) return null
        return Entry(parts[0].toLongOrNull() ?: return null, parts[1].toLongOrNull() ?: return null, parts[2].toLongOrNull() ?: 0L)
    }

    fun save(context: Context, id: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        val finished = durationMs - positionMs <= FINISHED_MS
        val position = if (finished) durationMs else positionMs.coerceAtLeast(0L)
        val prefs = prefs(context)
        val edit = prefs.edit().putString(id, "$position|$durationMs|${System.currentTimeMillis()}")
        if (prefs.all.size >= MAX_ENTRIES) {
            prefs.all.keys.mapNotNull { key -> read(prefs, key)?.let { key to it.savedAt } }
                .sortedBy { it.second }.take(TRIM_COUNT).forEach { edit.remove(it.first) }
        }
        edit.apply()
    }

    /** Vị trí nên phát tiếp, 0 nếu phải xem từ đầu (chưa xem, đã xem hết, hoặc video ngắn). */
    fun resumeMs(context: Context, video: VideoItem, durationMs: Long): Long {
        val saved = read(prefs(context), video.id)
        if (saved != null) {
            val ended = saved.durationMs - saved.positionMs < ENDED_MS
            return if (saved.durationMs < SHORT_MAX_MS || ended) 0L else saved.positionMs
        }
        val progress = video.progress
        if (durationMs < SHORT_MAX_MS || progress <= 0f || progress >= SERVER_MAX_PROGRESS) return 0L
        return (durationMs * progress).toLong()
    }
}
