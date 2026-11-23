package com.nan.nantube.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Hiệu ứng chạm dùng chung (lấy từ meetube: Motion.pop + Burst): nút nảy lên (nhỏ lại 78% rồi phồng 112% rồi về 100%),
 * kèm một vòng sáng loe ra mờ dần và chùm chấm confetti bắn ra từ mép nút.
 *
 * Cách dùng (chỗ nào cũng giống nhau):
 *     val effect = rememberTapEffect()
 *     Icon(..., Modifier.tapEffect(effect, ringColor = Color.Red))
 *     ...khi thao tác thành công:  effect.play()            // nảy + vòng + confetti
 *                                  effect.play(burst = false) // chỉ nảy (vd. bỏ thích)
 *
 * Vòng + chấm vẽ ra ngoài khung nút nên chỗ đặt nút không được cắt (clip) phần ngoài; nếu nút có .clip(...) thì đặt tapEffect
 * TRƯỚC clip trong chuỗi modifier.
 */

private const val POP_MS = 260
private const val BURST_MS = 620
private const val DOTS = 12
private val STANDARD = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val STANDARD_DECELERATE = CubicBezierEasing(0f, 0f, 0f, 1f)
private val CONFETTI = listOf(0xFFFF453A, 0xFFFF9F0A, 0xFFFF375F, 0xFFFFD60A, 0xFF0A84FF, 0xFFBF5AF2).map { Color(it) }

class TapEffectState internal constructor(private val scope: CoroutineScope, private val view: View) {
    internal val scale = Animatable(1f)
    internal val burst = Animatable(1f) // 1 = đã xong / không vẽ
    private var popJob: Job? = null
    private var burstJob: Job? = null

    /** Chạy hiệu ứng. [burst] = có vòng sáng + confetti hay chỉ nảy; [haptic] = rung nhẹ như bấm phím. */
    fun play(burst: Boolean = true, haptic: Boolean = true) {
        if (haptic) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        popJob?.cancel()
        popJob = scope.launch {
            scale.snapTo(1f)
            scale.animateTo(1f, keyframes {
                durationMillis = POP_MS
                0.78f at (POP_MS * 35 / 100) using STANDARD
                1.12f at (POP_MS * 75 / 100) using STANDARD
            })
        }
        if (burst) {
            burstJob?.cancel()
            burstJob = scope.launch {
                this@TapEffectState.burst.snapTo(0f)
                this@TapEffectState.burst.animateTo(1f, tween(BURST_MS, easing = STANDARD_DECELERATE))
            }
        }
    }

    internal fun drawBurst(scope: DrawScope, ringColor: Color) = with(scope) {
        val t = burst.value
        if (t >= 1f) return@with
        val dp = density
        val c = Offset(size.width / 2f, size.height / 2f)
        val halfW = size.width / 2f
        val halfH = size.height / 2f

        // Vòng: loe ra 18dp trong 60% đầu, mỏng và mờ dần.
        val ringT = min(1f, t / 0.6f)
        if (ringT < 1f) {
            val grow = 18f * dp * ringT
            val corner = min(halfW, halfH) + grow
            drawRoundRect(
                ringColor.copy(alpha = 1f - ringT),
                topLeft = Offset(c.x - halfW - grow, c.y - halfH - grow),
                size = Size((halfW + grow) * 2f, (halfH + grow) * 2f),
                cornerRadius = CornerRadius(corner, corner),
                style = Stroke(width = (4f * dp * (1f - ringT)).coerceAtLeast(0.5f))
            )
        }

        // Chấm: bay 26dp ra ngoài và nhỏ dần; chấm lẻ nhỏ hơn, ngắn hơn và lệch nửa bước cho khỏi giống mặt đồng hồ.
        val fly = 26f * dp * t
        for (i in 0 until DOTS) {
            val even = i % 2 == 0
            val angle = PI * 2 * i / DOTS + if (even) 0.0 else PI / DOTS
            val reach = (if (even) 1f else 0.7f) * fly
            val x = c.x + cos(angle).toFloat() * (halfW + 2f * dp + reach)
            val y = c.y + sin(angle).toFloat() * (halfH + 2f * dp + reach)
            val radius = (if (even) 3.2f else 2.2f) * dp * (1f - t)
            if (radius <= 0f) continue
            drawCircle(
                CONFETTI[i % CONFETTI.size].copy(alpha = min(1f, (1f - t) * 1.6f)),
                radius, Offset(x, y)
            )
        }
    }
}

@Composable
fun rememberTapEffect(): TapEffectState {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    return remember { TapEffectState(scope, view) }
}

/** Gắn hiệu ứng vào một nút. [effect] null thì không làm gì (để trang không phải trang hiện tại khỏi vẽ). */
fun Modifier.tapEffect(effect: TapEffectState?, ringColor: Color = Color.White): Modifier =
    if (effect == null) this
    else this
        .drawWithContent {
            drawContent()
            effect.drawBurst(this, ringColor)
        }
        .graphicsLayer {
            scaleX = effect.scale.value
            scaleY = effect.scale.value
        }
