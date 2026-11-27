@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nan.nantube.ui

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import android.view.TextureView
import com.nan.nantube.data.CaptionCue
import com.nan.nantube.data.CaptionTrack
import com.nan.nantube.data.PipPrefs
import com.nan.nantube.data.ShortsCaptions
import com.nan.nantube.data.SleepTimer
import com.nan.nantube.data.WatchPrefs
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.nan.nantube.R
import com.nan.nantube.data.Account
import com.nan.nantube.data.ChannelAvatars
import com.nan.nantube.data.LensSearch
import com.nan.nantube.data.ChannelSubscriptions
import com.nan.nantube.data.CommentPage
import com.nan.nantube.data.CommentsRepository
import com.nan.nantube.data.publishedIn
import com.nan.nantube.data.relativePublished
import com.nan.nantube.data.LikeCounts
import com.nan.nantube.data.ShortDescription
import com.nan.nantube.data.ShortInfo
import com.nan.nantube.data.ShortsMedia
import com.nan.nantube.data.ShortsPlayback
import com.nan.nantube.data.VideoItem
import com.nan.nantube.data.VideoRepository
import com.nan.nantube.data.WatchHistory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Màn xem video dài (không phải Short, không phải nhạc) ngay trong app, bố cục như YouTube: trình phát 16:9 ở trên cùng,
 * thanh tua, rồi tiêu đề + dòng thông tin, hàng Đăng ký / thích / không thích / chia sẻ, thẻ bình luận và danh sách đề xuất.
 * AppRoot vẽ [WatchScreen] phủ lên mọi thứ khi [WatchViewer.video] khác null.
 */
object WatchViewer {
    var video by mutableStateOf<VideoItem?>(null)
        private set

    /** Các video đã xem trong phiên mở màn xem này (cũ nhất ở đầu): nút "Trước" quay lại video ngay trước đó. */
    private val history = mutableStateListOf<VideoItem>()

    /** Có video trước đó để quay lại hay chưa (nút "Trước" chỉ bấm được khi đã đổi sang video khác). */
    val canGoBack: Boolean get() = history.isNotEmpty()

    fun open(v: VideoItem) {
        val current = video
        if (current != null && current.id != v.id) {
            history.removeAll { it.id == current.id }
            history.add(current)
            if (history.size > MAX_HISTORY) history.removeAt(0)
        }
        video = v
    }

    /** Quay lại video trước đó (không đẩy video hiện tại vào lịch sử). */
    fun back() {
        if (history.isEmpty()) return
        video = history.removeAt(history.lastIndex)
    }

    fun close() {
        video = null
        history.clear()
    }

    private const val MAX_HISTORY = 50
}

private val Red = Color(0xFFFF0000)

/** Hai bảng kéo được nằm dưới trình phát (cùng một khung, một lúc chỉ mở một bảng). */
private enum class WatchPanel { Description, Comments, Transcript }
private val NoRipple: androidx.compose.foundation.Indication? = null

/** Số lần tự đổi client lấy link mới khi link bị từ chối / hết hạn trước khi báo "không phát được". */
private const val MAX_RECOVERY = 4
private val AUDIO_REP = Regex("aud(\\d+)_")

/**
 * Trình phát của một video: ExoPlayer riêng + kế hoạch phát lấy từ [ShortsPlayback.plan] (cùng đường lấy link
 * VISIONOS → TV_TIZEN → WEB_EMBED → ANDROID_VR và cùng bản kê DASH của Short), nhưng đệm dài hơn vì video dài.
 * Link bị từ chối giữa chừng thì đổi client rồi phát tiếp từ chỗ đang xem.
 */
