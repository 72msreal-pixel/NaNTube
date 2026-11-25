package com.nan.nantube.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Outline
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.nan.nantube.MainActivity
import com.nan.nantube.R
import com.nan.nantube.data.PipPrefs
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cửa sổ Short nổi do app tự vẽ (bật ở Cài đặt → Thử nghiệm các tính năng mới → "Cải thiện trải nghiệm dùng PiP"): thay cho PiP của
 * hệ thống nên chạm vào video là ẩn / hiện nút ngay, kéo đi đâu cũng được. Dùng chung phiên [ShortsMini] (danh sách, vị trí, tạm dừng)
 * và các ô phát của [ShortsPool], nên quay lại app là thẻ mini trong app tiếp tục đúng chỗ.
 */
object PipOverlay {
    /** Cửa sổ nổi đang giữ việc phát: các bộ phát trong app không được dừng video khi app rời màn hình. */
    @Volatile
    var active = false
        private set

    /** Có dùng được không: đã bật cài đặt, có quyền vẽ và có Short đang ở chế độ mini. */
    fun available(context: Context): Boolean =
        PipPrefs.enhanced && PipPrefs.canDraw(context) && ShortsMini.open && !ShortsMini.paused

    fun start(context: Context): Boolean {
        if (!available(context)) return false
        active = true
        return try {
            val intent = Intent(context, PipOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            true
        } catch (_: Exception) {
            active = false
            false
        }
    }

    /** App về màn hình trước: bỏ cửa sổ nổi (thẻ mini trong app hiện lại). */
    fun stop(context: Context) {
        if (!active) return
        active = false
        runCatching { context.stopService(Intent(context, PipOverlayService::class.java)) }
    }

    internal fun clear() {
        active = false
    }
}

class PipOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private lateinit var wm: WindowManager
    private var root: OverlayRoot? = null
    private var card: FrameLayout? = null
    private var tilt = 0f
    private var expanded = false
    private var tiltAnim: android.animation.ValueAnimator? = null
    private var params: WindowManager.LayoutParams? = null
    private var vertical = true
    private var closing = false
    private var settle: android.animation.ValueAnimator? = null
    private val dm = DisplayMetrics()

    private val density get() = resources.displayMetrics.density
    private var sizeScale = lastScale
    private var resizeAnim: android.animation.ValueAnimator? = null
    private val baseW get() = (if (vertical) 144f else 216f) * density
    private val baseH get() = (if (vertical) 144f * 16f / 9f else 216f * 9f / 16f) * density
    private val winW get() = (baseW * sizeScale).roundToInt()
    private val winH get() = (baseH * sizeScale).roundToInt()

    /** Lớn nhất: chừa lề hai bên và không cao quá 70% màn hình. */
    private fun maxScale(): Float {
        val m = realMetrics()
        return min(MAX_SCALE, min((m.widthPixels - 2 * margin) / baseW, m.heightPixels * 0.7f / baseH)).coerceAtLeast(MIN_SCALE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (root == null) {
            if (!PipPrefs.canDraw(this) || !ShortsMini.open) {
                shutdown()
                return START_NOT_STICKY
            }
            try {
                show()
            } catch (_: Exception) {
                shutdown()
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Short nổi", NotificationManager.IMPORTANCE_LOW))
        }
        val tap = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(this, 0, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pip_play)
            .setContentTitle("NaNTube")
            .setContentText("Đang phát Short trong cửa sổ nổi")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            // Không lên được dịch vụ nền: vẫn thử vẽ cửa sổ, hệ thống có thể dừng sớm.
        }
    }

    @Suppress("DEPRECATION")
    private fun realMetrics(): DisplayMetrics {
        wm.defaultDisplay.getRealMetrics(dm)
        return dm
    }

    private fun systemBar(name: String): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private val margin get() = (12 * density).roundToInt()
    private val minY get() = systemBar("status_bar_height") + (8 * density).roundToInt()
    private val homeX get() = realMetrics().widthPixels - winW - margin
    private val leftX get() = margin
    private val homeY get() = realMetrics().heightPixels - winH - systemBar("navigation_bar_height") - (96 * density).roundToInt()

    private fun show() {
        val themed = ContextThemeWrapper(this, R.style.Theme_NaNTube)
        val host = object : OverlayRoot.Host {
            override fun x() = params?.x ?: 0
            override fun y() = params?.y ?: 0
            override fun moveTo(x: Int, y: Int) = place(x, y)
            override fun onGrab() {
                settle?.cancel()
                resizeAnim?.cancel()
            }
            override fun onRelease(vx: Float, vy: Float) = release(vx, vy)
            override fun onPinchStart() {
                tiltAnim?.cancel()
                resizeAnim?.cancel()
                expanded = true
                commit()
            }
            override fun onPan(dx: Int, dy: Int) {
                params?.let { place(it.x + dx, it.y + dy) }
            }
            override fun onScale(factor: Float) = setScale(sizeScale * factor)
            override fun onRotate(deg: Float) {
                tilt += deg
                card?.rotation = tilt
            }
            override fun onPinchEnd() = straighten()
        }
        val view = OverlayRoot(themed, host)
        val compose = ComposeView(themed).apply {
            setContent {
                NaNTubeTheme {
                    OverlayContent(
                        onClose = { closeShrink() },
                        onExpand = { expand() },
                        onSettings = { openSettings() },
                        onResize = { toggleSize() },
                        onGone = { shutdown() },
                        onShape = { v -> reshape(v) }
                    )
                }
            }
        }
        val body = FrameLayout(themed)
        body.addView(compose, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        view.addView(body, FrameLayout.LayoutParams(winW, winH, Gravity.CENTER))
        card = body
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        body.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, 12 * density)
            }
        }
        body.clipToOutline = true

