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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
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

    /** Giao [playback] cho thẻ nổi; Short đang ở thẻ nổi (nếu có) thì tắt để hai bên không giành âm thanh. */
    fun start(playback: WatchPlayback) {
        if (pb !== playback) pb?.release()
        if (ShortsMini.open && !ShortsMini.background) ShortsMini.close()
        pb = playback
        // Thẻ nổi vừa mở khi app còn hiện: bật thẻ media (nếu có phát nền) ngay, không đợi rời app.
        ShortsBackground.onPopupOpened()
    }

    /** Màn xem mở đúng video đang ở thẻ nổi: nhận lại bộ phát đang chạy, thẻ biến mất. */
    fun take(video: VideoItem): WatchPlayback? {
        val have = pb ?: return null
        if (have.video.id != video.id || have.released) return null
        pb = null
        ShortsBackground.onPopupClosed()
        return have
    }

    /** Tắt thẻ và dừng hẳn video. */
    fun close() {
        val have = pb ?: return
        pb = null
        have.release()
        ShortsBackground.onPopupClosed()
    }

    /** Nút mở rộng: mở lại màn xem đúng video này, bộ phát chạy liền mạch. */
    fun expand() {
        pb?.video?.let { WatchViewer.open(it) }
    }
}

private val WATCH_MINI_WIDTH = 240.dp
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
    // Phụ đề, cài đặt theo tài khoản, hẹn giờ ngủ, âm lượng ổn định, lịch sử xem: y như ở màn xem.
    val captionText = WatchPlaybackEffects(pb, account)
    var sheet by remember { mutableStateOf<WatchSheetPage?>(null) }

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
                it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
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

        if (f.controlsAlpha > 0.001f && !inPip && !pb.failed) {
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
                MiniButton(
                    Modifier.align(Alignment.Center).graphicsLayer {
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