internal class WatchPlayback(private val context: Context, val video: VideoItem, private val account: Account?) {
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(20_000, 60_000, 1_000, 2_500).build())
        .build().apply {
            playWhenReady = true
            setWakeMode(C.WAKE_MODE_NETWORK)
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true
            )
        }

    var wantPlay by mutableStateOf(true)
    var buffering by mutableStateOf(true)
    var ended by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var firstFrame by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var bufferedMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var released = false
        private set

    /** Đã nạp nguồn phát ít nhất một lần: bộ phát nhận lại từ thẻ nổi thì không nạp lại từ đầu. */
    var started = false
        private set

    /** Tốc độ phát người dùng chọn (nút bánh răng → Tốc độ phát). */
    var speed by mutableFloatStateOf(1f)
        private set

    /** Mức chất lượng ép (cạnh ngắn của khung hình, vd 480); null = Tự động. */
    var quality by mutableStateOf<Int?>(null)
        private set

    /** Cạnh ngắn của khung hình đang phát, để ghi "Tự động (480p)"; 0 = chưa có hình. */
    var playingSide by mutableIntStateOf(0)
        private set

    /** Cho video lặp lại (Tuỳ chọn khác). */
    var loop by mutableStateOf(false)
        private set

    /** Các bản phụ đề /player liệt kê, có sau khi lấy được kế hoạch phát; [tracksReady] báo đã lấy xong (kể cả khi rỗng). */
    var tracks by mutableStateOf<List<CaptionTrack>>(emptyList())
        private set
    var tracksReady by mutableStateOf(false)
        private set

    private val refused = mutableSetOf<String>()
    private var source: String? = null
    private var tries = 0
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // App ẩn mà không được phát nền (xem ShortsBackground.mayPlay): không để video tự phát lại nốt phần đã tải.
                if (playWhenReady && !ShortsBackground.mayPlay()) {
                    player.playWhenReady = false
                    return
                }
                wantPlay = playWhenReady
                // Thẻ media (phát nền) phải theo đúng trạng thái, kể cả khi bị dừng từ ngoài (app khác giành âm thanh, hẹn giờ ngủ).
                ShortsBackground.onSessionChanged()
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                ended = state == Player.STATE_ENDED
                ShortsBackground.onSessionChanged()
                if (state == Player.STATE_READY) {
                    tries = 0
                    val d = player.duration
                    if (d > 0) durationMs = d
                }
            }

            override fun onRenderedFirstFrame() {
                firstFrame = true
            }

            override fun onPlayerError(error: PlaybackException) {
                recover()
            }

            override fun onTracksChanged(tracks: Tracks) {
                applyAudio(tracks)
                applyQuality()
            }
        })
    }

    /** Bản âm thanh người dùng chọn ([AudioChoice.key]); null = bản gốc (đứng đầu bản kê DASH). Video không có bản đó thì dùng bản gốc. */
    var audioKey by mutableStateOf<String?>(null)
        private set

    fun selectAudio(key: String?) {
        audioKey = key
        applyAudio(player.currentTracks)
    }

    /** Số thứ tự bản âm thanh của một nhóm track (đọc từ id biểu diễn trong bản kê DASH, xem ShortsMedia.mpd). */
    private fun audioIndex(group: Tracks.Group): Int? =
        group.getTrackFormat(0).id?.let { AUDIO_REP.find(it)?.groupValues?.get(1)?.toIntOrNull() }

    /** Khóa bản âm thanh đang phát (null nếu chưa nạp xong hoặc video chỉ có một bản). */
    fun playingAudio(): String? {
        val group = player.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 && it.isSelected } ?: return null
        val index = audioIndex(group) ?: return null
        return ShortsPlayback.audioChoices(video.id).firstOrNull { it.index == index }?.key
    }

    /**
     * Video có nhiều bản âm thanh (lồng tiếng): ép vào bản người dùng chọn, không có thì bản gốc, như Short,
     * dù máy đặt ngôn ngữ khác.
     */
    private fun applyAudio(tracks: Tracks) {
        if (released) return
        val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
        if (groups.size < 2) return
        val choices = ShortsPlayback.audioChoices(video.id)
        if (choices.isEmpty()) return
        val want = audioKey?.let { key -> choices.firstOrNull { it.key == key } } ?: choices.first()
        val group = groups.firstOrNull { audioIndex(it) == want.index } ?: return
        val params = player.trackSelectionParameters
        // Đã ép đúng rồi thì thôi, tránh vòng lặp onTracksChanged → đặt lại → onTracksChanged.
        if (params.overrides[group.mediaTrackGroup]?.trackIndices == listOf(0)) return
        player.trackSelectionParameters = params.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
            .build()
    }

    /** Lấy link → dựng DASH → nạp vào ExoPlayer, bắt đầu từ [resumeMs]. */
    fun start(resumeMs: Long = 0L) {
        if (released) return
        started = true
        job?.cancel()
        failed = false
        job = scope.launch {
            try {
                val plan = ShortsPlayback.plan(context, account, video, refused).await()
                source = plan.source
                if (plan.durationMs > 0) durationMs = plan.durationMs
                tracks = ShortsPlayback.meta(video.id)?.captionTracks.orEmpty()
                tracksReady = true
                val media = withContext(Dispatchers.IO) { ShortsMedia.buildSource(context, plan) }
                if (released) return@launch
                player.setMediaSource(media, resumeMs)
                player.prepare()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                recover()
            }
        }
    }

    private fun recover() {
        if (released) return
        if (tries >= MAX_RECOVERY) {
            failed = true
            return
        }
        tries++
        val at = player.currentPosition.coerceAtLeast(positionMs)
        source?.let { refused.add(it) }
        ShortsPlayback.forget(video.id)
        start(at)
    }

    fun retry() {
        tries = 0
        refused.clear()
        ShortsPlayback.forget(video.id)
        start(positionMs)
    }

    fun toggle() {
        if (ended) {
            player.seekTo(0)
            player.playWhenReady = true
        } else {
            player.playWhenReady = !player.playWhenReady
        }
    }

    fun seekTo(ms: Long) {
        positionMs = ms
        player.seekTo(ms)
    }

    fun selectSpeed(value: Float) {
        speed = value
        player.setPlaybackSpeed(value)
    }

    fun setLooping(on: Boolean) {
        loop = on
        player.repeatMode = if (on) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun selectQuality(value: Int?) {
        quality = value
        applyQuality()
    }

    /** Ép ExoPlayer vào đúng mức [quality] (mức gần nhất nếu video không có đúng mức đó); Tự động thì bỏ ép (như ShortsPool.applyQuality). */
    private fun applyQuality() {
        if (released) return
        val params = player.trackSelectionParameters
        val want = quality
        if (want == null) {
            if (params.overrides.keys.any { it.type == C.TRACK_TYPE_VIDEO }) {
                player.trackSelectionParameters = params.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
            }
            return
        }
        for (group in player.currentTracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO || group.length == 0) continue
            var best = -1
            var bestGap = Int.MAX_VALUE
            for (i in 0 until group.length) {
                val f = group.getTrackFormat(i)
                val gap = kotlin.math.abs(minOf(f.width, f.height) - want)
                if (gap < bestGap) {
                    bestGap = gap
                    best = i
                }
            }
            if (best < 0) continue
            // Đã ép đúng rồi thì thôi, tránh vòng lặp onTracksChanged → đặt lại → onTracksChanged.
            if (params.overrides[group.mediaTrackGroup]?.trackIndices == listOf(best)) continue
            player.trackSelectionParameters = params.buildUpon()
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, best))
                .build()
        }
    }

    /**
     * Âm lượng ổn định (theo AudioLoudness của meetube): YouTube chỉ hạ video to hơn mức chuẩn, không bao giờ tăng:
     * gain = min(1, 10^(-dB/20)). Không rõ độ ồn thì giữ nguyên.
     */
    fun applyStableVolume(on: Boolean) {
        if (released) return
        val db = ShortsPlayback.meta(video.id)?.loudnessDb
        player.volume = if (on && db != null && db > 0f) Math.pow(10.0, -db / 20.0).toFloat().coerceIn(0f, 1f) else 1f
    }

    /** Cập nhật vị trí / bộ đệm / thời lượng từ ExoPlayer (gọi định kỳ trên luồng chính). */
    fun poll() {
        if (released) return
        positionMs = player.currentPosition.coerceAtLeast(0L)
        bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
        playingSide = player.videoFormat?.let { minOf(it.width, it.height).coerceAtLeast(0) } ?: 0
        val d = player.duration
        if (d > 0) durationMs = d
    }

    /**
     * Video dài còn phát mà app không còn hiện (phát nền): chỉ phát âm thanh, tắt giải mã + tải luồng hình cho nhẹ mạng và pin, như
     * [ShortsPool.enterAudioOnly]. Hiện lại app thì bật hình trở lại.
     */
    fun setAudioOnly(on: Boolean) {
        if (released) return
        val params = player.trackSelectionParameters
        if (params.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO) == on) return
        player.trackSelectionParameters = params.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, on).build()
    }

    fun release() {
        if (released) return
        released = true
        job?.cancel()
        scope.cancel()
        player.release()
        if (PipState.watchPb === this) PipState.watchPb = null
    }
}