        val type = overlayType()
        val lp = WindowManager.LayoutParams(
            winW, winH, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = lastX ?: homeX
            y = lastY ?: homeY
        }
        params = lp
        root = view
        view.alpha = 0f
        view.scaleX = 0.9f
        view.scaleY = 0.9f
        wm.addView(view, lp)
        registry.currentState = Lifecycle.State.STARTED
        registry.currentState = Lifecycle.State.RESUMED
        // Vào: nở từ 90% và hiện dần (emphasized decelerate, như thẻ mini trong app / meetube).
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(250).setInterpolator(EMPH_DECEL).start()
    }

    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

    private fun place(x: Int, y: Int) {
        val lp = params ?: return
        val m = realMetrics()
        lp.x = x.coerceIn(0, max(0, m.widthPixels - winW))
        lp.y = y.coerceIn(0, max(0, m.heightPixels - winH))
        lastX = lp.x
        lastY = lp.y
        commit()
    }

    /**
     * Đẩy vị trí / cỡ thật của cửa sổ lên hệ thống. [params] là khung của thẻ video; đang xoay thì cửa sổ được nới thành hình vuông
     * ôm trọn thẻ (thẻ xoay ra ngoài khung cửa sổ sẽ bị cắt góc), thẻ vẫn nằm đúng chỗ.
     */
    private fun commit() {
        val view = root ?: return
        val lp = params ?: return
        val wl = WindowManager.LayoutParams().also { it.copyFrom(lp) }
        if (expanded) {
            val side = ceil(hypot(lp.width.toDouble(), lp.height.toDouble())).toInt()
            wl.x = lp.x - (side - lp.width) / 2
            wl.y = lp.y - (side - lp.height) / 2
            wl.width = side
            wl.height = side
        }
        card?.let { c ->
            val cl = c.layoutParams
            if (cl.width != lp.width || cl.height != lp.height) {
                cl.width = lp.width
                cl.height = lp.height
                c.layoutParams = cl
            }
        }
        runCatching { wm.updateViewLayout(view, wl) }
    }

    /** Thả tay sau khi xoay: thẻ quay về thẳng theo đường ngắn nhất rồi cửa sổ thu lại đúng cỡ thẻ, hít về góc. */
    private fun straighten() {
        val body = card
        val from = tilt
        val to = (from / 360f).roundToInt() * 360f
        tiltAnim?.cancel()
        settleAfterResize()
        if (body == null || abs(to - from) < 0.5f) {
            tilt = 0f
            body?.rotation = 0f
            expanded = false
            commit()
            return
        }
        tiltAnim = android.animation.ValueAnimator.ofFloat(from, to).apply {
            duration = 260
            interpolator = EMPH_DECEL
            addUpdateListener {
                tilt = it.animatedValue as Float
                body.rotation = tilt
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    cancelled = true
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (cancelled) return
                    tilt = 0f
                    body.rotation = 0f
                    expanded = false
                    commit()
                }
            })
            start()
        }
    }

    /** Đổi cỡ cửa sổ quanh tâm hiện tại (chụm hai ngón / nút đổi cỡ). */
    private fun setScale(target: Float) {
        val lp = params ?: return
        val s = target.coerceIn(MIN_SCALE, maxScale())
        if (abs(s - sizeScale) < 1e-4f) return
        val cx = lp.x + lp.width / 2f
        val cy = lp.y + lp.height / 2f
        sizeScale = s
        lastScale = s
        lp.width = winW
        lp.height = winH
        place((cx - winW / 2f).roundToInt(), (cy - winH / 2f).roundToInt())
    }

    /** Chụm xong / đổi cỡ xong: kéo cửa sổ vào lại trong màn hình rồi hít về góc gần nhất. */
    private fun settleAfterResize() {
        val lp = params ?: return
        val m = realMetrics()
        place(lp.x.coerceIn(0, max(0, m.widthPixels - winW)), lp.y)
        release(0f, 0f)
    }

    /** Chạm đúp / nút đổi cỡ: đang nhỏ thì phóng to, đang lớn thì về cỡ gốc. */
    private fun toggleSize() {
        if (closing) return
        val from = sizeScale
        val big = min(LARGE_SCALE, maxScale())
        val to = if (from < (1f + big) / 2f) big else 1f
        if (abs(to - from) < 1e-3f) return
        settle?.cancel()
        resizeAnim?.cancel()
        resizeAnim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            interpolator = EMPH_DECEL
            addUpdateListener { setScale(from + (to - from) * (it.animatedValue as Float)) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    cancelled = true
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!cancelled) settleAfterResize()
                }
            })
            start()
        }
    }

    /** Nút bánh răng: về app, mở thẳng Cài đặt → Thử nghiệm các tính năng mới (chỗ tắt / bật cửa sổ nổi). */
    private fun openSettings() {
        if (closing) return
        closing = true
        PipPrefs.requestSettings()
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (_: Exception) {
        }
        shutdown()
    }

    /** Video đổi hướng (dọc / ngang): đổi cỡ cửa sổ, giữ góc đang đứng. */
    private fun reshape(isVertical: Boolean) {
        if (isVertical == vertical) return
        vertical = isVertical
        sizeScale = sizeScale.coerceIn(MIN_SCALE, maxScale())
        val lp = params ?: return
        lp.width = winW
        lp.height = winH
        place(lp.x.coerceAtMost(max(leftX, homeX)), lp.y.coerceAtMost(homeY))
    }

    private fun release(vx: Float, vy: Float) {
        val lp = params ?: return
        // Về góc gần nhất theo hướng ném: trái / phải tự hít, dọc nằm đúng chỗ thả, gần mép trên / dưới thì hít vào mép.
        val throwS = 0.18f
        val centerX = lp.x + winW / 2f + vx * throwS
        val x = if (abs(centerX - (homeX + winW / 2f)) < abs(centerX - (leftX + winW / 2f))) homeX else leftX
        val top = min(minY, homeY)
        var y = (lp.y + vy * throwS).roundToInt().coerceIn(top, max(top, homeY))
        val snap = (48 * density).roundToInt()
        if (homeY - y < snap) y = homeY else if (y - top < snap) y = top
        springTo(lp.x, lp.y, x, y, vx, vy)
    }

    /** Lò xo (độ cứng 550, tắt dần 0.75 như SNAP của meetube) từ chỗ thả tới góc, tính công thức đóng. */
    private fun springTo(fromX: Int, fromY: Int, toX: Int, toY: Int, vx: Float, vy: Float) {
        val sx = SpringCurve(fromX.toFloat(), toX.toFloat(), vx)
        val sy = SpringCurve(fromY.toFloat(), toY.toFloat(), vy)
        val total = max(sx.settleMs, sy.settleMs).coerceAtLeast(1L)
        settle?.cancel()
        settle = android.animation.ValueAnimator.ofFloat(0f, total.toFloat()).apply {
            duration = total
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = (it.animatedValue as Float) / 1000f
                place(sx.at(t).roundToInt(), sy.at(t).roundToInt())
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (settle === animation) place(toX, toY)
                }
            })
            start()
        }
    }

    /** Nút X: thẻ co về 90% và mờ đi (emphasized accelerate), tiếng tắt ngay, rồi mới đóng phiên. */
    private fun closeShrink() {
        if (closing) return
        closing = true
        ShortsMini.paused = true
        ShortsPool.pauseAll()
        settle?.cancel()
        root?.animate()?.alpha(0f)?.scaleX(0.9f)?.scaleY(0.9f)?.setDuration(200)?.setInterpolator(EMPH_ACCEL)
            ?.withEndAction { finishClose() }?.start() ?: finishClose()
    }

    private fun finishClose() {
        ShortsMini.close()
        shutdown()
    }

    /** Nút mở rộng: về app, xem Short toàn màn hình đúng video đang phát. */
    private fun expand() {
        if (closing) return
        closing = true
        ShortsMini.expand()
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (_: Exception) {
        }
        shutdown()
    }

    private fun shutdown() {
        settle?.cancel()
        settle = null
        resizeAnim?.cancel()
        resizeAnim = null
        root?.let { view ->
            root = null
            runCatching { wm.removeView(view) }
        }
        PipOverlay.clear()
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        settle?.cancel()
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        PipOverlay.clear()
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "pip_overlay"
        private const val NOTIFICATION_ID = 7421
        private val EMPH_DECEL = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        private val EMPH_ACCEL = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)

        // Nhớ chỗ cửa sổ nằm giữa các lần hiện.
        private var lastX: Int? = null
        private var lastY: Int? = null
        private var lastScale = 1f
        private const val MIN_SCALE = 0.85f
        private const val MAX_SCALE = 2.5f
        private const val LARGE_SCALE = 2f
    }
}

