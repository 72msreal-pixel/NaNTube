package com.nan.nantube.ui

import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.nan.nantube.R
import com.nan.nantube.data.Account
import com.nan.nantube.data.VideoItem
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Cửa sổ Short nhỏ nổi trong app (như "mini card" của meetube): bấm nút Hình trong hình ở Short thì app tự về tab Main,
 * Short vẫn phát trong cửa sổ nhỏ kéo đi đâu cũng được. Rời app lúc này thì cửa sổ thành PiP của hệ thống (xem [PipState]).
 * Chỉ tắt khi bấm X, hoặc mở rộng để quay lại xem Short toàn màn hình.
 */
object ShortsMini {
    var list by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    var paused by mutableStateOf(false)

    /** Tăng mỗi lần mở: AppRoot thấy thì đưa app về tab Main. */
    var startSerial by mutableIntStateOf(0)
        private set

    val open: Boolean get() = list.isNotEmpty()
    val current: VideoItem? get() = list.getOrNull(index)

    fun start(videos: List<VideoItem>, at: Int) {
        if (videos.isEmpty()) return
        list = videos
        index = at.coerceIn(0, videos.lastIndex)
        paused = false
        startSerial++
        ShortsViewer.close()
    }

    fun close() {
        if (list.isNotEmpty()) list = emptyList()
    }

    fun next() {
        if (index + 1 < list.size) {
            index++
            paused = false
        }
    }

    fun prev() {
        if (index > 0) {
            index--
            paused = false
        }
    }

    fun toggle() {
        paused = !paused
    }

    /** Danh sách + vị trí lúc bấm mở rộng; AppRoot đọc khi [expandSerial] đổi. */
    var expandList: List<VideoItem> = emptyList()
        private set
    var expandIndex = 0
        private set
    var expandSerial by mutableIntStateOf(0)
        private set

    /** Mở rộng: tắt cửa sổ nhỏ, AppRoot mở tab Short đúng video đang phát (không có trong tab thì mở màn xem Short). */
    fun expand() {
        expandList = list
        expandIndex = index
        close()
        expandSerial++
    }
}

private val MINI_WIDTH = 132.dp
private val MINI_HEIGHT = 132.dp * 16 / 9

