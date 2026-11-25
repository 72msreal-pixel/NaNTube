package com.nan.nantube.ui

import android.os.SystemClock
import android.view.TextureView
import android.view.LayoutInflater
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.nan.nantube.R
import com.nan.nantube.data.Account
import com.nan.nantube.data.VideoItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
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

/**
 * Cờ dùng chung cho thẻ mini trong app và cửa sổ nổi: cú chạm hiện tại đã thành cú kéo / chụm thì KHÔNG được tính là bấm
 * (không ẩn / hiện nút, không bấm trúng nút Trước / Phát / Tiếp...). Hạ cờ ở lúc ngón vừa chạm xuống, nâng cờ ngay khi vượt ngưỡng kéo.
 */
internal object MiniTouch {
    @Volatile
    var dragged = false
}

/**
 * Gắn (lại) bộ phát vào mặt vẽ của PlayerView này. [PlayerView.setPlayer] bỏ qua khi bộ phát không đổi, nên sau khi cửa sổ nổi ngoài app
 * đã giành mặt vẽ của bộ phát (rồi bị gỡ), thẻ mini trong app vẫn tưởng mình đang giữ nó và đứng hình ở khung cũ: phải ép gắn lại mặt vẽ.
 */
internal fun PlayerView.rebind(target: Player?) {
    if (target == null) {
        player = null
        return
    }
    if (player !== target) player = target
    (videoSurfaceView as? TextureView)?.let { target.setVideoTextureView(it) }
}

internal val MINI_WIDTH = 132.dp
internal val MINI_HEIGHT = 132.dp * 16 / 9

// Chuyển động lấy theo meetube (Motion.java + MiniCardSwipe.java): cùng đường cong Material 3, cùng thời lượng, cùng lò xo.
internal val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
internal val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
internal val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
internal const val MINI_ENTER_MS = 250
internal const val MINI_EXIT_MS = 200
internal const val MINI_FADE_IN_MS = 150
internal const val MINI_FADE_OUT_MS = 90
private const val MINI_POP_MS = 260
private const val SNAP_STIFFNESS = 550f
private const val SNAP_DAMPING = 0.75f
private const val THROW_S = 0.18f
private const val FLING_VELOCITY_DP = 450f
/** Ném ngang nhanh cỡ này (và chủ yếu là ngang) thì đóng thẻ thay vì cất vào góc. */
private const val CLOSE_VELOCITY_DP = 1800f
private const val FLY_MIN_MS = 110
/** Chạm xảy ra trong khoảng này sau lần kéo cuối thì coi là đuôi của cú kéo, bỏ qua. */
private const val TAP_AFTER_DRAG_MS = 250L
private const val FLY_MAX_MS = 240

internal fun miniIcon(name: String, filled: Boolean, vararg paths: String): ImageVector =
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