@Composable
fun WatchScreen(account: Account?, onSave: (VideoItem) -> Unit) {
    val video = WatchViewer.video ?: return
    // Đổi video (bấm một video đề xuất) thì dựng lại hết: trình phát cũ được giải phóng, trang cuộn về đầu.
    key(video.id) { WatchContent(video, account, onSave) }
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

/** Nút CC đang bật: chưa lấy xong danh sách phụ đề thì coi như bật nếu người dùng đã bật, để nút CC không nháy. */
internal fun watchCaptionsOn(pb: WatchPlayback): Boolean =
    WatchPrefs.captionKey != null && (!pb.tracksReady || activeCaption(pb.tracks) != null)

/**
 * Mọi việc phải chạy suốt đời một [WatchPlayback], dù nó đang ở màn xem hay ở thẻ nổi ([WatchMiniWindow]): cập nhật vị trí, cài đặt theo tài khoản,
 * hẹn giờ ngủ, âm lượng ổn định, ghi lịch sử xem, nạp phụ đề. Trả về dòng phụ đề đang hiện (null nếu không có).
 */
@Composable
internal fun WatchPlaybackEffects(pb: WatchPlayback, account: Account?): String? {
    val context = LocalContext.current
    LaunchedEffect(pb) {
        while (true) {
            pb.poll()
            delay(250)
        }
    }
    // Cài đặt theo tài khoản (tự phát, môi trường xung quanh, âm lượng ổn định, phụ đề).
    LaunchedEffect(account?.id) { WatchPrefs.bind(account) }
    // Hẹn giờ ngủ: hết giờ thì tạm dừng video đang xem.
    DisposableEffect(pb) {
        val pause = { pb.player.playWhenReady = false }
        SleepTimer.attach(pause)
        onDispose { SleepTimer.detach(pause) }
    }
    LaunchedEffect(WatchPrefs.stableVolume, pb.tracksReady) { pb.applyStableVolume(WatchPrefs.stableVolume) }
    LaunchedEffect(pb, account?.id) {
        WatchHistory.track(account, pb.video) {
            if (!pb.released && pb.firstFrame && (pb.player.isPlaying || pb.ended)) {
                WatchHistory.Playback(pb.player.currentPosition / 1000f, pb.durationMs.takeIf { it > 0 }?.let { it / 1000f })
            } else null
        }
    }

    // Phụ đề: bản đang bật (nhớ qua các video, theo tài khoản); tải khi có bản, tự vẽ theo vị trí phát.
    val captionTrack = activeCaption(pb.tracks)
    var cues by remember { mutableStateOf<List<CaptionCue>>(emptyList()) }
    LaunchedEffect(captionTrack?.key, captionTrack?.baseUrl) {
        cues = emptyList()
        val track = captionTrack ?: return@LaunchedEffect
        try {
            cues = ShortsCaptions.load(track)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toast(context, e.message?.takeIf { it.startsWith("Không tải được phụ đề") } ?: "Không tải được phụ đề")
        }
    }
    val captionText by remember { derivedStateOf { if (cues.isEmpty()) null else ShortsCaptions.textAt(cues, pb.positionMs) } }
    return captionText
}

private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun WatchContent(video: VideoItem, account: Account?, onSave: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Mở lại đúng video đang ở thẻ nổi thì nhận lại bộ phát đang chạy (không nạp lại); mở video khác thì thẻ nổi tắt.
    val pb = remember { WatchMini.take(video) ?: run { WatchMini.close(); WatchPlayback(context, video, account) } }
    DisposableEffect(pb) {
        if (!pb.started) pb.start(0L)
        // Bấm Hình trong hình: bộ phát đã giao cho thẻ nổi, không giải phóng.
        onDispose { if (WatchMini.pb !== pb) pb.release() }
    }
    val captionText = WatchPlaybackEffects(pb, account)

    var sheet by remember { mutableStateOf<WatchSheetPage?>(null) }
    var moreOpen by remember { mutableStateOf(false) }
    // Lớp nút của trình phát đang hiện hay ẩn: thanh tua theo (hiện: đỏ + chấm, ẩn: trắng xám, không chấm) như YouTube.
    var controlsShown by remember { mutableStateOf(true) }
    var ambientFrame by remember { mutableStateOf<ImageBitmap?>(null) }

    // Chưa lấy xong danh sách phụ đề thì coi như đang bật nếu người dùng đã bật, để nút CC không nháy.
    val captionsOn = watchCaptionsOn(pb)

    var info by remember { mutableStateOf<ShortInfo?>(null) }
    var desc by remember { mutableStateOf<ShortDescription?>(ShortsPlayback.cachedDescription(video.id)) }
    var comments by remember { mutableStateOf<CommentPage?>(null) }
    var liked by remember { mutableStateOf(false) }
    var disliked by remember { mutableStateOf(false) }
    var subBusy by remember { mutableStateOf(false) }
    val likeEffect = rememberTapEffect()
    val subEffect = rememberTapEffect()
    val playerHolder = remember { arrayOfNulls<PlayerView>(1) }
    var notifyFor by remember { mutableStateOf<com.nan.nantube.data.SearchResult.Channel?>(null) }
    // Bảng mô tả (bấm tiêu đề) nằm dưới trình phát, chiều cao tối đa = vùng dưới video nên không bao giờ che video.
    // Chỉ có hai mốc hết cỡ / đóng: kéo xuống quá nửa (hoặc vuốt nhanh xuống) thì tự đóng nốt, chưa tới thì bật lại.
    val panelSheet = remember { PanelSheet(scope, 1f, 1f) }
    var panel by remember { mutableStateOf<WatchPanel?>(null) }
    var panelAreaHeight by remember { mutableIntStateOf(0) }
    fun openPanel(kind: WatchPanel) {
        panel = kind
        panelSheet.open()
    }
    fun closePanel() = panelSheet.close { panel = null }

    LaunchedEffect(video.id, account?.id) {
        val i = try {
            ShortsPlayback.info(context, account, video).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        info = i
        i?.liked?.let { liked = it }
        i?.likes?.let { LikeCounts.remember(video.id, it) }
        LikeCounts.noteState(video.id, liked)
    }
    LaunchedEffect(video.id) {
        desc = try {
            ShortsPlayback.description(video.id).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
    LaunchedEffect(video.id, account?.id) {
        comments = try {
            CommentsRepository.first(video.id, account)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }


    val related = remember {
        PagedFeed<VideoItem>(
            first = { acc, _ -> VideoRepository.watchRelated(acc, video.id) },
            more = { acc, token -> VideoRepository.watchRelatedMore(acc, token, video.id) },
            keyOf = { it.id },
            prepare = { ChannelAvatars.prefetchFor(it) }
        )
    }
    LaunchedEffect(account?.id) { related.load(account) }

    // Video kế tiếp = đề xuất đầu tiên (nút "Sau" và tự phát).
    val next = related.items.firstOrNull { it.id != video.id }

    // Hình trong hình: nút Trước / Phát-Tạm dừng / Sau của cửa sổ PiP điều khiển đúng video này; rời app khi đang phát thì tự vào PiP.
    val latestNext by rememberUpdatedState(next)
    DisposableEffect(pb) {
        val controls = PipControls(
            prev = { WatchViewer.back() },
            next = { latestNext?.let { WatchViewer.open(it) } },
            toggle = { pb.toggle() }
        )
        PipState.controls = controls
        PipState.watchStop = { pb.player.playWhenReady = false }
        PipState.watchPb = pb
        onDispose {
            if (PipState.controls === controls) PipState.controls = null
            PipState.watchStop = null
            PipState.watchId = null
            PipState.watchPlaying = false
            if (PipState.watchPb === pb) PipState.watchPb = null
        }
    }
    LaunchedEffect(video.id, pb.wantPlay) {
        PipState.watchId = video.id
        PipState.watchPlaying = pb.wantPlay
    }
    var sleepStopped by remember { mutableStateOf(false) }
    // Hết video: hẹn giờ "Cuối video" thì dừng hẳn, không thì tự phát video kế nếu công tắc tự phát đang bật.
    LaunchedEffect(pb.ended, WatchPrefs.autoplay, next?.id) {
        if (!pb.ended) return@LaunchedEffect
        if (SleepTimer.endOfVideo) {
            SleepTimer.expire()
            sleepStopped = true
            return@LaunchedEffect
        }
        if (sleepStopped) return@LaunchedEffect
        if (WatchPrefs.autoplay && next != null) WatchViewer.open(next)
    }

    BackHandler(enabled = true) {
        when {
            panel != null -> closePanel()
            else -> WatchViewer.close()
        }
    }


    fun toggleLike() {
        if (account == null) return toast(context, "Hãy đăng nhập để thích video")
        val now = !liked
        val wasDisliked = disliked
        LikeCounts.noteState(video.id, !now)
        liked = now
        if (now) disliked = false
        likeEffect.play(burst = now)
        scope.launch {
            try {
                ShortsPlayback.like(account, video.id, now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                liked = !now
                disliked = wasDisliked
                toast(context, e.shortMessage ?: "Không thích được video")
            }
        }
    }

    fun toggleDislike() {
        if (account == null) return toast(context, "Hãy đăng nhập để đánh giá video")
        val now = !disliked
        val wasLiked = liked
        disliked = now
        if (now) liked = false
        scope.launch {
            try {
                ShortsPlayback.dislike(account, video.id, now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                disliked = !now
                liked = wasLiked
                toast(context, e.shortMessage ?: "Không đổi được đánh giá")
            }
        }
    }

    fun share() {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, video.watchUrl)
        try {
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    fun setSubscribed(channel: com.nan.nantube.data.SearchResult.Channel, subscribe: Boolean) {
        if (account == null) return toast(context, "Hãy đăng nhập để đăng ký kênh")
        if (subBusy) return
        subBusy = true
        scope.launch {
            try {
                ChannelSubscriptions.set(account, channel, subscribe)
                if (subscribe) subEffect.play()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(context, e.shortMessage ?: "Không đổi được trạng thái đăng ký")
            } finally {
                subBusy = false
            }
        }
    }

    val channel = remember(info) { info?.toChannel() }
    val subscribed = channel != null && ChannelSubscriptions.isSubscribed(account, channel)
    val likeText = LikeCounts.show(video.id, liked) ?: info?.likes ?: desc?.likesText
    val title = video.title.ifBlank { info?.title ?: ShortsPlayback.meta(video.id)?.title.orEmpty() }

    Box(Modifier.fillMaxSize().background(NanColors.Background)) {
        if (WatchPrefs.ambient) AmbientGlowBackground(ambientFrame)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().background(Color.Black).then(if (PipState.inPip) Modifier else Modifier.statusBarsPadding())) {
                WatchPlayerArea(
                    pb = pb,
                    video = video,
                    hasNext = next != null,
                    hasPrev = WatchViewer.canGoBack,
                    onPrev = { WatchViewer.back() },
                    captionText = captionText,
                    captionsOn = captionsOn,
                    ambient = WatchPrefs.ambient,
                    holder = playerHolder,
                    onFrame = { ambientFrame = it },
                    onClose = { WatchViewer.close() },
                    onNext = { next?.let { WatchViewer.open(it) } },
                    onCast = { TvScreen.show(video) },
                    onCc = {
                        if (captionsOn) {
                            WatchPrefs.setCaption(null)
                        } else {
                            val pick = ShortsCaptions.pick(pb.tracks, WatchPrefs.captionLast) ?: ShortsCaptions.defaultTrack(pb.tracks)
                            if (pick == null) toast(context, if (pb.tracksReady) "Video này không có phụ đề" else "Đang tải danh sách phụ đề…")
                            else WatchPrefs.setCaption(pick.key)
                        }
                    },
                    onSettings = { sheet = WatchSheetPage.Main },
                    controls = controlsShown,
                    onControls = { controlsShown = it }
                )
                // Thanh tua nằm đè lên đáy video như YouTube, không chiếm thêm chiều cao bên dưới.
                if (!PipState.inPip) WatchSeekBar(
                    positionMs = pb.positionMs,
                    durationMs = pb.durationMs,
                    bufferedMs = pb.bufferedMs,
                    onSeek = { pb.seekTo(it) },
                    active = controlsShown,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            LoadMoreEffect(related.listState, related.items.size) { related.loadMore(account) }
            Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { panelAreaHeight = it.height }) {
                LazyColumn(Modifier.fillMaxSize(), state = related.listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                    item(key = "head") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = NoRipple) { openPanel(WatchPanel.Description) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                title,
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            val meta = buildAnnotatedString {
                                val author = info?.author ?: video.channel ?: ShortsPlayback.meta(video.id)?.author
                                if (author != null) {
                                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium)) { append(author) }
                                    append("  ")
                                }
                                // Dòng này chỉ hiện số rút gọn + "x phút trước" như YouTube; số đầy đủ và ngày chi tiết nằm trong bảng mô tả.
                                likeText?.let { append("$it lượt thích  ") }
                                val views = video.views ?: ShortsPlayback.meta(video.id)?.views ?: desc?.viewsText
                                views?.let { append("$it  ") }
                                // Chuỗi lượt xem của một số nguồn đã kèm sẵn "x phút trước": đừng nối thêm lần nữa.
                                (video.published ?: desc?.publishText).takeIf { publishedIn(views) == null }?.let { raw ->
                                    // Mở từ "Video đã xem" thì chỉ có ngày đầy đủ: đổi sang "x ngày trước" như mọi chỗ khác.
                                    val shown = publishedIn(raw) ?: relativePublished(desc?.isoDate, ShortsPlayback.meta(video.id)?.publishDate, raw) ?: raw
                                    append("$shown  ")
                                }
                                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium)) { append("…thêm") }
                            }
                            Text(
                                meta,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = NanColors.TextSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    item(key = "actions") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChannelAvatar(
                                video.copy(
                                    channel = info?.author ?: video.channel,
                                    channelId = info?.channelId ?: video.channelId,
                                    avatar = info?.avatar ?: video.avatar
                                ),
                                32.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            SubscribeButton(channel, subscribed, account, subEffect, { setSubscribed(it, true) }, { notifyFor = it })
                            Spacer(Modifier.weight(1f))
                            WatchAction(if (liked) WatchThumbUp else WatchThumbUpOutline, "Thích", likeEffect) { toggleLike() }
                            WatchAction(if (disliked) WatchThumbDown else WatchThumbDownOutline, "Không thích") { toggleDislike() }
                            WatchAction(WatchShare, "Chia sẻ") { share() }
                            WatchAction(WatchMore, "Thêm") { moreOpen = true }
                        }
                    }
                    item(key = "comments") {
                        CommentsCard(comments, onClick = { openPanel(WatchPanel.Comments) })
                    }
                    items(related.items, key = { it.id }) { VideoCard(it, account, onSave) }
                    if (related.loadingMore || (related.loading && related.items.isEmpty())) item(key = "footer") { LoadingFooter() }
                }
                panel?.let { open ->
                    DraggablePanel(
                        fraction = panelSheet.fraction,
                        onDrag = { panelSheet.drag(it, panelAreaHeight) },
                        onRelease = { panelSheet.settle(it) { panel = null } },
                        modifier = Modifier.align(Alignment.BottomCenter).imePadding(),
                        handleHeight = 20.dp
                    ) {
                        when (open) {
                            WatchPanel.Description -> ShortDescriptionContent(
                                video = video,
                                info = info,
                                likesHint = likeText,
                                bottomInset = true,
                                onClose = ::closePanel,
                                compact = true,
                                extras = { stats ->
                                    WatchDescriptionExtras(
                                        video = video,
                                        info = info,
                                        stats = stats,
                                        channel = channel,
                                        subscribed = subscribed,
                                        account = account,
                                        subEffect = subEffect,
                                        onLens = {
                                            val frame = if (pb.firstFrame) LensSearch.grab(playerHolder[0]) else null
                                            if (frame == null) {
                                                toast(context, "Chưa lấy được khung hình của video")
                                            } else {
                                                scope.launch {
                                                    if (!LensSearch.open(context, frame)) toast(context, "Không mở được Google Ống kính")
                                                }
                                            }
                                        },
                                        onTranscript = { openPanel(WatchPanel.Transcript) },
                                        onSubscribe = { setSubscribed(it, true) },
                                        onNotify = { notifyFor = it }
                                    )
                                }
                            )
                            WatchPanel.Transcript -> TranscriptContent(
                                tracks = pb.tracks,
                                positionMs = pb.positionMs,
                                onSeek = { pb.seekTo(it) },
                                onClose = ::closePanel
                            )
                            WatchPanel.Comments -> VideoChatContent(video = video, account = account, onSave = onSave, onClose = ::closePanel)
                        }
                    }
                }
            }
        }

        sheet?.let { page ->
            WatchSettingsSheet(
                pb = pb,
                tracks = pb.tracks,
                page = page,
                onPage = { sheet = it },
                onClose = { sheet = null }
            )
        }

        if (moreOpen && !PipState.inPip) {
            WatchMoreSheet(
                // Hình trong hình: thu video vào thẻ nổi trong app, màn xem đóng, app về tab đang mở trước đó.
                onPip = {
                    if (PipPrefs.popup) {
                        WatchMini.start(pb)
                        WatchViewer.close()
                    } else {
                        // Popup đang tắt: mở trang cài đặt để bật lại (AppRoot đóng màn xem).
                        PipState.openExperiments = true
                    }
                },
                onSave = { onSave(video) },
                onDownload = { DownloadPanel.open(video, account) },
                onClose = { moreOpen = false }
            )
        }

        notifyFor?.let { ch ->
            NotificationSheet(
                ch = ch,
                account = account,
                onDismiss = { notifyFor = null },
                onUnsubscribe = { notifyFor = null; setSubscribed(ch, false) }
            )
        }
    }
}

@Composable
private fun WatchAction(icon: ImageVector, description: String, effect: TapEffectState? = null, onClick: () -> Unit) {
    // Lớp bấm được cắt tròn nằm riêng bên dưới để chấm confetti của tapEffect bay ra ngoài khung không bị cắt.
    Box(Modifier.size(41.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().clip(CircleShape).clickable(onClick = onClick))
        Icon(icon, description, Modifier.size(22.dp).tapEffect(effect, ring = false), tint = Color.White)
    }
}

/** Thẻ "Bình luận N" + bình luận đầu tiên; chạm mở bảng bình luận đầy đủ. */
@Composable
private fun CommentsCard(page: CommentPage?, onClick: () -> Unit) {
    val total = page?.total
        ?.replace(Regex("\\s*(bình luận|comments?)", RegexOption.IGNORE_CASE), "")?.trim()
        ?.takeIf { c -> c.any(Char::isDigit) }
    val first = page?.items?.firstOrNull()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NanColors.Popup)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bình luận", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (total != null) Text(total, fontSize = 12.sp, color = NanColors.TextSecondary, modifier = Modifier.padding(start = 8.dp))
        }
        if (first != null) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF8E2A5B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(first.author.trimStart('@').take(1).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    first.avatar?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
                Spacer(Modifier.width(12.dp))
                Text(first.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Vùng trình phát 16:9: video, ảnh bìa lúc chờ khung đầu, vòng chờ, lỗi và lớp nút điều khiển như YouTube. */
@Composable
private fun WatchPlayerArea(
    pb: WatchPlayback,
    video: VideoItem,
    hasNext: Boolean,
    hasPrev: Boolean,
    onPrev: () -> Unit,
    captionText: String?,
    captionsOn: Boolean,
    ambient: Boolean,
    holder: Array<PlayerView?>,
    onFrame: (ImageBitmap?) -> Unit,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onCast: () -> Unit,
    onCc: () -> Unit,
    onSettings: () -> Unit,
    controls: Boolean,
    onControls: (Boolean) -> Unit
) {
    var touch by remember { mutableStateOf(0) }
    // Dòng báo "Tự động phát đang bật / tắt" hiện giữa trên video ~1,5 giây sau khi gạt công tắc.
    var hint by remember { mutableStateOf<String?>(null) }
    var hintStamp by remember { mutableIntStateOf(0) }
    LaunchedEffect(hintStamp) {
        if (hintStamp == 0) return@LaunchedEffect
        delay(1_500)
        hint = null
    }
    // Đang phát thì lớp nút tự ẩn sau 3,5 giây không chạm.
    LaunchedEffect(controls, pb.wantPlay, touch) {
        if (controls && pb.wantPlay && !pb.ended) {
            delay(3_500)
            onControls(false)
        }
    }
    DisposableEffect(Unit) { onDispose { holder[0]?.player = null } }

    // Chế độ môi trường xung quanh (theo AmbientGlow của meetube): vài giây đọc một khung nhỏ 48x27 từ video, nền trang tô theo khung đó.
    LaunchedEffect(ambient, pb.firstFrame) {
        if (!ambient || !pb.firstFrame) return@LaunchedEffect
        while (true) {
            val view = holder[0]?.videoSurfaceView as? TextureView
            if (view != null && view.isAvailable) {
                view.getBitmap(48, 27)?.let { onFrame(it.asImageBitmap()) }
            }
            delay(2_000)
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = NoRipple) { onControls(!controls) }
    ) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.shorts_player_view, null) as PlayerView).also {
                    holder[0] = it
                    it.player = pb.player
                }
            },
            update = { it.player = pb.player },
            modifier = Modifier.fillMaxSize()
        )
        if (!pb.firstFrame) {
            AsyncImage(
                model = video.hqThumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        when {
            pb.failed -> Box(
                Modifier.fillMaxSize().background(Color(0x99000000)).clickable { pb.retry() },
                contentAlignment = Alignment.Center
            ) {
                Text("Không phát được video này.\nChạm để thử lại.", fontSize = 14.sp, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            pb.buffering && !pb.ended -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
            }
        }

        // Phụ đề nằm dưới giữa video, nhích lên khi lớp nút điều khiển đang hiện.
        if (captionsOn && captionText != null && !pb.failed) {
            CaptionOverlay(
                captionText,
                Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = if (controls) 34.dp else 10.dp)
            )
        }

        AnimatedVisibility(visible = controls && !pb.failed && !PipState.inPip, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
                // Hàng trên: thu nhỏ (đóng) | công tắc tự phát, truyền lên TV, phụ đề (CC), cài đặt.
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ControlIcon(Lucide.ChevronDown, "Thu nhỏ", 30.dp) { onClose() }
                    Spacer(Modifier.weight(1f))
                    AutoplaySwitch(WatchPrefs.autoplay) {
                        val on = !WatchPrefs.autoplay
                        WatchPrefs.updateAutoplay(on)
                        hint = if (on) "Tự động phát đang bật" else "Tự động phát đang tắt"
                        hintStamp++
                        touch++
                    }
                    ControlIcon(WatchCast, "Truyền", 24.dp) { onCast(); touch++ }
                    ControlIcon(if (captionsOn) WatchCcOn else WatchCc, "Phụ đề", 28.dp) { onCc(); touch++ }
                    ControlIcon(Lucide.Settings, "Cài đặt", 24.dp) { onSettings(); touch++ }
                }
                hint?.let {
                    Text(
                        it,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 52.dp)
                    )
                }
                // Giữa: trước (video vừa xem, chỉ bấm được sau khi đã đổi video) | phát / tạm dừng | sau (sang video đề xuất đầu tiên).
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.alpha(if (hasPrev) 1f else 0.4f)) {
                        CircleControl(WatchSkipPrev, "Trước", 44.dp) { if (hasPrev) onPrev(); touch++ }
                    }
                    CircleControl(
                        when {
                            pb.ended -> WatchReplay
                            pb.wantPlay -> WatchPause
                            else -> WatchPlay
                        },
                        "Phát / Tạm dừng", 58.dp
                    ) { pb.toggle(); touch++ }
                    Box(Modifier.alpha(if (hasNext) 1f else 0.4f)) {
                        CircleControl(WatchSkipNext, "Sau", 44.dp) { if (hasNext) onNext(); touch++ }
                    }
                }
                // Hàng dưới: thời gian + toàn màn hình (trang trí).
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 14.dp, end = 4.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${clock(pb.positionMs)} / ${clock(pb.durationMs)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Spacer(Modifier.weight(1f))
                    ControlIcon(WatchFullscreen, "Toàn màn hình", 22.dp) { touch++ }
                }
            }
        }
    }
}

