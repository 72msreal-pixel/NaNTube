package com.nan.nantube.ui

import android.view.LayoutInflater
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import com.nan.nantube.data.HistoryChip
import com.nan.nantube.data.SleepTimer
import com.nan.nantube.data.WatchPrefs
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.nan.nantube.R
import com.nan.nantube.data.Account
import com.nan.nantube.data.VideoItem
import kotlinx.coroutines.delay

/**
 * Thẻ nổi của video dài (bấm Hình trong hình ở menu ba chấm): màn xem đóng lại, app về tab đang mở trước đó, video vẫn phát tiếp trong
 * thẻ nhỏ kéo đi đâu cũng được (khung chung [MiniFrame] với Short). Bộ phát được giao nguyên cho thẻ, không nạp lại.
 * Tắt khi bấm X, mở video khác, hoặc mở rộng để quay lại màn xem. Rời app lúc đang phát thì thẻ thành cửa sổ PiP của hệ thống.
 */
internal object WatchMini {
    var pb by mutableStateOf<WatchPlayback?>(null)
        private set

    val open: Boolean get() = pb != null

    /** Thẻ vừa nhận video từ cú kéo-xuống thu nhỏ: hiện luôn (không nở / hiện dần) vì khung video vừa đáp đúng chỗ đó. */
    var instantEnter = false

    /** Dữ liệu trang xem (bình luận, thích, đề xuất...) của video đang ở thẻ nổi: mở rộng lại thì trả nguyên, khỏi tải lại. */
    private var stash: WatchStash? = null

    /** Các video đã xem trước video này (cũ nhất ở đầu): nút Trước của thẻ. */
    val history = mutableStateListOf<VideoItem>()

    /** Vị trí thẻ trên màn hình, giữ ở đây để đổi sang video khác ngay trong thẻ không làm thẻ nhảy về góc. */
    val cardPos = mutableStateOf(Offset.Unspecified)

    /**
     * Thẻ do cú kéo xuống / mũi tên thu nhỏ tạo ra: giao diện riêng (chỉ Tạm dừng + X ở hai góc, tự ẩn sau 3 giây, hiện lại khi kéo,
     * chạm vào video là mở rộng) và ném ngang thì thu vào mép thành nút kéo. Thẻ mở từ menu "Hình trong hình" thì giữ giao diện đầy đủ.
     */
    var dragStyle by mutableStateOf(false)
        private set

    /** Thẻ kiểu kéo đang thu ở mép nào (-1 trái, 1 phải, 0 không) và cao ở đâu: giữ ở đây để đổi video trong thẻ không làm mất. */
    val dock = MiniDock()

    /** Giao [playback] cho thẻ nổi; Short đang ở thẻ nổi (nếu có) thì tắt để hai bên không giành âm thanh. */
    fun start(playback: WatchPlayback, data: WatchStash? = null, prevs: List<VideoItem> = emptyList(), fromDrag: Boolean = false) {
        if (pb !== playback) pb?.release()
        if (ShortsMini.open && !ShortsMini.background) ShortsMini.close()
        // Thẻ nổi không tương tác được với các bảng phía sau: đóng các bảng cấp app (tải xuống, truyền lên TV) để không che / chặn thẻ.
        DownloadPanel.close()
        TvScreen.close()
        stash = data
        history.clear()
        history.addAll(prevs)
        cardPos.value = Offset.Unspecified
        dock.reset()
        dragStyle = fromDrag
        pb = playback
        // Thẻ nổi vừa mở khi app còn hiện: bật thẻ media (nếu có phát nền) ngay, không đợi rời app.
        ShortsBackground.onPopupOpened()
    }

    /** Màn xem mở đúng video đang ở thẻ nổi: nhận lại bộ phát đang chạy, thẻ biến mất. */
    fun take(video: VideoItem): WatchPlayback? {
        val have = pb ?: return null
        if (have.video.id != video.id || have.released) return null
        pb = null
        history.clear()
        dock.reset()
        ShortsBackground.onPopupClosed()
        return have
    }

    /** Màn xem nhận lại dữ liệu trang đã tải (chỉ khi đúng video này). */
    fun takeStash(videoId: String): WatchStash? {
        val have = stash
        stash = null
        return have?.takeIf { it.videoId == videoId }
    }

    /** Tắt thẻ và dừng hẳn video. */
    fun close() {
        val have = pb ?: return
        pb = null
        stash = null
        history.clear()
        dock.reset()
        have.release()
        ShortsBackground.onPopupClosed()
    }

    /** Nút mở rộng: mở lại màn xem đúng video này (kèm lịch sử Trước), bộ phát chạy liền mạch. */
    fun expand() {
        val playback = pb ?: return
        WatchViewer.restore(playback.video, history.toList(), playback.onTheGo)
    }

    /** Video đề xuất đầu tiên của video đang ở thẻ (null nếu danh sách chưa tải xong): nút Sau và tự phát. */
    fun nextVideo(): VideoItem? {
        val id = pb?.video?.id
        return stash?.related?.items?.firstOrNull { it.id != id }
    }