/** Lò xo tắt dần (độ cứng 550, hệ số tắt 0.75) bắt đầu từ vận tốc ngón tay: giải theo công thức đóng như Motion.Spring của meetube. */
private class SpringCurve(private val from: Float, private val to: Float, private val velocity: Float) {
    private val omega = sqrt(550.0)
    private val zeta = 0.75
    private val x0 = (from - to).toDouble()
    val settleMs: Long

    init {
        var settled = 0L
        if (abs(to - from) > 1e-3f || abs(velocity) > 1f) {
            for (ms in 1..2000) if (abs(offset(ms / 1000.0)) > 0.5) settled = ms.toLong()
        }
        settleMs = settled
    }

    private fun offset(t: Double): Double {
        val damped = omega * sqrt(1.0 - zeta * zeta)
        return exp(-zeta * omega * t) * (x0 * cos(damped * t) + (velocity + zeta * omega * x0) / damped * sin(damped * t))
    }

    fun at(t: Float): Float = (to + offset(t.toDouble())).toFloat()
}

/**
 * Gốc của cửa sổ nổi: nhận cú vuốt / kéo ở mức View (toạ độ màn hình, nên không giật khi chính cửa sổ đang dịch chuyển) và chỉ chặn
 * khi ngón đã đi quá ngưỡng. Chặn rồi thì Compose bên trong nhận CANCEL, nên kéo xong không bao giờ bị tính là bấm vào video.
 */