/** Công tắc tự phát kiểu YouTube: núm trắng + biểu tượng phát ở bên phải khi bật, núm xám + biểu tượng tạm dừng ở bên trái khi tắt. */
@Composable
private fun AutoplaySwitch(on: Boolean, onToggle: () -> Unit) {
    val x by animateDpAsState(if (on) 16.dp else 0.dp, tween(150), label = "autoplay")
    Box(
        Modifier
            .width(40.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(50))
            .background(if (on) Color(0x99555555) else Color(0x66AAAAAA))
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier.offset(x = 2.dp + x).size(20.dp).clip(CircleShape).background(if (on) Color.White else Color(0xFF8E8E8E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (on) WatchPlay else WatchPause, if (on) "Tự động phát đang bật" else "Tự động phát đang tắt", Modifier.size(12.dp), tint = if (on) Color.Black else Color.White)
        }
    }
}

/** Ánh sáng môi trường xung quanh: khung hình video phóng to, làm đậm màu, mờ dần vào màu nền trang; khung mới chuyển dần vào. */
@Composable
private fun AmbientGlowBackground(frame: ImageBitmap?) {
    Box(Modifier.fillMaxWidth().height(420.dp)) {
        Crossfade(targetState = frame, animationSpec = tween(1_200), label = "ambient") { f ->
            if (f != null) {
                Image(
                    bitmap = f,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.Medium,
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.6f) }),
                    modifier = Modifier.fillMaxSize().alpha(0.8f)
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, NanColors.Background.copy(alpha = 0.35f), NanColors.Background))
            )
        )
    }
}

