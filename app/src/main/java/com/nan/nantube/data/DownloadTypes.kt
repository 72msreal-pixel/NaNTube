package com.nan.nantube.data

import java.util.Locale

/** Loại âm thanh khi tải riêng âm thanh. M4A là luồng AAC của YouTube giữ nguyên; MP3 được giải mã rồi mã hóa lại bằng LAME. */
enum class AudioFormat(val label: String, val ext: String, val mime: String) {
    M4A("M4A", "m4a", "audio/mp4"),
    MP3("MP3", "mp3", "audio/mpeg")
}

/** Một mức đồ họa tải được: chiều cao và dung lượng video + âm thanh (-1 nếu không rõ). */
class QualityOption(val height: Int, val bytes: Long) {
    val label: String get() = "${height}p"
}

/** Thông tin của video để dựng bảng tải: các mức đồ họa, dung lượng âm thanh, độ dài (ms, -1 nếu không rõ). */
class DownloadInfo(val qualities: List<QualityOption>, val audioBytes: Long, val lengthMs: Long)

/**
 * Một yêu cầu tải từ bảng Tải xuống.
 * [audioOnly] = tải âm thanh; ngược lại tải video ở [height] (null = mức cao nhất tải được).
 * [clipEndMs] > [clipStartMs] = chỉ tải đoạn đó (tải đoạn video).
 */
data class DownloadRequest(
    val audioOnly: Boolean = false,
    val height: Int? = null,
    val audioFormat: AudioFormat = AudioFormat.M4A,
    val clipStartMs: Long = 0,
    val clipEndMs: Long = 0
) {
    val isClip: Boolean get() = clipEndMs > clipStartMs
}

object DownloadText {
    /** "90", "1:30" hoặc "1:01:30" -> ms; -1 khi không phải thời gian (như DownloadOptions.parseTime của meetube). */
    fun parseTime(text: CharSequence?): Long {
        val value = text?.toString()?.trim().orEmpty()
        if (value.isEmpty()) return -1
        val parts = value.split(":")
        if (parts.size > 3) return -1
        var seconds = 0L
        for ((i, raw) in parts.withIndex()) {
            val part = raw.trim()
            if (!part.matches(Regex("[0-9]{1,5}")) || (i > 0 && (part.length > 2 || part.toInt() > 59))) return -1
            seconds = seconds * 60 + part.toLong()
        }
        return seconds * 1000
    }

    /** ms -> "1:05" hoặc "1:01:05". */
    fun duration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    /** "1:20 to 3:45" như tên tệp đoạn của meetube, nhưng dùng dấu chấm cho hợp tên tệp. */
    fun clipLabel(startMs: Long, endMs: Long): String =
        duration(startMs).replace(':', '.') + " to " + duration(endMs).replace(':', '.')

    fun bytes(bytes: Long): String {
        if (bytes < 0) return ""
        if (bytes < 1000) return "$bytes B"
        var value = bytes.toDouble()
        val units = arrayOf("kB", "MB", "GB")
        var unit = -1
        while (value >= 1000 && unit < units.size - 1) {
            value /= 1000
            unit++
        }
        return String.format(Locale.US, if (value >= 100) "%.0f %s" else "%.1f %s", value, units[unit])
    }
}
