package com.nan.nantube.data

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

object MediaJoiner {
    private const val VIDEO_BUFFER_BYTES = 8 * 1024 * 1024
    private const val AUDIO_BUFFER_BYTES = 1024 * 1024

    private class Source(val extractor: MediaExtractor, val format: MediaFormat, bufferBytes: Int) {
        var track = -1
        // Mỗi luồng một bộ đệm riêng (như MediaMuxHelper của meetube), cỡ theo mẫu lớn nhất của luồng đó.
        val buffer: ByteBuffer = ByteBuffer.allocateDirect(bufferBytes)
    }

    fun join(videoFile: File, audioFile: File, output: File) {
        val sources = mutableListOf<Source>()
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            sources += open(videoFile, "video/", VIDEO_BUFFER_BYTES)
            sources += open(audioFile, "audio/", AUDIO_BUFFER_BYTES)
            sources.forEach { it.track = muxer.addTrack(it.format) }
            muxer.start()
            copySamples(sources, muxer)
            muxer.stop()
        } finally {
            sources.forEach { it.extractor.release() }
            muxer.release()
        }
    }

    private fun open(file: File, mimePrefix: String, bufferBytes: Int): Source {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith(mimePrefix) == true) {
                    extractor.selectTrack(index)
                    val maxInput = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
                    return Source(extractor, format, maxOf(bufferBytes, maxInput))
                }
            }
        } catch (e: Exception) {
            extractor.release()
            throw e
        }
        extractor.release()
        error("Không đọc được luồng ${mimePrefix.trimEnd('/')} của video")
    }

    private fun copySamples(sources: List<Source>, muxer: MediaMuxer) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val next = sources.filter { it.extractor.sampleTime >= 0 }.minByOrNull { it.extractor.sampleTime } ?: break
            // clear() trước mỗi lần đọc: readSampleData cập nhật limit, không xóa thì mẫu lớn sau mẫu nhỏ bị cắt/ném lỗi.
            next.buffer.clear()
            val size = next.extractor.readSampleData(next.buffer, 0)
            if (size < 0) break
            val key = (next.extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
            info.set(0, size, next.extractor.sampleTime, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            muxer.writeSampleData(next.track, next.buffer, info)
            next.extractor.advance()
        }
    }
}
