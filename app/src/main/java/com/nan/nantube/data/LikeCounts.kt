package com.nan.nantube.data

/**
 * Số thích của Short theo cách meetube (RatingUndo.likeCount + mLikeCountRaw / mLikedInRaw): nhớ con số YouTube trả về và
 * tài khoản này đã thích hay chưa LÚC con số đó được đếm; sau đó thích / bỏ thích thì số hiện ra lệch đúng 1 (110 -> 111).
 * Số đã làm tròn kiểu "1,7 N" thì giữ nguyên như YouTube. Nhớ theo tiến trình nên mở lại màn Short vẫn khớp.
 */
object LikeCounts {
    private class Entry(val raw: String, var base: Boolean? = null)

    private val entries = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > 200
    }

    /** Chữ số, có thể ngăn nhóm 3 số ("133", "1,234", "12 345"); "1,7 N" thì không phải. */
    private val PLAIN = Regex("\\d{1,15}(?:[,.'\\s\u00a0\u202f]\\d{3})*")

    /** Số đã nhớ của video (kèm trạng thái đã đếm), null nếu chưa có. */
    @Synchronized
    fun raw(videoId: String): String? = entries[videoId]?.raw

    /** Nhớ [raw] cho video; đã có số nhớ từ trước thì giữ số đó (để khớp với trạng thái đã đếm) và trả về nó. */
    @Synchronized
    fun remember(videoId: String, raw: String): String {
        entries[videoId]?.let { return it.raw }
        entries[videoId] = Entry(raw)
        return raw
    }

    /** Ghi trạng thái thích mà con số đang nhớ đã đếm (chỉ ghi lần đầu; bấm tim trước đó cũng đã ghi). */
    @Synchronized
    fun noteState(videoId: String, liked: Boolean) {
        val entry = entries[videoId] ?: return
        if (entry.base == null) entry.base = liked
    }

    /** Số thích để hiện theo trạng thái thích hiện tại; chưa biết trạng thái lúc đếm thì giữ nguyên số YouTube trả. */
    @Synchronized
    fun show(videoId: String, likedNow: Boolean): String? {
        val entry = entries[videoId] ?: return null
        val base = entry.base ?: return entry.raw
        return adjust(entry.raw, base, likedNow)
    }

    /** Số thích sau khi lượt thích của tài khoản này đến / đi; [raw] đã tính lượt thích đó khi [likedInRaw]. */
    fun adjust(raw: String, likedInRaw: Boolean, likedNow: Boolean): String {
        if (likedInRaw == likedNow || !PLAIN.matches(raw)) return raw
        val digits = raw.filter { it.isDigit() }
        if (digits.length > 15) return raw
        val count = digits.toLong() + if (likedNow) 1 else -1
        if (count < 0) return raw
        val out = StringBuilder(count.toString())
        val separators = raw.filterNot { it.isDigit() }
        if (separators.isNotEmpty()) {
            var i = out.length - 3
            while (i > 0) {
                out.insert(i, separators[0]) // ngăn nhóm đúng như YouTube
                i -= 3
            }
        }
        return out.toString()
    }
}
