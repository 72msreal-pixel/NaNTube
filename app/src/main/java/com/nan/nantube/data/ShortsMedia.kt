@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nan.nantube.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.DefaultDashChunkSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Phần media3 của trình phát Shorts, làm theo Media3SourceFactory / Media3PlayerCache / Media3PlayerInitializer của meetube:
 * - DASH dựng từ các luồng của /player (SegmentBase: init + sidx), ExoPlayer chỉ tải từng đoạn đang cần;
 * - mọi yêu cầu googlevideo dùng đúng UA media của meetube, đọc quá 4 giây không có dữ liệu thì hỏng nhanh rồi thử lại ngay
 *   từ chỗ dừng (googlevideo hay treo lặng lẽ); lỗi 403/404/410/416 báo ngay cho app đổi client chứ không thử lại cùng link;
 * - bộ nhớ đệm đĩa với khóa ổn định (id + itag + lmt), vuốt lại video cũ hoặc video đã tải trước là phát ngay;
 * - ngưỡng bắt đầu phát 0,5 giây như meetube.
 */
object ShortsMedia {
    private const val CACHE_DIR = "shorts-media-cache"
    private const val MAX_CACHE_BYTES = 256L * 1024 * 1024
    private const val READ_TIMEOUT_MS = 4_000
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val LOAD_RETRY_COUNT = 6
    private const val PREFETCH_VIDEO_BYTES = 900_000L
    private const val PREFETCH_AUDIO_BYTES = 160_000L
    private val GENERATED_MANIFEST_URI: Uri = Uri.parse("https://youtube.com/generated.mpd")

    @Volatile
    private var cache: SimpleCache? = null
    private val prefetching = ConcurrentHashMap<String, Boolean>()

    /** Khóa ổn định cho media googlevideo: id + itag + lmt + xtags xác định đúng các byte; phần còn lại của URL đổi liên tục (expire, sig, ip). */
    private val cacheKeys = CacheKeyFactory { spec ->
        val uri = spec.uri
        val id = uri.getQueryParameter("id")
        val itag = uri.getQueryParameter("itag")
        if (id == null || itag == null) {
            spec.key ?: uri.toString()
        } else {
            "yt.$id.$itag.${uri.getQueryParameter("lmt").orEmpty()}.${uri.getQueryParameter("xtags").orEmpty()}"
        }
    }

