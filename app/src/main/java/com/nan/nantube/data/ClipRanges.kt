package com.nan.nantube.data

/**
 * Port của ClipRanges (meetube, issue #241): các byte mà một luồng MP4 DASH cần để cắt đoạn [startMs, endMs].
 *
 * Luồng MP4 của YouTube gồm phần đầu (ftyp + moov, `initRange`), chỉ mục sidx (`indexRange`) rồi các mảnh (moof + mdat).
 * Mỗi mảnh bắt đầu ở một khung hình khóa và sidx ghi kích thước + thời lượng từng mảnh, nên các mảnh phủ đoạn cần cắt
 * là một dải byte liền nhau. Tệp tạm = phần đầu + các mảnh đó (bỏ sidx vì offset của nó trỏ vào byte không còn trong tệp);
 * mảnh tự mang thời gian giải mã (tfdt) nên bộ ghép vẫn thấy đúng mốc thời gian.
 * Chỉ MP4: WebM (VP9 + Opus) để chỉ mục ở Cues nên vẫn tải nguyên rồi cắt.
 */
internal object ClipRanges {
    /** Ghi [header] trước, rồi nối các byte [from]..[to] của luồng. [startMs] = thời điểm mảnh đầu tiên bắt đầu trong luồng. */
    class Plan(val header: ByteArray, val from: Long, val to: Long, val startMs: Long) {
        fun fileLength(): Long = header.size + (to - from + 1)
    }

    /** "0-740" -> (0, 740); null khi thiếu hoặc không phải dải. */
    fun range(text: String?): LongArray? {
        if (text == null) return null
        val dash = text.indexOf('-')
        if (dash <= 0) return null
        val from = text.substring(0, dash).trim().toLongOrNull() ?: return null
        val to = text.substring(dash + 1).trim().toLongOrNull() ?: return null
        return if (to >= from) longArrayOf(from, to) else null
    }

    /**
     * @param head các byte của luồng từ 0 tới hết `indexRange`
     * @param indexStart nơi hộp sidx bắt đầu
     * @return null khi không có sidx dùng được (khi đó tải nguyên luồng)
     */
    fun plan(head: ByteArray, initEnd: Int, indexStart: Int, startMs: Long, endMs: Long): Plan? {
        if (indexStart < 0 || indexStart + 32 > head.size || initEnd >= indexStart) return null
        var p = indexStart
        val boxSize = u32(head, p)
        if (boxSize < 32 || indexStart + boxSize > head.size ||
            head[p + 4] != 's'.code.toByte() || head[p + 5] != 'i'.code.toByte() ||
            head[p + 6] != 'd'.code.toByte() || head[p + 7] != 'x'.code.toByte()
        ) return null
        val version = head[p + 8].toInt() and 0xFF
        p += 12 + 4 // size, type, version + flags, reference_ID
        val timescale = u32(head, p)
        p += 4
        var time: Long
        val firstOffset: Long
        if (version == 0) {
            time = u32(head, p)
            firstOffset = u32(head, p + 4)
            p += 8
        } else {
            time = u64(head, p)
            firstOffset = u64(head, p + 8)
            p += 16
        }
        val count = ((head[p + 2].toInt() and 0xFF) shl 8) or (head[p + 3].toInt() and 0xFF)
        p += 4
        if (timescale <= 0 || count == 0 || p + 12L * count > indexStart + boxSize) return null

        var offset = indexStart + boxSize + firstOffset
        var from = -1L
        var to = -1L
        var firstMs = 0L
        for (i in 0 until count) {
            val size = u32(head, p) and 0x7FFFFFFFL
            val duration = u32(head, p + 4)
            if ((head[p].toInt() and 0x80) != 0) return null // tham chiếu tới sidx khác: YouTube không gửi kiểu này
            val segStartMs = time * 1000 / timescale
            val segEndMs = (time + duration) * 1000 / timescale
            if (segEndMs > startMs && segStartMs < endMs) {
                if (from < 0) {
                    from = offset
                    firstMs = segStartMs
                }
                to = offset + size - 1
            }
            offset += size
            time += duration
            p += 12
        }
        if (from < 0) return null
        return Plan(head.copyOf(initEnd + 1), from, to, firstMs)
    }

    private fun u32(b: ByteArray, p: Int): Long =
        ((b[p].toLong() and 0xFF) shl 24) or ((b[p + 1].toLong() and 0xFF) shl 16) or
            ((b[p + 2].toLong() and 0xFF) shl 8) or (b[p + 3].toLong() and 0xFF)

    private fun u64(b: ByteArray, p: Int): Long = (u32(b, p) shl 32) or u32(b, p + 4)
}
