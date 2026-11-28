package com.nan.nantube.data

import android.media.MediaCodecList
import android.os.Build

object StreamSupport {
    private val decoders by lazy { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder } }

    private fun mimeOf(codecs: String): String? = when {
        codecs.startsWith("avc1") -> "video/avc"
        codecs.startsWith("vp9") -> "video/x-vnd.on2.vp9"
        codecs.startsWith("av01") -> "video/av01"
        else -> null
    }

    fun canDecode(stream: MediaStream): Boolean {
        val mime = mimeOf(stream.codecs) ?: return false
        return decoders.any { info ->
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || info.isHardwareAccelerated) &&
                info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                info.getCapabilitiesForType(mime).videoCapabilities?.isSizeSupported(stream.width, stream.height) == true
        }
    }
}
