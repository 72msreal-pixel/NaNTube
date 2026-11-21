package com.nan.nantube.data

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

object MediaJoiner {
    private const val BUFFER_BYTES = 8 * 1024 * 1024

    private class Source(val extractor: MediaExtractor, val format: MediaFormat) {
        var track = -1
    }

    fun join(videoFile: File, audioFile: File, output: File) {
        val sources = mutableListOf<Source>()
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            sources += open(videoFile, "video/")
            sources += open(audioFile, "audio/")
            sources.forEach { it.track = muxer.addTrack(it.format) }
            muxer.start()
            copySamples(sources, muxer)
            muxer.stop()
        } finally {
            sources.forEach { it.extractor.release() }
            muxer.release()
        }
    }

    private fun open(file: File, mimePrefix: String): Source {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith(mimePrefix) == true) {
                    extractor.selectTrack(index)
                    return Source(extractor, format)
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
        val buffer = ByteBuffer.allocateDirect(BUFFER_BYTES)
        val info = MediaCodec.BufferInfo()
        while (true) {
            val next = sources.filter { it.extractor.sampleTime >= 0 }.minByOrNull { it.extractor.sampleTime } ?: break
            val size = next.extractor.readSampleData(buffer, 0)
            if (size < 0) break
            val key = (next.extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
            info.set(0, size, next.extractor.sampleTime, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            muxer.writeSampleData(next.track, buffer, info)
            next.extractor.advance()
        }
    }
}