    /** Đổi sang [v] ngay trong thẻ: bộ phát mới, danh sách đề xuất mới, thẻ giữ nguyên chỗ. */
    private fun go(context: Context, account: Account?, v: VideoItem, pushCurrent: Boolean) {
        val cur = pb ?: return
        if (pushCurrent) {
            history.removeAll { it.id == cur.video.id }
            history.add(cur.video)
            if (history.size > 50) history.removeAt(0)
        }
        val np = WatchPlayback(context, v, account, initialOnTheGo = cur.onTheGo)
        np.start()
        val chips = mutableStateOf<List<HistoryChip>>(emptyList())
        val related = makeRelatedFeed(v, chips)
        related.load(account)
        stash = WatchStash(v.id, null, null, null, false, false, chips, related, makeChipFeed(v))
        instantEnter = true
        pb = np
        cur.release()
    }

    fun next(context: Context, account: Account?) {
        nextVideo()?.let { go(context, account, it, true) }
    }

    fun prev(context: Context, account: Account?) {
        val v = history.removeLastOrNull() ?: return
        go(context, account, v, false)
    }
}

internal val WATCH_MINI_WIDTH = 240.dp
private val WATCH_MINI_HEIGHT = WATCH_MINI_WIDTH * 9 / 16
private val MiniRed = Color(0xFFFF0000)
private const val SKIP_MS = 10_000L

/** Đặt cuối cây giao diện của AppRoot, cạnh [ShortsMiniWindow]: tự ẩn khi không có video dài nào ở thẻ nổi. */
@Composable
internal fun WatchMiniWindow(account: Account?) {
    val pb = WatchMini.pb ?: return
    key(pb) { WatchMiniContent(pb, account) }
}

