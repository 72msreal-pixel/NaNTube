package com.nan.nantube.data

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Ghép luồng video và/hoặc âm thanh DASH thành một tệp MP4 (hoặc M4A khi chỉ có âm thanh) không mã hóa lại, bằng cách chép mẫu
 * từ MediaExtractor sang MediaMuxer (như MediaMuxHelper của meetube). Có [Clip] thì chỉ giữ đoạn đó.
 */
object MediaJoiner {
    private const val VIDEO_BUFFER_BYTES = 8 * 1024 * 1024
    private const val AUDIO_BUFFER_BYTES = 1024 * 1024

    /**
     * Đoạn cần giữ (micro giây). Không mã hóa lại nên video cắt ở khung hình khóa tại hoặc ngay trước [startUs] (sớm vài giây là cùng);
     * mọi mốc thời gian được dời để tệp bắt đầu từ 0. [videoPartStartUs] / [audioPartStartUs]: tệp tạm chỉ chứa vài mảnh
     * ([ClipRanges]) bắt đầu ở đâu trong luồng, phòng khi extractor tính từ mẫu đầu tiên của tệp.
     */
    class Clip(val startUs: Long, val endUs: Long, val videoPartStartUs: Long = 0, val audioPartStartUs: Long = 0)

    private class Track(val extractor: MediaExtractor, val format: MediaFormat, bufferBytes: Int) {
        // Mỗi luồng một bộ đệm riêng, cỡ theo mẫu lớn nhất của luồng đó.
        val buffer: ByteBuffer = ByteBuffer.allocateDirect(bufferBytes)
        var muxerTrack = -1
        var finished = false
        var baseUs = 0L
        var endUs = Long.MAX_VALUE
        var endOnKeyframe = false
        var stoppedAtUs = -1L
        var shiftUs = 0L

        /** Đếm từ [partStartUs] khi extractor đếm tệp tạm từ 0. */
        fun placeAt(partStartUs: Long) {
            val first = extractor.sampleTime
            if (partStartUs > 1_000_000 && first >= 0 && first < partStartUs / 2) shiftUs = partStartUs
        }

        /** Thời gian của mẫu theo luồng gốc; -1 khi hết. */
        fun time(): Long {
            val raw = extractor.sampleTime
            return if (raw < 0) raw else raw + shiftUs
        }

        fun seekTo(streamUs: Long) = extractor.seekTo(maxOf(0L, streamUs - shiftUs), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        /** @return false khi luồng đã hết (hoặc tới điểm dừng). */
        fun writeSample(muxer: MediaMuxer, info: MediaCodec.BufferInfo): Boolean {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            val timeUs = time()
            if (size < 0) return false
            val sync = (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
            if (timeUs >= endUs && (sync || !endOnKeyframe)) {
                stoppedAtUs = timeUs
                return false
            }
            if (timeUs < baseUs) {
                // Các khung đầu của GOP mở nằm trước điểm cắt: muxer từ chối mốc thời gian âm.
                extractor.advance()
                return true
            }
            info.set(0, size, timeUs - baseUs, if (sync) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            muxer.writeSampleData(muxerTrack, buffer, info)
            extractor.advance()
            return true
        }
    }

    /** Dạng cũ: ghép nguyên video + âm thanh. */
    fun join(videoFile: File, audioFile: File, output: File) = join(videoFile, audioFile, output, null)

    fun join(videoFile: File?, audioFile: File?, output: File, clip: Clip?) {
        if (videoFile == null && audioFile == null) throw IOException("Không có luồng nào để ghép")
        val tracks = mutableListOf<Track>()
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        try {
            val video = videoFile?.let { open(it, "video/", VIDEO_BUFFER_BYTES) }?.also { tracks += it }
            val audio = audioFile?.let { open(it, "audio/", AUDIO_BUFFER_BYTES) }?.also { tracks += it }
            if (clip != null) {
                video?.placeAt(clip.videoPartStartUs)
                audio?.placeAt(clip.audioPartStartUs)
            }
            tracks.forEach { it.muxerTrack = muxer.addTrack(it.format) }

            var baseUs = 0L
            var endUs = Long.MAX_VALUE
            if (clip != null) {
                baseUs = clip.startUs
                if (video != null) {
                    video.seekTo(clip.startUs)
                    baseUs = maxOf(0L, video.time())
                }
                if (audio != null) {
                    audio.seekTo(baseUs)
                    // Muxer từ chối mốc âm: bỏ phần âm thanh trước khung hình đầu.
                    while (audio.time() in 0 until baseUs) audio.extractor.advance()
                }
                endUs = clip.endUs
            }
            video?.let {
                it.baseUs = baseUs
                it.endUs = endUs
                it.endOnKeyframe = true // khung sau điểm cắt giữa GOP có thể tham chiếu khung đã bỏ
            }
            audio?.let {
                it.baseUs = baseUs
                // Có video thì âm thanh dừng ở chỗ GOP cuối của video dừng (đặt khi video xong).
                it.endUs = if (video != null) Long.MAX_VALUE else endUs
            }

            muxer.start()
            started = true
            val info = MediaCodec.BufferInfo()
            while (true) {
                val next = pickNext(video, audio) ?: break
                if (!next.writeSample(muxer, info)) {
                    next.finished = true
                    if (next === video && audio != null && video.stoppedAtUs >= 0) audio.endUs = video.stoppedAtUs
                }
            }
            muxer.stop()
            started = false
        } finally {
            tracks.forEach { it.extractor.release() }
            try {
                muxer.release()
            } catch (_: RuntimeException) {
                // muxer chưa stop vì lỗi giữa chừng: bỏ qua, lỗi gốc đã được ném
            }
            if (started) output.delete()
        }
    }

    /** Luồng chưa xong có mẫu kế tiếp sớm nhất. */
    private fun pickNext(a: Track?, b: Track?): Track? {
        val aLive = a != null && !a.finished
        val bLive = b != null && !b.finished
        return when {
            aLive && bLive -> if (a!!.time() <= b!!.time()) a else b
            aLive -> a
            bLive -> b
            else -> null
        }
    }

    private fun open(file: File, mimePrefix: String, bufferBytes: Int): Track {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith(mimePrefix) == true) {
                    extractor.selectTrack(index)
                    val maxInput = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
                    return Track(extractor, format, maxOf(bufferBytes, maxInput))
                }
            }
        } catch (e: Exception) {
            extractor.release()
            throw e
        }
        extractor.release()
        throw IOException("Không đọc được luồng ${mimePrefix.trimEnd('/')} của video")
    }
}