private fun miniIcon(name: String, filled: Boolean, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = if (filled) SolidColor(Color.White) else null,
                stroke = if (filled) null else SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()

private val MiniClose: ImageVector by lazy { miniIcon("MiniClose", false, "M18 6 6 18", "m6 6 12 12") }
private val MiniExpand: ImageVector by lazy { miniIcon("MiniExpand", false, "M15 3h6v6", "m21 3-7 7", "m3 21 7-7", "M9 21H3v-6") }
private val MiniPlay: ImageVector by lazy { miniIcon("MiniPlay", true, "M8,5v14l11,-7z") }
private val MiniPause: ImageVector by lazy { miniIcon("MiniPause", true, "M6,19h4V5H6v14zM14,5v14h4V5h-4z") }
private val MiniPrev: ImageVector by lazy { miniIcon("MiniPrev", true, "M6,6h2v12H6zM9.5,12l8.5,6V6z") }
private val MiniNext: ImageVector by lazy { miniIcon("MiniNext", true, "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z") }

/** Đặt cuối cây giao diện của AppRoot: tự ẩn khi không có Short nào đang ở chế độ cửa sổ nhỏ. */
@Composable
fun ShortsMiniWindow(account: Account?) {
    val video = ShortsMini.current ?: return
    MiniContent(video, account)
}

@Composable
private fun MiniContent(video: VideoItem, account: Account?) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val list = ShortsMini.list
    val idx = ShortsMini.index
    val inPip = PipState.inPip

    remember(account?.id) { ShortsPool.bind(context, account) }
    DisposableEffect(Unit) { onDispose { ShortsPool.pagerGone() } }

    // Giữ ô phát của video này + kế + trước (kể cả sau khi tab Short vừa nhả), rồi chỉ phát video đang hiện.
    LaunchedEffect(video.id, list) {
        ShortsPool.window(listOfNotNull(list.getOrNull(idx), list.getOrNull(idx + 1), list.getOrNull(idx - 1)))
    }
    val slot = ShortsPool.slot(video.id)
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(video.id, slot, ShortsMini.paused, tick) {
        ShortsPool.setActive(video.id, !ShortsMini.paused)
    }
    // Bật tự vuốt ở menu Short thì hết video là sang video kế như trong Short.
    val ended = slot?.ended == true
    LaunchedEffect(ended, video.id) { if (ended && ShortsPool.autoScroll) ShortsMini.next() }

    // Báo cho MainActivity: rời app lúc đang phát thì vào PiP; nút Trước / Phát-Tạm dừng / Tiếp của cửa sổ PiP điều khiển cửa sổ này.
    LaunchedEffect(video.id, ShortsMini.paused, slot?.vertical) {
        PipState.shortId = video.id
        PipState.playing = !ShortsMini.paused
        PipState.vertical = slot?.vertical ?: true
    }
    DisposableEffect(Unit) {
        val controls = PipControls(
            prev = { ShortsMini.prev() },
            next = { ShortsMini.next() },
            toggle = { ShortsMini.toggle() }
        )
        PipState.controls = controls
        onDispose {
            if (PipState.controls === controls) {
                PipState.controls = null
                PipState.shortId = null
                PipState.playing = false
            }
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // Đang phát mà rời app thì sắp vào PiP: không dừng ở ON_PAUSE; PiP đóng (X) hoặc app tắt hẳn thì dừng ở ON_STOP.
                Lifecycle.Event.ON_PAUSE -> { if (!PipState.armed && !PipState.inPip) ShortsPool.pauseAll() }
                Lifecycle.Event.ON_STOP -> { if (!PipState.inPip) ShortsPool.pauseAll() }
                Lifecycle.Event.ON_RESUME -> { tick++ }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(inPip) {
        if (!inPip && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) ShortsPool.pauseAll()
    }

    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, ShortsMini.paused, video.id) {
        if (controlsVisible && !ShortsMini.paused) {
            delay(3000)
            controlsVisible = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val w = with(density) { MINI_WIDTH.toPx() }
        val h = with(density) { MINI_HEIGHT.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val barPx = with(density) { 64.dp.toPx() }
        val navPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        val initial = Offset(maxW - w - margin, maxH - h - navPx - barPx - margin)
        var pos by remember { mutableStateOf(Offset.Unspecified) }
        fun clamp(o: Offset) = Offset(
            o.x.coerceIn(0f, (maxW - w).coerceAtLeast(0f)),
            o.y.coerceIn(0f, (maxH - h).coerceAtLeast(0f))
        )
        val p = clamp(if (pos.isSpecified) pos else initial)

        val cardModifier = if (inPip) {
            // Cửa sổ PiP: thẻ phình ra đúng bằng cửa sổ, chỉ còn video.
            Modifier.fillMaxSize()
        } else {
            Modifier
                .offset { IntOffset(p.x.roundToInt(), p.y.roundToInt()) }
                .size(MINI_WIDTH, MINI_HEIGHT)
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        pos = clamp((if (pos.isSpecified) pos else initial) + drag)
                    }
                }
        }

        Box(
            cardModifier
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures(onTap = { controlsVisible = !controlsVisible }) }
        ) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            val holder = remember { arrayOfNulls<PlayerView>(1) }
            DisposableEffect(Unit) { onDispose { holder[0]?.player = null } }
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.shorts_player_view, null) as PlayerView).also {
                        holder[0] = it
                        it.player = slot?.player
                    }
                },
                update = {
                    it.player = slot?.player
                    it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                modifier = Modifier.fillMaxSize()
            )

            if (controlsVisible && !inPip) {
                Box(Modifier.fillMaxSize().background(Color(0x66000000)))
                MiniButton(MiniClose, Modifier.align(Alignment.TopStart).padding(4.dp)) { ShortsMini.close() }
                MiniButton(MiniExpand, Modifier.align(Alignment.TopEnd).padding(4.dp)) { ShortsMini.expand() }
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniButton(MiniPrev, Modifier) { ShortsMini.prev() }
                    MiniButton(if (ShortsMini.paused) MiniPlay else MiniPause, Modifier.padding(horizontal = 6.dp), big = true) { ShortsMini.toggle() }
                    MiniButton(MiniNext, Modifier) { ShortsMini.next() }
                }
            }
        }
    }
}

@Composable
private fun MiniButton(icon: ImageVector, modifier: Modifier, big: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.size(if (big) 40.dp else 32.dp).clip(CircleShape).background(Color(0x66000000)).plainClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(if (big) 24.dp else 18.dp), tint = Color.White)
    }
}