private class OverlayRoot(context: Context, private val host: Host) : FrameLayout(context) {
    interface Host {
        fun x(): Int
        fun y(): Int
        fun moveTo(x: Int, y: Int)
        fun onGrab()
        fun onRelease(vx: Float, vy: Float)
        fun onPinchStart()
        fun onScale(factor: Float)
        fun onRotate(deg: Float)
        fun onPan(dx: Int, dy: Int)
        fun onPinchEnd()
    }

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var velocity: VelocityTracker? = null
    private var pinching = false
    private var lastAngle = 0f
    private var lastMidX = 0f
    private var lastMidY = 0f
    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (pinching) host.onScale(detector.scaleFactor)
            return true
        }
    })

    private fun track(e: MotionEvent) {
        val raw = MotionEvent.obtain(e)
        raw.setLocation(e.rawX, e.rawY)
        velocity?.addMovement(raw)
        raw.recycle()
    }

    private fun angle(e: MotionEvent): Float =
        Math.toDegrees(atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat()

    // Toạ độ màn hình của trung điểm hai ngón (rawX / rawY là của ngón đầu).
    private fun midX(e: MotionEvent) = e.rawX + (e.getX(1) - e.getX(0)) / 2f
    private fun midY(e: MotionEvent) = e.rawY + (e.getY(1) - e.getY(0)) / 2f

    private fun endPinch() {
        pinching = false
        host.onPinchEnd()
    }

    /** Hai ngón: chụm = đổi cỡ, xoay = nghiêng thẻ (tự thẳng lại khi thả), dịch cả hai ngón = kéo cửa sổ. Trả true khi đang ở chế độ hai ngón. */
    private fun pinch(e: MotionEvent): Boolean {
        scaler.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount == 2 && !pinching) {
                pinching = true
                dragging = false
                lastAngle = angle(e)
                lastMidX = midX(e)
                lastMidY = midY(e)
                host.onGrab()
                host.onPinchStart()
            }
            MotionEvent.ACTION_MOVE -> if (pinching && e.pointerCount >= 2) {
                val now = angle(e)
                var d = now - lastAngle
                while (d > 180f) d -= 360f
                while (d < -180f) d += 360f
                lastAngle = now
                host.onRotate(d)
                val mx = midX(e)
                val my = midY(e)
                host.onPan((mx - lastMidX).roundToInt(), (my - lastMidY).roundToInt())
                lastMidX = mx
                lastMidY = my
            }
            MotionEvent.ACTION_POINTER_UP -> if (pinching && e.pointerCount == 2) endPinch()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (pinching) endPinch()
        }
        return pinching
    }

    private fun begin(e: MotionEvent) {
        downX = e.rawX
        downY = e.rawY
        startX = host.x()
        startY = host.y()
        dragging = false
        velocity?.recycle()
        velocity = VelocityTracker.obtain()
        track(e)
    }

    /**
     * Kéo một ngón. Dùng chung cho onIntercept và onTouch: con (Compose) không nhận DOWN thì onIntercept không được gọi nữa,
     * lúc đó chỉ onTouch thấy cú kéo, nên cả hai nơi đều phải bắt được.
     */
    private fun drag(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(e)
            MotionEvent.ACTION_MOVE -> {
                if (velocity == null) begin(e)
                track(e)
                if (!dragging && e.pointerCount == 1 && hypot(e.rawX - downX, e.rawY - downY) > slop) {
                    dragging = true
                    host.onGrab()
                    startX = host.x()
                    startY = host.y()
                    downX = e.rawX
                    downY = e.rawY
                }
                if (dragging) host.moveTo(startX + (e.rawX - downX).roundToInt(), startY + (e.rawY - downY).roundToInt())
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    var vx = 0f
                    var vy = 0f
                    if (e.actionMasked == MotionEvent.ACTION_UP) {
                        track(e)
                        velocity?.let {
                            it.computeCurrentVelocity(1000)
                            vx = it.xVelocity
                            vy = it.yVelocity
                        }
                    }
                    velocity?.recycle()
                    velocity = null
                    host.onRelease(vx, vy)
                } else {
                    velocity?.recycle()
                    velocity = null
                }
            }
        }
        return dragging
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (pinch(e)) return true
        return drag(e)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (pinch(e)) return true
        drag(e)
        return true
    }
}