@Composable
private fun WatchMiniContent(pb: WatchPlayback, account: Account?) {
    val video = pb.video
    val inPip = PipState.inPip
    val instant = remember { WatchMini.instantEnter.also { WatchMini.instantEnter = false } }
    // Phụ đề, cài đặt theo tài khoản, hẹn giờ ngủ, âm lượng ổn định, lịch sử xem: y như ở màn xem.
    val captionText = WatchPlaybackEffects(pb, account)
    var sheet by remember { mutableStateOf<WatchSheetPage?>(null) }
    val context = LocalContext.current

    // Hết video: hẹn giờ "Cuối video" thì dừng hẳn, không thì tự phát video kế nếu công tắc tự phát đang bật (như ở màn xem).
    val nextVideo = WatchMini.nextVideo()
    LaunchedEffect(pb.ended, WatchPrefs.autoplay, nextVideo?.id) {
        if (!pb.ended) return@LaunchedEffect
        if (SleepTimer.endOfVideo) {
            SleepTimer.expire()
            return@LaunchedEffect
        }
        if (WatchPrefs.autoplay && nextVideo != null) WatchMini.next(context, account)
    }

    // Báo cho MainActivity: rời app lúc đang phát thì vào PiP; hai nút bên của cửa sổ PiP lùi / tua 10 giây.
    LaunchedEffect(video.id, pb.wantPlay) {
        PipState.watchId = video.id
        PipState.watchPlaying = pb.wantPlay
        PipState.watchSeek = true
    }
    DisposableEffect(pb) {
        val controls = PipControls(
            prev = { pb.seekTo((pb.positionMs - SKIP_MS).coerceAtLeast(0L)) },
            next = { pb.seekTo((pb.positionMs + SKIP_MS).let { if (pb.durationMs > 0) it.coerceAtMost(pb.durationMs) else it }) },
            toggle = { pb.toggle() }
        )
        PipState.controls = controls
        PipState.watchStop = { WatchMini.close() }
        PipState.watchPb = pb
        onDispose {
            if (PipState.watchPb === pb) PipState.watchPb = null
            if (PipState.controls === controls) PipState.controls = null
            PipState.watchStop = null
            PipState.watchId = null
            PipState.watchPlaying = false
            PipState.watchSeek = false
        }
    }

    MiniFrame(
        width = WATCH_MINI_WIDTH,
        height = WATCH_MINI_HEIGHT,
        paused = !pb.wantPlay,
        changeKey = video.id,
        instantEnter = instant,
        posState = WatchMini.cardPos,
        dock = if (WatchMini.dragStyle) WatchMini.dock else null,
        showOnDrag = WatchMini.dragStyle,
        onTap = if (WatchMini.dragStyle) ({ WatchMini.expand() }) else null,
        onSilence = { pb.player.playWhenReady = false }, // tiếng tắt ngay, thẻ bay đi rồi mới đóng hẳn
        onClosed = { WatchMini.close() }
    ) { f ->
        val holder = remember { arrayOfNulls<PlayerView>(1) }
        DisposableEffect(Unit) { onDispose { holder[0]?.player = null } }
        // Màn xem vừa nhả mặt vẽ của bộ phát: gắn lại ngay, rồi thêm hai lần vì bên kia gỡ trễ hơn một nhịp.
        LaunchedEffect(pb) {
            for (wait in longArrayOf(0L, 150L, 500L)) {
                delay(wait)
                holder[0]?.rebind(pb.player)
            }
        }
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.shorts_player_view, null) as PlayerView).also {
                    holder[0] = it
                    it.rebind(pb.player)
                }
            },
            update = {
                it.rebind(pb.player)
                it.keepScreenOn = !pb.onTheGo
                it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
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
        if (pb.failed) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable { pb.retry() }, contentAlignment = Alignment.Center) {
                Text("Không phát được.\nChạm để thử lại.", fontSize = 11.sp, color = Color.White, textAlign = TextAlign.Center)
            }
        } else if (pb.buffering && !pb.ended && f.controlsAlpha < 0.5f) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
        }
        if (watchCaptionsOn(pb) && captionText != null && !pb.failed && !inPip) {
            CaptionOverlay(captionText, Modifier.align(Alignment.BottomCenter).padding(start = 8.dp, end = 8.dp, bottom = 8.dp), small = true)
        }

        if (f.controlsAlpha > 0.001f && !inPip && !pb.failed && WatchMini.dragStyle) {
            // Thẻ kiểu kéo: chỉ hai nút tròn ở hai góc (Tạm dừng | Đóng), không lớp phủ; chạm vào video là mở rộng.
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = f.controlsAlpha }) {
                MiniButton(Modifier.align(Alignment.TopStart).padding(6.dp), 40.dp, f.controlsEnabled, { pb.toggle() }) {
                    Crossfade(targetState = !pb.wantPlay || pb.ended, animationSpec = tween(MINI_FADE_IN_MS), label = "dragPlayPause") { paused ->
                        Box(Modifier.size(36.dp).background(Color(0x99000000), CircleShape), contentAlignment = Alignment.Center) {
                            MiniIcon(if (paused) MiniPlay else MiniPause, 22.dp)
                        }
                    }
                }
                MiniButton(Modifier.align(Alignment.TopEnd).padding(6.dp), 40.dp, f.controlsEnabled, f.closeShrink) {
                    Box(Modifier.size(36.dp).background(Color(0x99000000), CircleShape), contentAlignment = Alignment.Center) {
                        MiniIcon(MiniClose, 22.dp)
                    }
                }
            }
        } else if (f.controlsAlpha > 0.001f && !inPip && !pb.failed) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = f.controlsAlpha }) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x99000000), Color(0x40000000), Color(0x99000000)))))
                MiniButton(Modifier.align(Alignment.TopStart).padding(2.dp), 36.dp, f.controlsEnabled, f.closeShrink) {
                    MiniIcon(MiniClose, 22.dp)
                }
                Row(Modifier.align(Alignment.TopEnd).padding(2.dp)) {
                    // Cài đặt như nút bánh răng ở màn xem: chất lượng, tốc độ, bản âm thanh, phụ đề, tuỳ chọn khác.
                    MiniButton(Modifier, 36.dp, f.controlsEnabled, { sheet = WatchSheetPage.Main }) { MiniIcon(Lucide.Settings, 22.dp) }
                    MiniButton(Modifier, 36.dp, f.controlsEnabled, { WatchMini.expand() }) { MiniIcon(MiniExpand, 22.dp) }
                }
                // Hàng giữa như YouTube: Trước | Phát / Tạm dừng | Sau. Trước chỉ bấm được khi đã chuyển video trong phiên;
                // Sau chỉ bấm được khi đã có video đề xuất.
                val canPrev = WatchMini.history.isNotEmpty()
                val canNext = nextVideo != null
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniButton(
                        Modifier.graphicsLayer { alpha = if (canPrev) 1f else 0.38f },
                        40.dp, f.controlsEnabled && canPrev, { WatchMini.prev(context, account) }
                    ) { MiniIcon(MiniPrev, 26.dp) }
                    MiniButton(
                        Modifier.graphicsLayer {
                            scaleX = f.pop
                            scaleY = f.pop
                        },
                        48.dp, f.controlsEnabled, { pb.toggle() }
                    ) {
                        Crossfade(targetState = !pb.wantPlay || pb.ended, animationSpec = tween(MINI_FADE_IN_MS), label = "watchPlayPause") { paused ->
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                MiniIcon(if (paused) MiniPlay else MiniPause, 34.dp)
                            }
                        }
                    }
                    MiniButton(
                        Modifier.graphicsLayer { alpha = if (canNext) 1f else 0.38f },
                        40.dp, f.controlsEnabled && canNext, { WatchMini.next(context, account) }
                    ) { MiniIcon(MiniNext, 26.dp) }
                }
            }
        }

        // Thanh tiến độ mảnh ở đáy thẻ, luôn hiện (không tua được ở đây, muốn tua thì mở rộng).
        if (!inPip && pb.durationMs > 0) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(Color(0x66FFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((pb.positionMs.toFloat() / pb.durationMs).coerceIn(0f, 1f)).background(MiniRed))
            }
        }
    }

    // Bảng cài đặt hiện ngay trên thẻ, đóng / đổi trang như ở màn xem.
    sheet?.let { page ->
        WatchSettingsSheet(
            pb = pb,
            tracks = pb.tracks,
            page = page,
            onPage = { sheet = it },
            onClose = { sheet = null }
        )
    }
}