@Composable
private fun ControlIcon(icon: ImageVector, description: String, size: Dp, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, Modifier.size(size), tint = Color.White)
    }
}

@Composable
private fun CircleControl(icon: ImageVector, description: String, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(0x55000000)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, Modifier.size(size * 0.6f), tint = Color.White)
    }
}

/**
 * Thanh tua dưới video, đo theo YouTube: dày 2dp, nền xám đặc #444444. Lớp nút đang hiện ([active]): phần đã xem đỏ #FF0033 + chấm đỏ
 * (to hơn khi kéo). Lớp nút ẩn: phần đã xem xám trắng #CCCCCC, không chấm. Chạm hoặc kéo để tua.
 */
@Composable
private fun WatchSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val played = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val frac = drag ?: played
    val buffered = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier
            .fillMaxWidth()
            .height(16.dp)
            .pointerInput(durationMs) {
                detectTapGestures { o ->
                    if (durationMs > 0) onSeek((o.x / size.width * durationMs).toLong().coerceIn(0L, durationMs))
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        drag?.let { if (durationMs > 0) onSeek((it * durationMs).toLong()) }
                        drag = null
                    },
                    onDragCancel = { drag = null },
                    onHorizontalDrag = { change, _ -> drag = (change.position.x / size.width).coerceIn(0f, 1f) }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val track = 2.dp.toPx()
            val top = size.height - track
            val showThumb = active || drag != null
            drawRect(Color(0xFF444444), Offset(0f, top), Size(size.width, track))
            drawRect(Color(0x33FFFFFF), Offset(0f, top), Size(size.width * buffered, track))
            drawRect(if (showThumb) Color(0xFFFF0033) else Color(0xFFCCCCCC), Offset(0f, top), Size(size.width * frac, track))
            if (showThumb) {
                val radius = (if (drag != null) 8.dp else 6.dp).toPx()
                drawCircle(Color(0xFFFF0033), radius, Offset((size.width * frac).coerceIn(radius, size.width - radius), top + track / 2f))
            }
        }
    }
}
