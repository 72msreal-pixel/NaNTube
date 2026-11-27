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
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.platform.LocalDensity
import com.nan.nantube.data.HistoryChip
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.drawBehind
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
import com.composables.icons.lucide.X
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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

    /** Giữ chế độ chỉ âm thanh khi sang video khác trong cùng phiên xem. */
    internal var onTheGo = false

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

    /** Bản chụp danh sách video đã xem trong phiên (cũ nhất ở đầu): thẻ nổi giữ lại để nút Trước vẫn dùng được. */
    fun historySnapshot(): List<VideoItem> = history.toList()

    /** Mở lại màn xem từ thẻ nổi: video + lịch sử "Trước" mà thẻ nổi đã giữ. */
    fun restore(v: VideoItem, prevs: List<VideoItem>, listenOnly: Boolean) {
        onTheGo = listenOnly
        history.clear()
        history.addAll(prevs.filter { it.id != v.id })
        video = v
    }

    /** Quay lại video trước đó (không đẩy video hiện tại vào lịch sử). */
    fun back() {
        if (history.isEmpty()) return
        video = history.removeAt(history.lastIndex)
    }

    /** Đang xem toàn màn hình (xoay ngang, ẩn thanh hệ thống). Nằm ở mức toàn app để tự phát sang video khác vẫn giữ nguyên toàn màn hình. */
    var fullscreen by mutableStateOf(false)
        private set

    fun updateFullscreen(on: Boolean) {
        fullscreen = on && video != null
    }

    fun close() {
        video = null
        fullscreen = false
        onTheGo = false
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
internal class WatchPlayback(
    private val context: Context,
    val video: VideoItem,
    private val account: Account?,
    initialOnTheGo: Boolean = false
) {
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

    var onTheGo by mutableStateOf(initialOnTheGo)
        private set
    private var backgroundAudioOnly = false

    var wantPlay by mutableStateOf(true)
    var buffering by mutableStateOf(true)
    var ended by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var firstFrame by mutableStateOf(false)
        private set
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
        applyAudioOnly()
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

    /** Công tắc chủ động, độc lập với chế độ chỉ âm thanh tạm thời khi app ẩn. */
    fun updateOnTheGo(on: Boolean) {
        if (released || onTheGo == on) return
        onTheGo = on
        if (WatchMini.pb !== this && WatchViewer.video?.id == video.id) WatchViewer.onTheGo = on
        if (!on) firstFrame = false
        applyAudioOnly()
    }

    fun setBackgroundAudioOnly(on: Boolean) {
        if (released || backgroundAudioOnly == on) return
        backgroundAudioOnly = on
        applyAudioOnly()
    }

    private fun applyAudioOnly() {
        val disableVideo = onTheGo || backgroundAudioOnly
        val params = player.trackSelectionParameters
        if (params.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO) == disableVideo) return
        // Cập nhật tham số hiện có, không nạp lại nguồn và không xoá chất lượng / bản âm thanh đã chọn.
        player.trackSelectionParameters = params.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disableVideo).build()
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
            if (!pb.released && (pb.firstFrame || pb.onTheGo) && (pb.player.isPlaying || pb.ended)) {
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

/**
 * Mọi thứ đã tải của trang xem một video (thông tin, mô tả, bình luận, thích / không thích, danh sách đề xuất kèm chỗ đang cuộn, chip):
 * thu video vào thẻ nổi thì [WatchMini] giữ lại, mở rộng lại là hiện đủ ngay, không tải lại.
 */
internal class WatchStash(
    val videoId: String,
    val info: ShortInfo?,
    val desc: ShortDescription?,
    val comments: CommentPage?,
    val liked: Boolean,
    val disliked: Boolean,
    val chips: androidx.compose.runtime.MutableState<List<HistoryChip>>,
    val related: PagedFeed<VideoItem>,
    val chipFeed: PagedFeed<VideoItem>
)

/** Danh sách đề xuất bên dưới trình phát của [video]; chip lọc YouTube trả kèm được ghi vào [chips]. */
internal fun makeRelatedFeed(video: VideoItem, chips: androidx.compose.runtime.MutableState<List<HistoryChip>>) =
    PagedFeed<VideoItem>(
        first = { acc, _ ->
            val r = VideoRepository.watchRelatedWithChips(acc, video.id)
            chips.value = r.chips
            r.page
        },
        more = { acc, token -> VideoRepository.watchRelatedMore(acc, token, video.id) },
        keyOf = { it.id },
        prepare = { ChannelAvatars.prefetchFor(it) }
    )

/** Danh sách của chip đang chọn (khác "Tất cả") trong bảng "Video khác": token chip = trang đề xuất đã lọc; "c:" / "q:" / "h:" = nguồn dự phòng. */
internal fun makeChipFeed(video: VideoItem) =
    PagedFeed<VideoItem>(
        first = { acc, arg ->
            when {
                arg.startsWith("c:") -> VideoRepository.channelVideosPage(acc, arg.removePrefix("c:"))
                arg.startsWith("q:") -> VideoRepository.chipPage(acc, arg.removePrefix("q:"), null, false)
                arg == "h:" -> VideoRepository.homePage(acc)
                else -> VideoRepository.watchRelatedMore(acc, "w:$arg", video.id)
            }
        },
        more = { acc, token ->
            if (token.startsWith("b:")) VideoRepository.homeMore(acc, token)
            else VideoRepository.watchRelatedMore(acc, token, video.id)
        },
        keyOf = { it.id },
        prepare = { ChannelAvatars.prefetchFor(it) }
    )

@Composable
private fun WatchContent(video: VideoItem, account: Account?, onSave: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Mở lại đúng video đang ở thẻ nổi thì nhận lại bộ phát đang chạy (không nạp lại); mở video khác thì thẻ nổi tắt.
    val pb = remember { WatchMini.take(video) ?: run { WatchMini.close(); WatchPlayback(context, video, account, initialOnTheGo = WatchViewer.onTheGo) } }
    DisposableEffect(pb) {
        if (!pb.started) pb.start(0L)
        // Bấm Hình trong hình: bộ phát đã giao cho thẻ nổi, không giải phóng.
        onDispose { if (WatchMini.pb !== pb) pb.release() }
    }
    // Mở lại từ thẻ nổi: nhận lại dữ liệu trang đã tải (bình luận, thích, đề xuất...), khỏi tải lại.
    val stash = remember { WatchMini.takeStash(video.id) }
    val captionText = WatchPlaybackEffects(pb, account)

    var sheet by remember { mutableStateOf<WatchSheetPage?>(null) }
    var moreOpen by remember { mutableStateOf(false) }
    // Lớp nút của trình phát đang hiện hay ẩn: thanh tua theo (hiện: đỏ + chấm, ẩn: trắng xám, không chấm) như YouTube.
    var controlsShown by remember { mutableStateOf(true) }
    var ambientFrame by remember { mutableStateOf<ImageBitmap?>(null) }

    // Chưa lấy xong danh sách phụ đề thì coi như đang bật nếu người dùng đã bật, để nút CC không nháy.
    val captionsOn = watchCaptionsOn(pb)

    var info by remember { mutableStateOf<ShortInfo?>(stash?.info) }
    var desc by remember { mutableStateOf<ShortDescription?>(stash?.desc ?: ShortsPlayback.cachedDescription(video.id)) }
    var comments by remember { mutableStateOf<CommentPage?>(stash?.comments) }
    var liked by remember { mutableStateOf(stash?.liked ?: false) }
    var disliked by remember { mutableStateOf(stash?.disliked ?: false) }
    // Phần nào đã có sẵn từ thẻ nổi thì lần chạy đầu của hiệu ứng nạp bỏ qua (đổi tài khoản về sau vẫn nạp lại).
    var infoReady by remember { mutableStateOf(stash?.info != null) }
    var descReady by remember { mutableStateOf(stash?.desc != null) }
    var commentsReady by remember { mutableStateOf(stash?.comments != null) }
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
        if (infoReady) { infoReady = false; return@LaunchedEffect }
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
        if (descReady) { descReady = false; return@LaunchedEffect }
        desc = try {
            ShortsPlayback.description(video.id).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
    LaunchedEffect(video.id, account?.id) {
        if (commentsReady) { commentsReady = false; return@LaunchedEffect }
        comments = try {
            CommentsRepository.first(video.id, account)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }


    // Chip lọc của bảng "Video khác" (toàn màn hình) do chính YouTube trả cùng trang đề xuất: "Của <kênh>", "Video có liên quan", "Dành cho bạn".
    val relatedChipsState = remember { stash?.chips ?: androidx.compose.runtime.mutableStateOf<List<HistoryChip>>(emptyList()) }
    val relatedChips by relatedChipsState
    val related = remember { stash?.related ?: makeRelatedFeed(video, relatedChipsState) }
    LaunchedEffect(account?.id) { related.load(account) }
    // Danh sách của chip đang chọn (khác "Tất cả"): token chip = trang đề xuất đã lọc; "c:<id kênh>" = video của kênh (khi server không trả chip).
    val chipFeed = remember { stash?.chipFeed ?: makeChipFeed(video) }
    /** Gói dữ liệu trang này để giao cho thẻ nổi khi thu nhỏ. */
    fun makeStash() = WatchStash(video.id, info, desc, comments, liked, disliked, relatedChipsState, related, chipFeed)

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
    // Tự phát tắt + hết video: hiện khung "Video đề xuất" (như YouTube) thay cho tự nhảy sang video khác.
    // Bấm Đóng / X thì quay về màn hết video có nút phát lại; phát lại video (ended tắt) thì khung được dựng lại cho lần hết sau.
    var suggestClosed by remember(pb.ended) { mutableStateOf(false) }
    val suggest = next.takeIf { pb.ended && !WatchPrefs.autoplay && !suggestClosed && !sleepStopped && !PipState.inPip && !SleepTimer.endOfVideo }

    val fullscreen = WatchViewer.fullscreen
    // Toàn màn hình + bình luận: khung chat trượt vào từ mép phải, video thu hẹp lại bên trái (như YouTube ngang).
    var fsComments by remember { mutableStateOf(false) }
    LaunchedEffect(fullscreen) { if (!fullscreen) fsComments = false }
    // Tỉ lệ mở của khung chat: kéo bằng ngón tay thì đi theo ngón tay, thả tay thì tự về mốc mở / đóng gần nhất.
    val fsAnim = remember { Animatable(0f) }
    val fsPanelFraction = fsAnim.value
    LaunchedEffect(fsComments, fullscreen) { fsAnim.animateTo(if (fsComments && fullscreen) 1f else 0f, tween(260)) }
    val fsPanelWidth = maxOf(300f, LocalConfiguration.current.screenWidthDp * 0.34f).dp
    val fsPanelPx = with(LocalDensity.current) { fsPanelWidth.toPx() }
    // Bảng "Video khác" (toàn màn hình): trượt từ đáy lên, kéo lên xuống được, 0 = ẩn hẳn, 1 = mở hết.
    val moreAnim = remember { Animatable(0f) }
    var rootHeight by remember { mutableIntStateOf(1) }
    var rootWidth by remember { mutableIntStateOf(1) }

    // Kéo KHUNG VIDEO xuống để thu nhỏ thành thẻ nổi (logic của meetube: MobilePlaybackActivity.applyMorph / onDismissDrag / MagneticDrag):
    // chỉ khung video co + dời về đúng chỗ thẻ nổi sẽ hiện, phần còn lại của trang mờ rất nhanh, nền mờ dần để lộ màn hình bên dưới.
    // Cú kéo "dính" như nam châm: tới khi ngón đi được 72dp thì video chỉ theo 75% quãng ngón tay, rồi bật "tách" (rung nhẹ) và theo sát 1:1;
    // thả tay lúc đã tách thì thu nhỏ, chưa tách thì chỉ vuốt nhanh xuống mới thu nhỏ, còn lại bật về.
    val minimize = remember { Animatable(0f) }
    val minGap = remember { Animatable(0f) }                       // khoảng lệch lò xo khép lại sau cú "tách" / "dính"
    var minDragging by remember { mutableStateOf(false) }
    var minGoalPx by remember { mutableFloatStateOf(0f) }          // nơi video "thuộc về" theo ngón tay (px)
    val minRun = remember { floatArrayOf(0f, 0f, 1f, 0f) }         // [0]=quãng ngón tay, [1]=1 nếu đã tách, [2]=quãng kéo tối đa, [3]=điểm cầm (0..1 theo chiều cao video)
    val dens = LocalDensity.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val minStatusPx = WindowInsets.statusBars.getTop(dens).toFloat()
    val minNavPx = WindowInsets.navigationBars.getBottom(dens).toFloat()
    val miniWPx = with(dens) { WATCH_MINI_WIDTH.toPx() }
    val miniMarginPx = with(dens) { 12.dp.toPx() }
    val miniBarPx = with(dens) { 64.dp.toPx() }
    val rootW = rootWidth.coerceAtLeast(1).toFloat()
    val miniScale = (miniWPx / rootW).coerceIn(0.2f, 1f)
    val videoHPx = rootW * 9f / 16f
    // Đích: khung video (cao 16:9, nằm dưới thanh trạng thái) đáp đúng lên thẻ nổi (góc dưới phải, trên thanh đáy) nên chuyển sang thẻ không giật.
    val minTx = rootW - miniWPx - miniMarginPx
    val minTy = rootHeight - miniWPx * 9f / 16f - minNavPx - miniBarPx - miniMarginPx - minStatusPx
    val pageBg = NanColors.Background
    /** Mức thu nhỏ 0..1: đang kéo thì theo ngón tay + lò xo, còn lại theo [minimize]. */
    fun minF(): Float = if (minDragging) ((minGoalPx + minGap.value) / minRun[2]).coerceIn(0f, 1f) else minimize.value
    fun minPlace() = minGoalPx + minGap.value
    fun minGoal() = if (minRun[1] > 0.5f) minRun[0] else minRun[0] * 0.75f
    val detachPx = with(dens) { 72.dp.toPx() }
    val attachPx = with(dens) { 56.dp.toPx() }
    fun minClick(detach: Boolean) {
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            val from = minPlace()
            minRun[1] = if (detach) 1f else 0f
            minGap.snapTo(from - minGoal())
            minGoalPx = minGoal()
            minGap.animateTo(0f, spring(dampingRatio = 0.95f, stiffness = if (detach) 800f else 850f))
        }
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
    }
    val minimizeDrag = rememberDraggableState { d ->
        if (!minDragging) {
            minDragging = true
            minRun[0] = 0f; minRun[1] = 0f
            // Quãng ngón tay đi để điểm đang cầm vẫn nằm dưới ngón khi video co về thẻ (meetube dragTravelFor).
            minRun[2] = (minTy + minRun[3] * videoHPx * (miniScale - 1f)).coerceAtLeast(rootHeight * 0.33f)
            minGoalPx = 0f
            controlsShown = false
        }
        minRun[0] = (minRun[0] + d).coerceAtLeast(0f)
        if (minRun[1] < 0.5f && minRun[0] >= detachPx) minClick(true)
        else if (minRun[1] > 0.5f && minRun[0] <= attachPx) minClick(false)
        minGoalPx = minGoal()
    }
    /** Thu video vào thẻ nổi: gập hết mọi bảng đang mở (Video khác, bình luận, cài đặt...) để không còn lớp nào che / chặn thẻ, rồi đóng màn xem. */
    fun enterPopup(fromDrag: Boolean) {
        sheet = null
        moreOpen = false
        fsComments = false
        scope.launch { moreAnim.snapTo(0f) }
        WatchMini.start(pb, makeStash(), WatchViewer.historySnapshot(), fromDrag = fromDrag)
        WatchViewer.close()
    }
    // Rời app thành cửa sổ PiP của hệ thống khi đang mở bảng (Video khác, bình luận...): cửa sổ PiP chụp cả giao diện nên bảng che mất video.
    // Chỉ gập các bảng lại, giữ nguyên màn xem (không đổi sang thẻ nổi giữa chừng vì làm cửa sổ PiP bị méo / tự phóng to).
    LaunchedEffect(PipState.inPip) {
        if (!PipState.inPip) return@LaunchedEffect
        sheet = null
        moreOpen = false
        fsComments = false
        moreAnim.snapTo(0f)
    }
    fun minRelease(v: Float) {
        if (!minDragging) return
        val density = dens.density
        val dismiss = if (minRun[1] > 0.5f) v >= -1200f else v > 800f * density
        val travel = minRun[2]
        val cur = minF()
        scope.launch {
            minimize.snapTo(cur)
            minDragging = false
            minGap.snapTo(0f)
            if (!dismiss) {
                val ms = (60f + 150f * cur).toInt().coerceIn(90, 280)
                minimize.animateTo(0f, tween(ms, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
            } else {
                // Rơi lên thẻ bằng lò xo (như meetube SETTLE_LAND_*): hạ cánh nhẹ, có chút nảy.
                minimize.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 800f), initialVelocity = (v / travel).coerceIn(0f, 8f))
                WatchMini.instantEnter = true
                enterPopup(fromDrag = true)
            }
        }
    }
    /** Mũi tên chỉ xuống ở góc video: thu nhỏ thành thẻ y như cú kéo xuống (video co về chỗ thẻ rồi nhận thẻ kiểu kéo), không đóng video. */
    fun minimizeNow() {
        if (minDragging || minimize.isRunning || minimize.value > 0f) return
        if (!PipPrefs.popup) {
            WatchViewer.close()
            return
        }
        if (fullscreen) {
            // Toàn màn hình (ngang): không có hiệu ứng co về góc, thu thẳng vào thẻ nổi rồi màn xem tự đóng, app về dọc.
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            enterPopup(fromDrag = true)
            return
        }
        controlsShown = false
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        scope.launch {
            minimize.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 800f))
            WatchMini.instantEnter = true
            enterPopup(fromDrag = true)
        }
    }
    LaunchedEffect(fullscreen) { if (!fullscreen) moreAnim.snapTo(0f) }
    // Toàn màn hình: ẩn thanh trạng thái + thanh điều hướng (vuốt từ mép để hiện tạm); thoát thì hiện lại.
    DisposableEffect(fullscreen) {
        val window = (context as? android.app.Activity)?.window
        if (fullscreen && window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
        } else {
            onDispose { }
        }
    }

    fun chatDrag(dx: Float) {
        scope.launch { fsAnim.snapTo((fsAnim.value - dx / fsPanelPx).coerceIn(0f, 1f)) }
    }

    /** [velocity] px/s, dương = đang kéo sang phải (đóng). */
    fun chatSettle(velocity: Float) {
        val target = when {
            velocity > 900f -> 0f
            velocity < -900f -> 1f
            fsAnim.value > 0.5f -> 1f
            else -> 0f
        }
        fsComments = target > 0f
        if (target > 0f) controlsShown = false
        scope.launch { fsAnim.animateTo(target, tween(220)) }
    }
    val chatDragState = rememberDraggableState { chatDrag(it) }

    fun moreDrag(dy: Float, gain: Float = 1f) {
        scope.launch { moreAnim.snapTo((moreAnim.value - dy * gain / rootHeight).coerceIn(0f, 1f)) }
    }

    /** [velocity] px/s, dương = đang kéo xuống (đóng). */
    fun moreSettle(velocity: Float) {
        val target = when {
            velocity > 1000f -> 0f
            velocity < -1000f -> 1f
            moreAnim.value > 0.5f -> 1f
            else -> 0f
        }
        scope.launch { moreAnim.animateTo(target, tween(220)) }
    }

    fun openMore() {
        controlsShown = false
        scope.launch { moreAnim.animateTo(1f, tween(260)) }
    }

    fun closeMore() {
        scope.launch { moreAnim.animateTo(0f, tween(240)) }
    }

    val moreChips = remember(relatedChips, info) {
        val author = (info?.author ?: video.channel)?.trim()?.replace(Regex("\\s+"), " ")
        val chId = info?.channelId ?: video.channelId
        val q = (info?.title ?: video.title).trim()
        fun clean(t: String) = t.replace(Regex("[\\s\\u00A0\\u2000-\\u200B\\u202F\\u3000]+"), " ").trim()
        buildList<Pair<String, String?>> {
            add("Tất cả" to null)
            if (relatedChips.size >= 3) {
                // Server trả đủ bộ chip: YouTube luôn xếp theo thứ tự kênh, liên quan, gợi ý cho bạn -> dùng nguyên nhãn + token của server
                // (đúng mọi ngôn ngữ, không đoán theo tên).
                relatedChips.take(3).forEach { add(clean(it.label) to it.token) }
            } else {
                // Server trả thiếu / không trả (không thể biết chip nào là chip nào) -> dùng đủ 3 chip dự phòng, không trộn với chip server để khỏi lệch vị trí / trùng.
                if (!chId.isNullOrBlank()) add((if (author.isNullOrBlank()) "Của kênh" else "Của $author") to "c:$chId")
                if (q.isNotEmpty()) add("Video có liên quan" to "q:$q")
                add("Dành cho bạn" to "h:")
            }
        }
    }
    var moreChip by remember { mutableIntStateOf(0) }
    val moreSel = moreChip.coerceIn(0, moreChips.lastIndex)
    val moreFeed = if (moreSel == 0) related else chipFeed
    val moreListState = remember(moreSel) { LazyListState() }

    BackHandler(enabled = true) {
        when {
            moreAnim.value > 0.01f -> closeMore()
            fsComments -> fsComments = false
            fullscreen -> WatchViewer.updateFullscreen(false)
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

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(pageBg.copy(alpha = (1f - minF()).coerceIn(0f, 1f))) }
            .onSizeChanged { rootHeight = it.height.coerceAtLeast(1); rootWidth = it.width.coerceAtLeast(1) }
    ) {
        if (WatchPrefs.ambient && !fullscreen && !pb.onTheGo) Box(Modifier.fillMaxSize().graphicsLayer { alpha = (1f - minF() * 5f).coerceIn(0f, 1f) }) { AmbientGlowBackground(ambientFrame) }
        Column(Modifier.fillMaxSize()) {
            Box(
                (if (fullscreen) Modifier.fillMaxSize().padding(end = fsPanelWidth * fsPanelFraction) else Modifier.fillMaxWidth())
                    // Dải thanh trạng thái phía trên video: đen, mờ cùng nền khi thu nhỏ. Nằm trên danh sách bên dưới để video bay qua không bị che.
                    .drawBehind { drawRect(Color.Black.copy(alpha = (1f - minF() * 3f).coerceIn(0f, 1f))) }
                    .zIndex(1f)
                    .draggable(
                        state = minimizeDrag,
                        orientation = Orientation.Vertical,
                        enabled = !fullscreen && !PipState.inPip && PipPrefs.popup,
                        onDragStarted = { pos ->
                            // Điểm ngón tay chạm trên khung video (0 = mép trên, 1 = mép dưới) để video co về mà không trượt khỏi ngón tay.
                            minRun[3] = ((pos.y - minStatusPx) / videoHPx).coerceIn(0f, 1f)
                        },
                        onDragStopped = { v -> minRelease(v) }
                    )
            ) {
              // Khung video: chỉ phần này co / dời / bo góc khi thu nhỏ (gốc xoay ở góc trên trái, như meetube).
              Box(
                Modifier
                    .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                    .then(if (PipState.inPip || fullscreen) Modifier else Modifier.statusBarsPadding())
                    .graphicsLayer {
                        val f = minF()
                        if (f > 0f) {
                            transformOrigin = TransformOrigin(0f, 0f)
                            val sc = 1f + (miniScale - 1f) * f
                            scaleX = sc
                            scaleY = sc
                            translationX = minTx * f
                            translationY = minTy * f
                            val fc = f.coerceAtMost(1f)
                            shadowElevation = 8.dp.toPx() * fc
                            shape = RoundedCornerShape((12.dp.toPx() * fc / sc.coerceAtLeast(0.01f)))
                            clip = true
                        }
                    }
                    .background(Color.Black)
              ) {
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
                    onClose = { minimizeNow() },
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
                    onControls = { controlsShown = it },
                    suggest = suggest,
                    onSuggestPlay = { next?.let { WatchViewer.open(it) } },
                    onSuggestClose = { suggestClosed = true; controlsShown = true },
                    fullscreen = fullscreen,
                    onFullscreen = { WatchViewer.updateFullscreen(!fullscreen); controlsShown = true },
                    fsInfo = if (!fullscreen) null else FullscreenInfo(
                        title = title,
                        channel = info?.author ?: video.channel ?: ShortsPlayback.meta(video.id)?.author,
                        liked = liked,
                        disliked = disliked,
                        nextThumb = next?.hqThumbnail,
                        onLike = { toggleLike() },
                        onDislike = { toggleDislike() },
                        onComments = { fsComments = !fsComments; if (fsComments) controlsShown = false },
                        onSave = { onSave(video) },
                        onShare = { share() },
                        onMore = { moreOpen = true },
                        onMoreVideos = { openMore() },
                        onSwipeUpDrag = { moreDrag(it, gain = 1.6f) },
                        onSwipeUpEnd = { moreSettle(it) }
                    )
                )
                // Thanh tua nằm đè lên đáy video như YouTube, không chiếm thêm chiều cao bên dưới.
                // Toàn màn hình: thanh tiến trình nằm trên hàng nút dưới và ẩn cùng lớp nút điều khiển.
                if (!PipState.inPip && suggest == null && (!fullscreen || controlsShown)) WatchSeekBar(
                    positionMs = pb.positionMs,
                    durationMs = pb.durationMs,
                    bufferedMs = pb.bufferedMs,
                    onSeek = { pb.seekTo(it) },
                    active = controlsShown,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .graphicsLayer { alpha = (1f - minF() * 5f).coerceIn(0f, 1f) }
                        .then(if (fullscreen) Modifier.displayCutoutPadding().padding(start = 12.dp, end = 12.dp, bottom = 56.dp) else Modifier)
                )
              }
            }
            LoadMoreEffect(related.listState, related.items.size) { related.loadMore(account) }
            if (!fullscreen) Box(Modifier.weight(1f).fillMaxWidth().graphicsLayer { alpha = (1f - minF() * 5f).coerceIn(0f, 1f) }.onSizeChanged { panelAreaHeight = it.height }) {
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

        // Vuốt sang trái ở mép phải video (lúc các nút đang ẩn) để kéo khung chat ra, ngón tay kéo tới đâu khung ra tới đó.
        // Mép này cao 70% để nửa dưới còn lại dành cho cử chỉ vuốt lên mở "Video khác".
        if (fullscreen && !fsComments && !controlsShown && moreAnim.value == 0f && !PipState.inPip) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight(0.7f)
                    .width(72.dp)
                    .draggable(state = chatDragState, orientation = Orientation.Horizontal, onDragStopped = { chatSettle(it) })
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = NoRipple) { controlsShown = true }
            )
        }

        // Khung bình luận toàn màn hình: kéo ra / vào bằng ngón tay (cả khung kéo ngang được, có thanh nắm ở mép trái),
        // hoặc bấm nút bình luận / X; thả tay thì tự về mốc gần nhất.
        if (fullscreen && fsPanelFraction > 0f) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(fsPanelWidth)
                    .offset { IntOffset(((1f - fsPanelFraction) * fsPanelWidth.toPx()).roundToInt(), 0) }
                    .background(Color(0xFF0F0F0F))
                    .draggable(state = chatDragState, orientation = Orientation.Horizontal, onDragStopped = { chatSettle(it) })
                    .imePadding()
            ) {
                Column(Modifier.fillMaxSize()) {
                    VideoChatContent(video = video, account = account, onSave = onSave, onClose = { fsComments = false })
                }
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp)
                        .size(width = 4.dp, height = 40.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0x66FFFFFF))
                )
            }
        }

        // Bảng "Video khác" toàn màn hình: trượt từ đáy lên; kéo xuống, bấm X hoặc bấm nền tối để trượt xuống.
        if (fullscreen && moreAnim.value > 0f) {
            FullscreenMorePanel(
                fraction = moreAnim.value,
                chips = moreChips.map { it.first },
                selected = moreSel,
                onChip = { i ->
                    moreChip = i
                    moreChips.getOrNull(i)?.second?.let { chipFeed.load(account, it) }
                },
                feed = moreFeed,
                listState = moreListState,
                onLoadMore = { moreFeed.loadMore(account) },
                onDrag = { moreDrag(it) },
                onRelease = { moreSettle(it) },
                onClose = { closeMore() },
                onOpen = { WatchViewer.open(it) }
            )
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
                        enterPopup(fromDrag = false)
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
    onControls: (Boolean) -> Unit,
    suggest: VideoItem? = null,
    onSuggestPlay: () -> Unit = {},
    onSuggestClose: () -> Unit = {},
    fullscreen: Boolean = false,
    onFullscreen: () -> Unit = {},
    fsInfo: FullscreenInfo? = null
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
    LaunchedEffect(ambient, pb.firstFrame, pb.onTheGo) {
        if (!ambient || !pb.firstFrame || pb.onTheGo) return@LaunchedEffect
        while (true) {
            val view = holder[0]?.videoSurfaceView as? TextureView
            if (view != null && view.isAvailable) {
                view.getBitmap(48, 27)?.let { onFrame(it.asImageBitmap()) }
            }
            delay(2_000)
        }
    }

    // Toàn màn hình + các nút đang ẩn: vuốt từ dưới lên để kéo bảng "Video khác" ra.
    val fsNow by rememberUpdatedState(fsInfo)
    val swipeUpState = rememberDraggableState { fsNow?.onSwipeUpDrag?.invoke(it) }
    Box(
        (if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = NoRipple) { onControls(!controls) }
            .draggable(
                state = swipeUpState,
                orientation = Orientation.Vertical,
                enabled = fullscreen && !controls && fsInfo != null,
                onDragStopped = { fsNow?.onSwipeUpEnd?.invoke(it) }
            )
    ) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.shorts_player_view, null) as PlayerView).also {
                    holder[0] = it
                    it.player = pb.player
                }
            },
            update = {
                it.player = pb.player
                it.keepScreenOn = !pb.onTheGo
            },
            modifier = Modifier.fillMaxSize()
        )
        if (!pb.firstFrame || pb.onTheGo) {
            AsyncImage(
                model = video.hqThumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (pb.onTheGo) Modifier.background(Color.Black) else Modifier)
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
                Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = if (controls) (if (fullscreen) 130.dp else 34.dp) else 10.dp)
            )
        }

        AnimatedVisibility(visible = controls && !pb.failed && !PipState.inPip && suggest == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)).then(if (fullscreen) Modifier.displayCutoutPadding() else Modifier)) {
                // Hàng trên: thu nhỏ (đóng) | công tắc tự phát, truyền lên TV, phụ đề (CC), cài đặt.
                // Toàn màn hình: mũi tên thoát toàn màn hình, kèm tiêu đề + tên kênh như YouTube.
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = if (fullscreen) 16.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                    ControlIcon(Lucide.ChevronDown, "Thu nhỏ", 30.dp) { onClose() }
                    if (fullscreen && fsInfo != null) {
                        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                            Text(fsInfo.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            fsInfo.channel?.takeIf { it.isNotBlank() }?.let {
                                Text(it, fontSize = 12.sp, color = Color(0xFFCCCCCC), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
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
                // Hàng dưới: thời gian + nút phóng to / thu nhỏ toàn màn hình.
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .padding(start = 14.dp, end = if (fullscreen) 12.dp else 4.dp, bottom = if (fullscreen) 76.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${clock(pb.positionMs)} / ${clock(pb.durationMs)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        // Toàn màn hình: chỉ chữ thời gian nhích xuống, nút thu nhỏ giữ nguyên chỗ.
                        modifier = if (fullscreen) Modifier.offset(y = 14.dp) else Modifier
                    )
                    Spacer(Modifier.weight(1f))
                    // Toàn màn hình: nút thu nhỏ cũng nhích xuống cùng hàng với chữ thời gian.
                    Box(if (fullscreen) Modifier.offset(y = 14.dp) else Modifier) {
                        ControlIcon(if (fullscreen) WatchFullscreenExit else WatchFullscreen, if (fullscreen) "Thu nhỏ" else "Toàn màn hình", 22.dp) { onFullscreen(); touch++ }
                    }
                }
                // Toàn màn hình: hàng thích / không thích / bình luận / lưu / chia sẻ / thêm + ô "Video khác" (kiểu YouTube).
                if (fullscreen && fsInfo != null) {
                    Row(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 14.dp, end = 12.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(Color(0x66000000)).padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ControlIcon(if (fsInfo.liked) WatchThumbUp else WatchThumbUpOutline, "Thích", 22.dp) { fsInfo.onLike(); touch++ }
                            ControlIcon(if (fsInfo.disliked) WatchThumbDown else WatchThumbDownOutline, "Không thích", 22.dp) { fsInfo.onDislike(); touch++ }
                            ControlIcon(YtComment, "Bình luận", 22.dp) { fsInfo.onComments() }
                            ControlIcon(YtBookmark, "Lưu", 22.dp) { fsInfo.onSave(); touch++ }
                            ControlIcon(WatchShare, "Chia sẻ", 22.dp) { fsInfo.onShare(); touch++ }
                            ControlIcon(WatchMore, "Thêm", 22.dp) { fsInfo.onMore(); touch++ }
                        }
                        Spacer(Modifier.weight(1f))
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(Color(0x66000000)).clickable { fsInfo.onMoreVideos() }
                                .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Video khác", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            fsInfo.nextThumb?.let {
                                Spacer(Modifier.width(10.dp))
                                AsyncImage(
                                    model = it,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.width(52.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
        if (suggest != null && !PipState.inPip) {
            EndSuggestion(suggest, onPlay = onSuggestPlay, onClose = onSuggestClose)
        }
    }
}

/**
 * Bảng "Video khác" của chế độ toàn màn hình (như YouTube): tiêu đề + X, hàng chip lọc, hàng thẻ video cuộn ngang.
 * [fraction] 0..1 là mức trượt từ đáy lên; kéo dọc ở vùng tiêu đề / chip / khoảng trống để trượt xuống, bấm nền tối cũng đóng.
 */
@Composable
private fun FullscreenMorePanel(
    fraction: Float,
    chips: List<String>,
    selected: Int,
    onChip: (Int) -> Unit,
    feed: PagedFeed<VideoItem>,
    listState: LazyListState,
    onLoadMore: () -> Unit,
    onDrag: (Float) -> Unit,
    onRelease: (Float) -> Unit,
    onClose: () -> Unit,
    onOpen: (VideoItem) -> Unit
) {
    val dragNow by rememberUpdatedState(onDrag)
    val releaseNow by rememberUpdatedState(onRelease)
    val dragState = rememberDraggableState { dragNow(it) }
    val screen = LocalConfiguration.current
    // Thẻ rộng theo chiều cao màn hình để cả bảng (tiêu đề + chip + thẻ) luôn vừa khi xoay ngang.
    val cardWidth = ((screen.screenHeightDp - 150) * 16f / 9f).coerceIn(150f, 280f).dp
    val shown = feed.items.filter { it.playlist == null }
    LoadMoreEffect(listState, shown.size, onLoadMore)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f * fraction))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = NoRipple) { onClose() }
    ) {
        // Tiêu đề + nút X đứng yên một chỗ (không trượt theo bảng): ẩn khi đang kéo / trượt, chỉ hiện khi bảng mở hẳn.
        val headerAlpha by animateFloatAsState(
            targetValue = if (fraction >= 0.999f) 1f else 0f,
            animationSpec = tween(if (fraction >= 0.999f) 150 else 80),
            label = "moreHeader"
        )
        Row(
            Modifier
                .fillMaxWidth()
                .zIndex(1f)
                .graphicsLayer { alpha = headerAlpha }
                .draggable(state = dragState, orientation = Orientation.Vertical, onDragStopped = { releaseNow(it) })
                .displayCutoutPadding()
                .padding(start = 18.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Video khác", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.weight(1f))
            ControlIcon(Lucide.X, "Đóng", 26.dp) { if (headerAlpha > 0.5f) onClose() }
        }
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = (1f - fraction) * size.height }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { releaseNow(it) }
                )
                .displayCutoutPadding()
        ) {
            // Khoảng trống giữa tiêu đề và chip: đẩy chip + hàng video xuống sát đáy (như YouTube), cũng kéo xuống được.
            Spacer(Modifier.weight(1f).fillMaxWidth())
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(chips) { i, label ->
                    val on = i == selected
                    Box(
                        Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) Color(0xFFF1F1F1) else Color(0x33FFFFFF))
                            .clickable { onChip(i) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (on) Color.Black else Color.White, maxLines = 1)
                    }
                }
            }
            when {
                shown.isEmpty() && (feed.loading || feed.loadingMore) -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
                shown.isEmpty() -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("Không có video nào.", fontSize = 14.sp, color = Color(0xFFCCCCCC))
                }
                else -> LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(shown, key = { it.id }) { v -> MoreVideoCard(v, cardWidth) { onOpen(v) } }
                    if (feed.loadingMore) item(key = "more") {
                        Box(Modifier.width(60.dp).aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp).fillMaxWidth())
        }
    }
}