@Composable
private fun OverlayContent(onClose: () -> Unit, onExpand: () -> Unit, onSettings: () -> Unit, onResize: () -> Unit, onGone: () -> Unit, onShape: (Boolean) -> Unit) {
    val video = ShortsMini.current
    if (video == null) {
        LaunchedEffect(Unit) { onGone() }
        return
    }
    val list = ShortsMini.list
    val idx = ShortsMini.index

    // Giữ ô phát của video này + kế + trước, chỉ phát video đang hiện.
    LaunchedEffect(video.id, list) {
        ShortsPool.window(listOfNotNull(list.getOrNull(idx), list.getOrNull(idx + 1), list.getOrNull(idx - 1)))
    }
    val slot = ShortsPool.slot(video.id)
    LaunchedEffect(video.id, slot, ShortsMini.paused) { ShortsPool.setActive(video.id, !ShortsMini.paused) }
    val ended = slot?.ended == true
    LaunchedEffect(ended, video.id) { if (ended && ShortsPool.autoScroll) ShortsMini.next() }
    // Vào từ màn Short toàn màn hình thì ô phát vừa bị bộ phát cũ dừng: giành lại vài lần cho chắc.
    LaunchedEffect(video.id) {
        repeat(6) {
            kotlinx.coroutines.delay(500)
            ShortsPool.setActive(video.id, !ShortsMini.paused)
        }
    }
    val vertical = slot?.vertical ?: true
    LaunchedEffect(vertical) { onShape(vertical) }

    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, ShortsMini.paused, video.id) {
        if (controlsVisible && !ShortsMini.paused) {
            kotlinx.coroutines.delay(3000)
            controlsVisible = false
        }
    }
    val controlsAlpha by animateFloatAsState(
        targetValue = if (controlsVisible) 1f else 0f,
        animationSpec = tween(if (controlsVisible) MINI_FADE_IN_MS else MINI_FADE_OUT_MS, easing = StandardEasing),
        label = "overlayControls"
    )

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            // Chạm vào video (chỗ không có nút) là ẩn / hiện nút, đúng thứ PiP của hệ thống không làm được.
            .pointerInput(Unit) { detectTapGestures(onTap = { controlsVisible = !controlsVisible }, onDoubleTap = { onResize() }) }
    ) {
        AsyncImage(
            model = video.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.shorts_player_view, null) as PlayerView).also { it.player = slot?.player }
            },
            update = {
                it.player = slot?.player
                it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )
        if (controlsAlpha > 0.001f) {
            val enabled = controlsVisible
            BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = controlsAlpha }) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0x99000000), Color(0x40000000), Color(0x99000000)))
                    )
                )
                // Bố cục như PiP hệ thống: bánh răng góc trên trái; đổi cỡ / mở rộng / đóng góc trên phải; Trước / Phát / Tiếp giữa dưới.
                // Nút giữ cỡ cố định ở mọi cỡ cửa sổ, chỉ khi cửa sổ nhỏ hơn mức đủ chỗ thì nút co lại vừa khung.
                val top = minOf(32.dp, (maxWidth - 8.dp) / 4)
                val topK = top / 32.dp
                MiniButton(Modifier.align(Alignment.TopStart).padding(top = 8.dp, start = 7.dp), top, enabled, onSettings) {
                    MiniIcon(Lucide.Settings, 20.dp * topK)
                }
                Row(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniButton(Modifier, top, enabled, onResize) { MiniIcon(MiniResize, 20.dp * topK) }
                    MiniButton(Modifier, top, enabled, onExpand) { MiniIcon(MiniExpand, 20.dp * topK) }
                    MiniButton(Modifier, top, enabled, onClose) { MiniIcon(MiniClose, 26.dp * topK) }
                }
                val bottom = minOf(39.dp, maxWidth / 3.4f)
                val bottomK = bottom / 39.dp
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniButton(Modifier, bottom, enabled, { ShortsMini.prev() }) { MiniIcon(MiniPrev, 18.dp * bottomK) }
                    MiniButton(Modifier, bottom, enabled, { ShortsMini.toggle() }) {
                        Crossfade(targetState = ShortsMini.paused, animationSpec = tween(MINI_FADE_IN_MS), label = "overlayPlayPause") { paused ->
                            Box(Modifier.size(bottom), contentAlignment = Alignment.Center) {
                                MiniIcon(if (paused) MiniPlay else MiniPause, 18.dp * bottomK)
                            }
                        }
                    }
                    MiniButton(Modifier, bottom, enabled, { ShortsMini.next() }) { MiniIcon(MiniNext, 18.dp * bottomK) }
                }
            }
        }
    }
}

/** Nút đổi cỡ (thay cho nút chia đôi màn hình của PiP hệ thống, cửa sổ tự vẽ không chia đôi được): khung lớn có khung nhỏ ở góc. */
private val MiniResize: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    miniIcon("MiniResize", false, "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M13 13h5v5h-5z")
}