internal val MiniClose: ImageVector by lazy { miniIcon("MiniClose", false, "M18 6 6 18", "m6 6 12 12") }
internal val MiniExpand: ImageVector by lazy { miniIcon("MiniExpand", false, "M15 3h6v6", "m21 3-7 7", "m3 21 7-7", "M9 21H3v-6") }
internal val MiniPlay: ImageVector by lazy { miniIcon("MiniPlay", true, "M8,5v14l11,-7z") }
internal val MiniPause: ImageVector by lazy { miniIcon("MiniPause", true, "M6,19h4V5H6v14zM14,5v14h4V5h-4z") }
internal val MiniPrev: ImageVector by lazy { miniIcon("MiniPrev", true, "M6,6h2v12H6zM9.5,12l8.5,6V6z") }
internal val MiniNext: ImageVector by lazy { miniIcon("MiniNext", true, "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z") }

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
    DisposableEffect(Unit) { onDispose { if (!PipOverlay.active) ShortsPool.pagerGone() } }

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
    LaunchedEffect(ended, video.id) { if (ended && ShortsPool.autoScroll && !PipOverlay.active) ShortsMini.next() }

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
                Lifecycle.Event.ON_PAUSE -> { if (!PipState.armed && !PipState.inPip && !PipOverlay.active) ShortsPool.pauseAll() }
                Lifecycle.Event.ON_STOP -> { if (!PipState.inPip && !PipOverlay.active) ShortsPool.pauseAll() }
                Lifecycle.Event.ON_RESUME -> { tick++ }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(inPip) {
        if (!inPip && !PipOverlay.active && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) ShortsPool.pauseAll()
    }

    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, ShortsMini.paused, video.id) {
        if (controlsVisible && !ShortsMini.paused) {
            delay(3000)
            controlsVisible = false
        }
    }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var closing by remember { mutableStateOf(false) }

    // Vào: thẻ nở từ 90% và hiện dần, đường cong "emphasized decelerate" của meetube (cửa sổ PiP thì hiện luôn).
    val cardScale = remember { Animatable(if (inPip) 1f else 0.9f) }
    val cardAlpha = remember { Animatable(if (inPip) 1f else 0f) }
    LaunchedEffect(Unit) {
        launch { cardScale.animateTo(1f, tween(MINI_ENTER_MS, easing = EmphasizedDecelerate)) }
        cardAlpha.animateTo(1f, tween(MINI_FADE_IN_MS, easing = StandardEasing))
    }

    // Chuyển sang Short khác (Trước / Tiếp / tự vuốt): thẻ nhún nhẹ để báo đã đổi (Motion.nudge của meetube).
    val nudge = remember { Animatable(1f) }
    var firstVideo by remember { mutableStateOf(true) }
    LaunchedEffect(video.id) {
        if (firstVideo) {
            firstVideo = false
            return@LaunchedEffect
        }
        nudge.snapTo(1f)
        nudge.animateTo(1f, keyframes {
            durationMillis = MINI_POP_MS
            0.965f at (MINI_POP_MS * 35 / 100) using StandardEasing
            1.015f at (MINI_POP_MS * 75 / 100) using StandardEasing
        })
    }

    // Nút Phát / Tạm dừng "pop": ép xuống 78%, nảy lên 112%, rồi về 100%.
    val pop = remember { Animatable(1f) }
    var firstPause by remember { mutableStateOf(true) }
    LaunchedEffect(ShortsMini.paused) {
        if (firstPause) {
            firstPause = false
            return@LaunchedEffect
        }
        pop.snapTo(1f)
        pop.animateTo(1f, keyframes {
            durationMillis = MINI_POP_MS
            0.78f at (MINI_POP_MS * 35 / 100) using StandardEasing
            1.12f at (MINI_POP_MS * 75 / 100) using StandardEasing
        })
    }

    // Nút mờ đi sau vài giây và hiện lại khi chạm: hiện 150ms, ẩn 90ms.
    val controlsAlpha by animateFloatAsState(
        targetValue = if (controlsVisible && !inPip) 1f else 0f,
        animationSpec = tween(if (controlsVisible) MINI_FADE_IN_MS else MINI_FADE_OUT_MS, easing = StandardEasing),
        label = "miniControls"
    )
    val controlsEnabled = controlsVisible && !inPip && !closing

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val w = with(density) { MINI_WIDTH.toPx() }
        val h = with(density) { MINI_HEIGHT.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val barPx = with(density) { 64.dp.toPx() }
        val navPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        val statusPx = WindowInsets.statusBars.getTop(density).toFloat()
        val homeX = maxW - w - margin
        val homeY = maxH - h - navPx - barPx - margin
        val leftX = margin
        val topY = min(statusPx + margin, max(homeY, 0f))
        val initial = Offset(homeX, homeY)

        // Vị trí khi nằm yên / đang kéo (pos) và khi đang bay về góc (settle, lò xo).
        var pos by remember { mutableStateOf(Offset.Unspecified) }
        val settle = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        var settling by remember { mutableStateOf(false) }
        var settleJob by remember { mutableStateOf<Job?>(null) }
        // Lần cuối ngón còn đang kéo thẻ: một cú kéo / vuốt không được tính là chạm vào video (không bật / tắt nút).
        var lastDragAt by remember { mutableStateOf(0L) }

        fun restPos() = if (pos.isSpecified) pos else initial
        fun visual(): Offset = if (settling) settle.value else restPos()
        fun clampDrag(o: Offset) = Offset(
            o.x.coerceIn(-w, maxW),
            o.y.coerceIn(0f, (maxH - h).coerceAtLeast(0f))
        )
        /** Thẻ thò ra khỏi mép bên nào bao nhiêu (âm: mép trái). */
        fun overhang(x: Float): Float = if (x < 0f) x else max(0f, x + w - maxW)

        fun settleTo(from: Offset, target: Offset, velocity: Offset) {
            settleJob?.cancel()
            settleJob = scope.launch {
                settle.snapTo(from)
                settling = true
                settle.animateTo(target, spring(dampingRatio = SNAP_DAMPING, stiffness = SNAP_STIFFNESS), velocity)
                pos = target
                settling = false
            }
        }

        fun flyAway(from: Offset, toX: Float, ms: Int) {
            if (closing) return
            closing = true
            ShortsMini.paused = true // tiếng tắt ngay, thẻ bay đi rồi mới đóng hẳn
            controlsVisible = false
            settleJob?.cancel()
            settleJob = scope.launch {
                settle.snapTo(from)
                settling = true
                launch { settle.animateTo(Offset(toX, from.y), tween(ms, easing = LinearEasing)) }
                cardAlpha.animateTo(0f, tween(ms, easing = LinearEasing))
                ShortsMini.close()
            }
        }

        /** Nút X: thẻ co về 90% và mờ đi (emphasized accelerate) rồi mới đóng. */
        fun closeShrink() {
            if (closing) return
            closing = true
            ShortsMini.paused = true
            controlsVisible = false
            scope.launch {
                launch { cardScale.animateTo(0.9f, tween(MINI_EXIT_MS, easing = EmphasizedAccelerate)) }
                cardAlpha.animateTo(0f, tween(MINI_EXIT_MS, easing = EmphasizedAccelerate))
                ShortsMini.close()
            }
        }

        fun release(vx: Float, vy: Float) {
            val from = restPos()
            val closeSpeed = CLOSE_VELOCITY_DP * density.density
            val thrownAway = abs(vx) > closeSpeed && abs(vx) > abs(vy) * 1.5f
            val over = overhang(from.x)
            if (thrownAway || abs(over) > w / 2f) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val dir = if (thrownAway) Math.signum(vx) else Math.signum(over)
                val target = dir * maxW
                val speed = max(abs(vx), FLING_VELOCITY_DP * density.density * 2f)
                val ms = (1000f * abs(target - from.x) / speed).roundToInt().coerceIn(FLY_MIN_MS, FLY_MAX_MS)
                flyAway(from, target, ms)
                return
            }
            // Về góc gần nhất theo hướng ném: trái / phải tự hít, dọc thì nằm đúng chỗ thả, gần mép trên / dưới thì hít vào mép.
            val centerX = from.x + w / 2f + vx * THROW_S
            val x = if (abs(centerX - (homeX + w / 2f)) < abs(centerX - (leftX + w / 2f))) homeX else leftX
            var y = (from.y + vy * THROW_S).coerceIn(topY, max(topY, homeY))
            val snap = with(density) { 48.dp.toPx() }
            if (homeY - y < snap) y = homeY else if (y - topY < snap) y = topY
            val target = Offset(x, y)
            pos = from
            settleTo(from, target, Offset(vx, vy))
        }

        val cardModifier = if (inPip) {
            // Cửa sổ PiP: thẻ phình ra đúng bằng cửa sổ, chỉ còn video.
            Modifier.fillMaxSize()
        } else {
            Modifier
                .offset { visual().let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
                .graphicsLayer {
                    val s = cardScale.value * nudge.value
                    scaleX = s
                    scaleY = s
                    // Thò ra khỏi mép càng nhiều càng mờ: thả ở đó là đóng.
                    val fade = 1f - 0.85f * min(1f, abs(overhang(visual().x)) / w)
                    alpha = cardAlpha.value * fade
                }
                .size(MINI_WIDTH, MINI_HEIGHT)
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    // Ngón vừa chạm xuống (pha Initial: chạy trước mọi con, đồng bộ): chưa phải kéo.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        MiniTouch.dragged = false
                    }
                }
                .pointerInput(maxW, maxH, homeX, homeY, topY) {
                    val tracker = VelocityTracker()
                    var total = Offset.Zero
                    var active = false
                    detectDragGestures(
                        onDragStart = {
                            MiniTouch.dragged = true
                            active = !closing
                            lastDragAt = SystemClock.uptimeMillis()
                            if (active) {
                                // Bắt lại thẻ đang bay về góc ngay dưới ngón tay.
                                settleJob?.cancel()
                                if (settling) {
                                    pos = settle.value
                                    settling = false
                                }
                                tracker.resetTracking()
                                total = Offset.Zero
                            }
                        },
                        onDrag = { change, drag ->
                            lastDragAt = SystemClock.uptimeMillis()
                            if (active) {
                                change.consume()
                                total += drag
                                tracker.addPosition(change.uptimeMillis, total)
                                pos = clampDrag(restPos() + drag)
                            }
                        },
                        onDragEnd = {
                            lastDragAt = SystemClock.uptimeMillis()
                            if (active) {
                                active = false
                                val v = tracker.calculateVelocity()
                                release(v.x, v.y)
                            }
                        },
                        onDragCancel = {
                            lastDragAt = SystemClock.uptimeMillis()
                            if (active) {
                                active = false
                                release(0f, 0f)
                            }
                        }
                    )
                }
        }

        Box(
            cardModifier
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures(onTap = {
                        val afterDrag = MiniTouch.dragged || SystemClock.uptimeMillis() - lastDragAt < TAP_AFTER_DRAG_MS
                        if (!closing && !afterDrag) controlsVisible = !controlsVisible
                    }) }
        ) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            val holder = remember { arrayOfNulls<PlayerView>(1) }
            DisposableEffect(Unit) { onDispose { holder[0]?.player = null } }
            // Về lại app (từ cửa sổ nổi ngoài app, PiP...): bộ phát có thể đã bị cửa sổ kia giành mặt vẽ hoặc đã đổi sang video khác.
            // Gắn lại ngay, rồi gắn thêm hai lần vì cửa sổ nổi được gỡ trễ hơn một nhịp sau onResume.
            LaunchedEffect(tick, slot) {
                for (wait in longArrayOf(0L, 150L, 500L)) {
                    delay(wait)
                    if (!PipOverlay.active) holder[0]?.rebind(slot?.player)
                }
            }
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.shorts_player_view, null) as PlayerView).also {
                        holder[0] = it
                        it.rebind(slot?.player)
                    }
                },
                update = {
                    it.rebind(slot?.player)
                    it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                modifier = Modifier.fillMaxSize()
            )

            if (controlsAlpha > 0.001f && !inPip) {
                // Không còn nền tròn đen sau từng nút: chỉ một lớp phủ tối dần ở đỉnh / đáy để biểu tượng trắng vẫn đọc được.
                Box(
                    Modifier.fillMaxSize().graphicsLayer { alpha = controlsAlpha }
                ) {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color(0x99000000), Color(0x40000000), Color(0x99000000)))
                        )
                    )
                    MiniButton(Modifier.align(Alignment.TopStart).padding(2.dp), 36.dp, controlsEnabled, ::closeShrink) {
                        MiniIcon(MiniClose, 22.dp)
                    }
                    MiniButton(Modifier.align(Alignment.TopEnd).padding(2.dp), 36.dp, controlsEnabled, { ShortsMini.expand() }) {
                        MiniIcon(MiniExpand, 22.dp)
                    }
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiniButton(Modifier, 36.dp, controlsEnabled, { ShortsMini.prev() }) { MiniIcon(MiniPrev, 24.dp) }
                        MiniButton(
                            Modifier.padding(horizontal = 2.dp).graphicsLayer {
                                scaleX = pop.value
                                scaleY = pop.value
                            },
                            44.dp, controlsEnabled, { ShortsMini.toggle() }
                        ) {
                            Crossfade(targetState = ShortsMini.paused, animationSpec = tween(MINI_FADE_IN_MS), label = "playPause") { paused ->
                                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                    MiniIcon(if (paused) MiniPlay else MiniPause, 32.dp)
                                }
                            }
                        }
                        MiniButton(Modifier, 36.dp, controlsEnabled, { ShortsMini.next() }) { MiniIcon(MiniNext, 24.dp) }
                    }
                }
            }
        }
    }
}

/** Nút trần, không nền: ngón bấm thì nút co lại rồi nảy về (lò xo), thả ra mới tính là bấm. */
@Composable
internal fun MiniButton(
    modifier: Modifier,
    size: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "miniPress"
    )
    val clickNow by rememberUpdatedState(onClick)
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                        onTap = { if (!MiniTouch.dragged) clickNow() }
                    )
                }
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
internal fun MiniIcon(icon: ImageVector, size: Dp) {
    Icon(icon, null, Modifier.size(size), tint = Color.White)
}