@Composable
private fun MoreVideoCard(video: VideoItem, width: Dp, onClick: () -> Unit) {
    Column(Modifier.width(width).clip(RoundedCornerShape(12.dp)).clickable { onClick() }) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF222222))) {
            AsyncImage(
                model = video.hqThumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            video.duration?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Row(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
            ChannelAvatar(video, 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(video.channel, video.views, video.published.takeIf { publishedIn(video.views) == null }).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(sub, fontSize = 12.sp, color = Color(0xFFAAAAAA), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

/** Dữ liệu + hành động cho lớp nút ở chế độ toàn màn hình (hàng tiêu đề trên cùng, hàng nút dưới cùng). */
private class FullscreenInfo(
    val title: String,
    val channel: String?,
    val liked: Boolean,
    val disliked: Boolean,
    val nextThumb: String?,
    val onLike: () -> Unit,
    val onDislike: () -> Unit,
    val onComments: () -> Unit,
    val onSave: () -> Unit,
    val onShare: () -> Unit,
    val onMore: () -> Unit,
    val onMoreVideos: () -> Unit,
    /** Vuốt dọc trên video lúc các nút đang ẩn: [onSwipeUpDrag] nhận px (âm = lên), [onSwipeUpEnd] nhận vận tốc px/s. */
    val onSwipeUpDrag: (Float) -> Unit,
    val onSwipeUpEnd: (Float) -> Unit
)

/** Khung "Video đề xuất" hiện đè lên video khi hết phim mà Tự động phát đang tắt: ảnh bìa + tiêu đề + kênh, nút Đóng / Phát ngay. */
@Composable
private fun EndSuggestion(video: VideoItem, onPlay: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Nuốt chạm để không lọt xuống lớp bật/tắt nút điều khiển của video.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = NoRipple) { },
        verticalArrangement = Arrangement.Center
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Video đề xuất", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
            ControlIcon(Lucide.X, "Đóng", 22.dp) { onClose() }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(172.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF222222))) {
                AsyncImage(
                    model = video.hqThumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                video.duration?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xB3000000))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                video.channel?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = NanColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(50)).background(Color(0xFF272727)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) { Text("Đóng", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White) }
            Box(
                Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(50)).background(Color.White).clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) { Text("Phát ngay", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black) }
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
 * Thanh tua dưới video, đo theo YouTube: dày 2dp, nền xám đặc #444444. Lớp nút đang hiện ([active]): phần đã xem đỏ #FF0033 + chấm đỏ.
 * Lớp nút ẩn: phần đã xem xám trắng #CCCCCC, không chấm. Chạm hoặc kéo để tua.
 *
 * Hoạt ảnh làm theo thanh tua của Short: đổi màu đỏ / xám trắng mượt, chấm phóng ra khi nút hiện và thu lại khi ẩn, kéo thì thanh dày lên
 * 3dp, chấm to hơn và phồng thêm theo tốc độ kéo (nhanh thì to, chậm / dừng thì nhỏ lại), và hiện "đang ở / tổng" ngay trên thanh.
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
    // Chấm phồng theo tốc độ kéo, xẹp lại sau 120ms không di chuyển (cùng cách với thanh tua của Short).
    var knobBoost by remember { mutableFloatStateOf(0f) }
    var lastMove by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lastMove) {
        delay(120)
        knobBoost = 0f
    }
    val dragging = drag != null
    val knobScale by animateFloatAsState(1f + knobBoost * 1.2f, label = "watchKnob")
    val thumbAmount by animateFloatAsState(if (active || dragging) 1f else 0f, tween(160), label = "watchThumb")
    val trackHeight by animateDpAsState(if (dragging) 3.dp else 2.dp, tween(120), label = "watchTrack")
    val accent by animateColorAsState(if (active || dragging) Color(0xFFFF0033) else Color(0xFFCCCCCC), tween(180), label = "watchAccent")
    val played = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val frac = drag ?: played
    val buffered = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(modifier.fillMaxWidth()) {
        drag?.let { f ->
            Text(
                buildAnnotatedString {
                    append(clock((durationMs * f).toLong()))
                    withStyle(SpanStyle(color = Color(0xB3FFFFFF))) { append(" / ${clock(durationMs)}") }
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp)
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
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
                        onHorizontalDrag = { change, dx ->
                            drag = (change.position.x / size.width).coerceIn(0f, 1f)
                            val dt = (change.uptimeMillis - change.previousUptimeMillis).coerceAtLeast(1L)
                            knobBoost = (kotlin.math.abs(dx) / dt / 2f).coerceIn(0f, 1f)
                            lastMove = change.uptimeMillis
                        }
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val track = trackHeight.toPx()
                val top = size.height - track
                drawRect(Color(0xFF444444), Offset(0f, top), Size(size.width, track))
                drawRect(Color(0x33FFFFFF), Offset(0f, top), Size(size.width * buffered, track))
                drawRect(accent, Offset(0f, top), Size(size.width * frac, track))
                if (thumbAmount > 0.01f) {
                    val radius = (if (dragging) 7.dp.toPx() * knobScale else 6.dp.toPx()) * thumbAmount
                    drawCircle(accent, radius, Offset((size.width * frac).coerceIn(radius, size.width - radius), top + track / 2f))
                }
            }
        }
    }
}