    @Synchronized
    private fun cache(context: Context): Cache? {
        cache?.let { return it }
        return try {
            val app = context.applicationContext
            SimpleCache(File(app.cacheDir, CACHE_DIR), LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES), StandaloneDatabaseProvider(app))
                .also { cache = it }
        } catch (_: Throwable) {
            null // Không tạo được bộ nhớ đệm thì vẫn phát bình thường, chỉ không nhớ lại.
        }
    }

    private fun http(): HttpDataSource.Factory = DefaultHttpDataSource.Factory()
        .setUserAgent(InnerTube.MEDIA_UA)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
        .setReadTimeoutMs(READ_TIMEOUT_MS)

    fun dataSourceFactory(context: Context): DataSource.Factory {
        val upstream = http()
        val store = cache(context) ?: return upstream
        return CacheDataSource.Factory()
            .setCache(store)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheKeyFactory(cacheKeys)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** Bộ đệm nhỏ cho Short (video ngắn, lặp lại; 3 ô phát cùng nạp nên không tải quá xa): bắt đầu phát khi có 0,5 giây, sau treo mạng thì 1,5 giây. */
    fun loadControl(): LoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(6_000, 15_000, 500, 1_500)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    /**
     * Giống FailFastLoadErrorPolicy của meetube: thử lại tối đa 6 lần nhưng chờ không quá 1 giây mỗi lần; link bị từ chối
     * (403/404/410/416) thì không thử lại cùng link mà báo lỗi ngay để app lấy link mới bằng client khác.
     */
    private class FailFastPolicy : DefaultLoadErrorHandlingPolicy(LOAD_RETRY_COUNT) {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val e = loadErrorInfo.exception
            if (e is HttpDataSource.InvalidResponseCodeException && e.responseCode in REFUSED) return C.TIME_UNSET
            val delay = super.getRetryDelayMsFor(loadErrorInfo)
            return if (delay == C.TIME_UNSET) delay else minOf(delay, 1_000L)
        }
    }

    private val REFUSED = intArrayOf(403, 404, 410, 416)

    /** Lỗi của ExoPlayer là do link googlevideo bị từ chối / hết hạn (cần lấy link mới). */
    fun isRefused(cause: Throwable?): Boolean {
        var t = cause
        while (t != null) {
            if (t is HttpDataSource.InvalidResponseCodeException && t.responseCode in REFUSED) return true
            t = t.cause
        }
        return false
    }

    /** Dựng nguồn phát từ kế hoạch: DASH khi có đủ luồng video + âm thanh, không thì tệp MP4 muxed. Chạy ngoài luồng chính. */
    fun buildSource(context: Context, plan: PlayPlan): MediaSource {
        val factory = dataSourceFactory(context)
        val policy = FailFastPolicy()
        val audio = plan.audio
        if (plan.videos.isNotEmpty() && audio != null) {
            try {
                val manifest = DashManifestParser().parse(GENERATED_MANIFEST_URI, ByteArrayInputStream(mpd(plan, audio).toByteArray(Charsets.UTF_8)))
                return DashMediaSource.Factory(DefaultDashChunkSource.Factory(factory), factory)
                    .setLoadErrorHandlingPolicy(policy)
                    .createMediaSource(manifest, MediaItem.fromUri(GENERATED_MANIFEST_URI))
            } catch (e: Exception) {
                // Bản kê không đọc được: rơi về tệp MP4 muxed nếu có, không thì báo lỗi gốc.
                if (plan.muxed == null) throw e
            }
        }
        val muxed = plan.muxed ?: error("Không có luồng để phát")
        return ProgressiveMediaSource.Factory(factory)
            .setLoadErrorHandlingPolicy(policy)
            .createMediaSource(MediaItem.fromUri(muxed.url))
    }

    /** Bản kê DASH tĩnh, cùng dạng YouTubeMPDBuilder của MediaServiceCore (SegmentBase + indexRange). */
    private fun mpd(plan: PlayPlan, audio: MediaStream): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<MPD xmlns=\"urn:mpeg:DASH:schema:MPD:2011\" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\" ")
        append("type=\"static\" minBufferTime=\"PT1.500S\" mediaPresentationDuration=\"PT${plan.durationSec.coerceAtLeast(1)}S\">\n")
        append("<Period>\n")
        val video = plan.videos.first()
        append("<AdaptationSet id=\"0\" mimeType=\"${esc(video.mime)}\" subsegmentAlignment=\"true\">\n")
        append("<Role schemeIdUri=\"urn:mpeg:DASH:role:2011\" value=\"main\"/>\n")
        for (v in plan.videos) {
            append("<Representation id=\"${v.itag}\" codecs=\"${esc(v.codecs)}\" startWithSAP=\"1\" bandwidth=\"${v.bitrate}\" ")
            append("width=\"${v.width}\" height=\"${v.height}\" maxPlayoutRate=\"1\" frameRate=\"${v.fps.coerceAtLeast(1)}\">\n")
            representationBody(v)
            append("</Representation>\n")
        }
        append("</AdaptationSet>\n")
        append("<AdaptationSet id=\"1\" mimeType=\"${esc(audio.mime)}\" subsegmentAlignment=\"true\">\n")
        append("<Role schemeIdUri=\"urn:mpeg:DASH:role:2011\" value=\"main\"/>\n")
        append("<Representation id=\"${audio.itag}\" codecs=\"${esc(audio.codecs)}\" startWithSAP=\"1\" bandwidth=\"${audio.bitrate}\" ")
        append("audioSamplingRate=\"${audio.sampleRate ?: "44100"}\">\n")
        append("<AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"2\"/>\n")
        representationBody(audio)
        append("</Representation>\n")
        append("</AdaptationSet>\n")
        append("</Period>\n</MPD>\n")
    }

    private fun StringBuilder.representationBody(s: MediaStream) {
        append("<BaseURL>${esc(s.url)}</BaseURL>\n")
        append("<SegmentBase indexRange=\"${s.indexRange}\" indexRangeExact=\"true\"><Initialization range=\"${s.initRange}\"/></SegmentBase>\n")
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    // ---- Tải trước ------------------------------------------------------------------------------

    /**
     * Ghi vài giây đầu của luồng video (mức chất lượng mà bộ chọn của ExoPlayer nhiều khả năng sẽ chọn: 70% băng thông ước tính,
     * như AdaptiveTrackSelection) và âm thanh vào bộ nhớ đệm, giống Media3NextPreloader của meetube. Chạy ngoài luồng chính.
     */
    fun prefetch(context: Context, plan: PlayPlan) {
        if (cache(context) == null || prefetching.putIfAbsent(plan.videoId, true) != null) return
        try {
            val factory = dataSourceFactory(context)
            val audio = plan.audio
            val video = if (plan.videos.isNotEmpty() && audio != null) {
                val estimate = DefaultBandwidthMeter.getSingletonInstance(context).bitrateEstimate * 0.7
                plan.videos.lastOrNull { it.bitrate <= estimate } ?: plan.videos.first()
            } else {
                plan.muxed
            }
            video?.let { warm(factory, it, PREFETCH_VIDEO_BYTES) }
            if (plan.videos.isNotEmpty() && audio != null) warm(factory, audio, PREFETCH_AUDIO_BYTES)
        } finally {
            // Sau khi xong cho phép tải trước lại (kế hoạch mới có link mới); trong lúc chạy chặn trùng.
            prefetching.remove(plan.videoId)
        }
    }

    private fun warm(factory: DataSource.Factory, stream: MediaStream, bytes: Long) {
        val length = if (stream.size > 0) minOf(bytes, stream.size) else bytes
        val spec = DataSpec.Builder().setUri(stream.url).setPosition(0).setLength(length).build()
        try {
            CacheWriter(factory.createDataSource() as CacheDataSource, spec, ByteArray(64 * 1024), null).cache()
        } catch (_: Exception) {
            // Mạng chập chờn hay link bị từ chối: bỏ qua, lúc phát thật sẽ tải lại và báo lỗi nếu cần.
        }
    }
}
