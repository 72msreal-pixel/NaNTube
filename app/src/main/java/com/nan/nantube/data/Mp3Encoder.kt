package com.nan.nantube.data

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.naman14.androidlame.AndroidLame
import com.naman14.androidlame.LameBuilder
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteOrder

/**
 * Âm thanh -> MP3. Android không có bộ mã hóa MP3 nên luồng AAC của YouTube được giải mã bằng MediaCodec ra PCM,
 * rồi LAME (thư viện TAndroidLame) mã hóa. Cắt đoạn chính xác tới từng mẫu vì làm trên PCM.
 */
internal object Mp3Encoder {
    private const val BITRATE_KBPS = 192
    private const val TIMEOUT_US = 10_000L

    /**
     * @param source tệp M4A/MP4 chứa luồng âm thanh
     * @param clipStartUs / [clipEndUs] đoạn cần giữ (micro giây theo luồng gốc); [clipEndUs] <= [clipStartUs] = lấy hết
     * @param partStartUs tệp tạm chỉ chứa vài mảnh bắt đầu ở đâu trong luồng (xem [MediaJoiner.Clip])
     */
    fun encode(source: File, output: File, clipStartUs: Long, clipEndUs: Long, partStartUs: Long) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var lame: AndroidLame? = null
        try {
            extractor.setDataSource(source.absolutePath)
            var trackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = i
                    inputFormat = format
                    break
                }
            }
            if (trackIndex < 0 || inputFormat == null) throw IOException("Không đọc được luồng âm thanh của video")
            extractor.selectTrack(trackIndex)

            val clip = clipEndUs > clipStartUs
            var shiftUs = 0L
            val first = extractor.sampleTime
            if (clip && partStartUs > 1_000_000 && first >= 0 && first < partStartUs / 2) shiftUs = partStartUs
            if (clip) extractor.seekTo(maxOf(0L, clipStartUs - shiftUs), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val codec = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
            decoder = codec
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var channels = inputFormat.optInt(MediaFormat.KEY_CHANNEL_COUNT, 2)
            var sampleRate = inputFormat.optInt(MediaFormat.KEY_SAMPLE_RATE, 44100)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            output.outputStream().buffered().use { out ->
                fun ensureLame(): AndroidLame {
                    lame?.let { return it }
                    if (channels !in 1..2) throw IOException("Chưa hỗ trợ âm thanh $channels kênh khi đổi sang MP3")
                    val built = AndroidLame(
                        LameBuilder()
                            .setInSampleRate(sampleRate)
                            .setOutChannels(channels)
                            .setOutBitrate(BITRATE_KBPS)
                            .setOutSampleRate(sampleRate)
                            .setQuality(2)
                    )
                    lame = built
                    return built
                }

                while (!outputDone) {
                    if (!inputDone) {
                        val index = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (index >= 0) {
                            val buffer = codec.getInputBuffer(index)!!
                            val size = extractor.readSampleData(buffer, 0)
                            val time = extractor.sampleTime
                            val past = clip && time >= 0 && time + shiftUs >= clipEndUs
                            if (size < 0 || past) {
                                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(index, 0, size, time + shiftUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    when {
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val format = codec.outputFormat
                            channels = format.optInt(MediaFormat.KEY_CHANNEL_COUNT, channels)
                            sampleRate = format.optInt(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        }
                        index >= 0 -> {
                            if (info.size > 0) {
                                writePcm(codec, index, info, ensureLame(), channels, sampleRate, clipStartUs, clipEndUs, clip, out)
                            }
                            codec.releaseOutputBuffer(index, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                        }
                    }
                }
                val encoder = ensureLame()
                val tail = ByteArray(7200)
                val written = flush(encoder, tail)
                if (written > 0) out.write(tail, 0, written)
            }
        } finally {
            lame?.let { close(it) }
            try { decoder?.stop() } catch (_: RuntimeException) {}
            try { decoder?.release() } catch (_: RuntimeException) {}
            extractor.release()
        }
    }

    private fun writePcm(
        codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo, lame: AndroidLame, channels: Int, sampleRate: Int,
        clipStartUs: Long, clipEndUs: Long, clip: Boolean, out: OutputStream
    ) {
        val buffer = codec.getOutputBuffer(index)!!
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val total = shorts.remaining()
        val all = ShortArray(total)
        shorts.get(all)
        var from = 0 // chỉ số mẫu (đã nhân kênh)
        var to = total
        if (clip) {
            val frames = total / channels
            val startFrame = ((clipStartUs - info.presentationTimeUs) * sampleRate / 1_000_000L).coerceIn(0L, frames.toLong()).toInt()
            val endFrame = ((clipEndUs - info.presentationTimeUs) * sampleRate / 1_000_000L).coerceIn(0L, frames.toLong()).toInt()
            from = startFrame * channels
            to = endFrame * channels
            if (to <= from) return
        }
        val frames = (to - from) / channels
        val left = ShortArray(frames)
        val right = if (channels == 2) ShortArray(frames) else left
        for (i in 0 until frames) {
            left[i] = all[from + i * channels]
            if (channels == 2) right[i] = all[from + i * channels + 1]
        }
        val mp3 = ByteArray((1.25 * frames + 7200).toInt())
        val written = lame.encode(left, right, frames, mp3)
        if (written < 0) throw IOException("Mã hóa MP3 lỗi ($written)")
        if (written > 0) out.write(mp3, 0, written)
    }

    // README của TAndroidLame ghi tên hàm xả/đóng không thống nhất (flush / lameFlush, close / lameClose),
    // nên gọi qua reflection, thử lần lượt từng tên để build không phụ thuộc phiên bản.
    private fun flush(lame: AndroidLame, buffer: ByteArray): Int {
        for (name in arrayOf("flush", "lameFlush")) {
            val method = runCatching { lame.javaClass.getMethod(name, ByteArray::class.java) }.getOrNull() ?: continue
            return (method.invoke(lame, buffer) as? Number)?.toInt() ?: 0
        }
        throw IOException("Thư viện MP3 thiếu hàm kết thúc mã hóa")
    }

    private fun close(lame: AndroidLame) {
        for (name in arrayOf("close", "lameClose")) {
            val method = runCatching { lame.javaClass.getMethod(name) }.getOrNull() ?: continue
            runCatching { method.invoke(lame) }
            return
        }
    }

    private fun MediaFormat.optInt(key: String, fallback: Int): Int = if (containsKey(key)) getInteger(key) else fallback
}
