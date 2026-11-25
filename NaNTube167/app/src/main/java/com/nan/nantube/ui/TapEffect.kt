package com.nan.nantube.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import kotlin.math.sin

/*
 * Hiệu ứng chạm dùng chung (lấy từ meetube: Motion.pop + Burst): nút nảy lên (nhỏ lại 78% rồi phồng 112% rồi về 100%),
 * kèm quầng sáng tròn nảy rồi lan ra, mờ dần và chùm chấm confetti bay quanh icon.
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
private val BURST_EXPAND = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
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
        burstJob?.cancel()
        burstJob = scope.launch {
            this@TapEffectState.burst.snapTo(if (burst) 0f else 1f)
            if (burst) this@TapEffectState.burst.animateTo(1f, tween(BURST_MS, easing = LinearEasing))
        }
    }

    internal fun drawBurst(scope: DrawScope, ringColor: Color, ring: Boolean) = with(scope) {
        val t = burst.value
        if (t >= 1f) return@with
        val dp = density
        val c = center
        val base = size.minDimension / 2f
        if (base <= 0f) return@with

        // Quầng luôn là đường tròn: bắt đầu sát tâm, phồng nhẹ quá đích rồi lắng xuống.
        val ringT = (t / 0.86f).coerceAtMost(1f)
        if (ring && ringT < 1f) {
            val radius = if (ringT < 0.58f) {
                val grow = BURST_EXPAND.transform(ringT / 0.58f)
                base * 0.38f + (base * 0.62f + 15f * dp) * grow
            } else {
                base + 15f * dp - 2f * dp * ((ringT - 0.58f) / 0.42f)
            }
            val fade = 1f - ringT
            val alpha = (ringT * 10f).coerceAtMost(1f) * fade * fade
            val glowRadius = radius + 4f * dp
            drawCircle(
                brush = Brush.radialGradient(
                    0f to ringColor.copy(alpha = 0f),
                    (radius / glowRadius) to ringColor.copy(alpha = alpha * 0.32f),
                    1f to ringColor.copy(alpha = 0f),
                    center = c, radius = glowRadius
                ),
                radius = glowRadius,
                center = c
            )
            drawCircle(
                color = ringColor.copy(alpha = alpha * 0.9f),
                radius = radius,
                center = c,
                style = Stroke(width = (0.6f + 1.4f * fade) * dp)
            )
        }

        // Các chấm bung từ mép icon, lượn nhẹ quanh tâm rồi tan.
        val dotT = ((t - 0.10f) / 0.90f).coerceIn(0f, 1f)
        if (dotT <= 0f) return@with
        val fly = BURST_EXPAND.transform(dotT)
        val appear = (dotT * 8f).coerceAtMost(1f)
        for (i in 0 until DOTS) {
            val even = i % 2 == 0
            val angle = 2.0 * PI * i / DOTS + (if (even) 0.0 else PI / (DOTS * 2)) + 0.22 * fly
            val reach = base * 0.85f + (if (even) 23f else 16f) * dp * fly
            val radius = (if (even) 2.5f else 1.7f) * dp * appear * (1f - dotT)
            drawCircle(
                color = CONFETTI[i % CONFETTI.size].copy(alpha = appear * (1f - dotT)),
                radius = radius,
                center = c + Offset(cos(angle).toFloat() * reach, sin(angle).toFloat() * reach)
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
fun Modifier.tapEffect(effect: TapEffectState?, ringColor: Color = Color.White, ring: Boolean = true): Modifier =
    if (effect == null) this
    else this
        .drawWithContent {
            drawContent()
            effect.drawBurst(this, ringColor, ring)
        }
        .graphicsLayer {
            scaleX = effect.scale.value
            scaleY = effect.scale.value
        }
