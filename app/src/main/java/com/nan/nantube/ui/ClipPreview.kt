@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nan.nantube.ui

import android.view.LayoutInflater
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.nan.nantube.R
import com.nan.nantube.data.Account
import com.nan.nantube.data.ShortsMedia
import com.nan.nantube.data.ShortsPlayback
import com.nan.nantube.data.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val MAX_RELOADS = 2

/**
 * Xem trước thật của trang "Tải đoạn video": phát đúng đoạn [startMs]..[endMs] của video.
 * - Lấy luồng bằng đúng đường của trình phát Short ([ShortsPlayback] + [ShortsMedia], các nguồn của meetube) nên chạy cho cả
 *   tài khoản web lẫn tài khoản mã; chỉ tải mức hình nhỏ (SD) vì khung xem trước nhỏ.
 * - Phát tới hết đoạn thì tự dừng và về lại đầu đoạn.
 * - Đang phát mà đổi thời gian thì dừng ngay (rồi nhảy tới đầu đoạn mới).
 * - [valid] = false (số sai): không phát; nút Phát mờ đi và bấm không có tác dụng.
 * - Chỉ có nút Phát / Tạm dừng và thanh thời gian trắng (không kéo được), như ô Short nhỏ.
 */
@Composable
fun ClipPreview(
    video: VideoItem,
    account: Account?,
    startMs: Long,
    endMs: Long,
    valid: Boolean,
    label: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val startNow by rememberUpdatedState(startMs)
    val endNow by rememberUpdatedState(endMs)
    val validNow by rememberUpdatedState(valid)

    val player = remember {
        ExoPlayer.Builder(context.applicationContext)
            .setLoadControl(ShortsMedia.loadControl())
            .build().apply {
                playWhenReady = false
                repeatMode = Player.REPEAT_MODE_OFF
                trackSelectionParameters = trackSelectionParameters.buildUpon().setMaxVideoSizeSd().build()
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                    true
                )
            }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    var loaded by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var wantPlay by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var attempt by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    var dragging by remember { mutableStateOf(false) }
    val failedSources = remember { HashSet<String>() }
    val lastSource = remember { arrayOfNulls<String>(1) }

    // Về đầu đoạn và dừng (hết đoạn / hết video).
    fun rewind() {
        player.pause()
        player.seekTo(startNow.coerceAtLeast(0L))
        position = startNow
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                wantPlay = playWhenReady
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) loaded = true
                // Đoạn kéo tới hết video: ExoPlayer báo ENDED trước khi vòng kiểm tra kịp thấy mốc kết thúc.
                if (state == Player.STATE_ENDED) rewind()
            }

            override fun onPlayerError(error: PlaybackException) {
                // Link bị từ chối / hết hạn: đổi nguồn khác lấy link mới, quá số lần thì bỏ phần phát (vẫn còn ảnh bìa).
                if (attempt < MAX_RELOADS) {
                    lastSource[0]?.let { failedSources.add(it) }
                    ShortsPlayback.forget(video.id)
                    attempt++
                } else {
                    loadFailed = true
                    wantPlay = false
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Lấy luồng rồi nạp vào trình phát (không tự phát).
    LaunchedEffect(video.id, account?.id, attempt) {
        loaded = false
        loadFailed = false
        try {
            val plan = ShortsPlayback.plan(context, account, video, failedSources).await()
            lastSource[0] = plan.source
            val source = withContext(Dispatchers.IO) { ShortsMedia.buildSource(context, plan) }
            player.setMediaSource(source, if (validNow) startNow.coerceAtLeast(0L) else 0L)
            player.prepare()
            player.playWhenReady = false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loadFailed = true
        }
    }

    // Đổi thời gian (kéo thanh / gõ số) thì dừng ngay; thời gian hợp lệ thì nhảy tới đầu đoạn mới sau một nhịp ngắn.
    LaunchedEffect(startMs, endMs, valid, loaded) {
        player.pause()
        if (valid && loaded) {
            delay(150)
            player.seekTo(startMs)
            position = startMs
        }
    }

    // Đang phát: tới cuối đoạn thì dừng và về đầu đoạn.
    LaunchedEffect(wantPlay, startMs, endMs, valid) {
        if (!wantPlay) return@LaunchedEffect
        if (!validNow) {
            player.pause()
            return@LaunchedEffect
        }
        while (true) {
            val pos = player.currentPosition
            position = pos
            if (pos >= endNow) {
                rewind()
                break
            }
            delay(60)
        }
    }

    // Như ô Short nhỏ: đang phát thì nút tự ẩn sau 3 giây, chạm vào khung hình để hiện / ẩn; dừng thì luôn hiện.
    LaunchedEffect(controlsVisible, wantPlay) {
        if (!wantPlay) {
            controlsVisible = true
        } else if (controlsVisible) {
            delay(3000)
            controlsVisible = false
        }
    }

    // Rời app thì dừng.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun toggle() {
        if (!valid || !loaded) return
        if (player.playWhenReady) {
            player.pause()
        } else {
            val pos = player.currentPosition
            if (pos < startNow || pos >= endNow - 100 || player.playbackState == Player.STATE_ENDED) player.seekTo(startNow)
            player.play()
        }
    }

    // Tua trong phạm vi đoạn: [fraction] 0..1 là vị trí trên thanh. Không chạm tới đúng mốc cuối để khỏi bị tính là hết đoạn.
    fun seekToFraction(fraction: Float) {
        if (!valid || !loaded) return
        val target = startNow + ((endNow - startNow) * fraction.coerceIn(0f, 0.999f)).toLong()
        player.seekTo(target)
        position = target
    }

    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NanColors.Surface)
            .pointerInput(Unit) { detectTapGestures(onTap = { controlsVisible = !controlsVisible }) },
        contentAlignment = Alignment.BottomEnd
    ) {
        AsyncImage(
            model = video.coverThumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (!loadFailed) {
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.shorts_player_view, null) as PlayerView).also {
                        it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        it.rebind(player)
                    }
                },
                update = { it.rebind(player) },
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            label,
            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White,
            modifier = Modifier.padding(8.dp).background(Color(0xCC000000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
        )
        // Nút Phát / Tạm dừng: mờ đi khi chưa dùng được (số sai, chưa tải xong, lỗi).
        val usable = valid && loaded && !loadFailed
        val controlsAlpha by animateFloatAsState(if (controlsVisible) 1f else 0f, tween(if (controlsVisible) 150 else 90), label = "clipControls")
        Box(
            Modifier
                .align(Alignment.Center)
                .size(56.dp)
                .alpha((if (usable) 1f else 0.4f) * controlsAlpha)
                .clip(CircleShape)
                .background(Color(0x80000000))
                .clickable(enabled = usable && controlsVisible) { toggle() },
            contentAlignment = Alignment.Center
        ) {
            MiniIcon(if (wantPlay && valid) MiniPause else MiniPlay, 32.dp)
        }
        // Thanh thời gian trắng của đoạn: chạm hoặc kéo để tua trong đoạn (vùng chạm cao hơn nét vẽ cho dễ bấm).
        val span = (endMs - startMs).coerceAtLeast(1L)
        val fraction = if (valid) ((position - startMs).toFloat() / span).coerceIn(0f, 1f) else 0f
        val barHeight by animateFloatAsState(if (dragging) 8f else 3f, tween(120), label = "clipBar")
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(usable) {
                    if (usable) detectTapGestures(onTap = { offset -> seekToFraction(offset.x / size.width) })
                }
                .pointerInput(usable) {
                    if (usable) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                dragging = true
                                seekToFraction(offset.x / size.width)
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false }
                        ) { change, _ ->
                            change.consume()
                            seekToFraction(change.position.x / size.width)
                        }
                    }
                },
            contentAlignment = Alignment.BottomStart
        ) {
            Box(Modifier.fillMaxWidth().height(barHeight.dp).background(Color(0x55FFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Color.White))
            }
        }
    }
}
