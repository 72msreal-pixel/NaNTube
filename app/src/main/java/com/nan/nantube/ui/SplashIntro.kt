package com.nan.nantube.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.ImageBitmap
import com.nan.nantube.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Intro lúc mở app, dựng lại từ video quay màn hình YouTube (720px rộng, 52 frame):
 *  1. Logo đứng yên, rộng 209px / 720 (29% bề ngang màn hình).
 *  2. Logo "nhún" thu nhỏ còn 80% (168px).
 *  3. Logo bẹp lại thành đường kẻ rồi mờ đi; thay vào đó là tam giác play trắng + thanh xám mỏng (dày 8px, cao giữa màn hình).
 *  4. Thanh dãn đều ra hai bên từ giữa: 206px -> 370px (28.6% -> 51.4% bề ngang) trong ~330ms.
 *  5. Thanh đỏ #FF0033 chạy từ trái sang phải, càng lúc càng nhanh, đầu thanh nhòe dần vào màu xám.
 *  6. Mờ dần để lộ app.
 *
 * Mọi kích thước tính theo u = chiều rộng màn hình / 720 nên khớp tỉ lệ video trên mọi máy.
 */

// Vùng logo thật trong nantube_logo.png (384x384, phần có hình nằm ở y 53..331).
private const val LOGO_SRC_Y = 53
private const val LOGO_SRC_W = 384
private const val LOGO_SRC_H = 278

private const val LOGO_W = 209f      // px trên video 720px
private const val SCALE_SHRINK = 0.80f

private const val BAR_HALF_START = 103f   // nửa chiều dài lúc mới hiện (206 / 2)
private const val BAR_HALF_END = 185f     // nửa chiều dài cuối (370 / 2)
private const val BAR_THICK = 8f
private const val TRI_W = 26f
private const val TRI_H = 27f
private const val TIP_FADE = 24f          // đoạn đầu thanh đỏ nhòe vào xám

private val BarGray = Color(0xFFCCCCCC)
private val ProgressRed = Color(0xFFFF0033)

private const val HOLD_MS = 600           // logo đứng yên (video giữ ~1.2s do máy đang tải)
private const val PULSE_MS = 110
private const val PULSE_HOLD_MS = 110
private const val MORPH_MS = 100
private const val BAR_MS = 330
private const val PROGRESS_MS = 1100
private const val EXIT_MS = 220

// Chờ app nạp xong Main (Short tải song song nhưng không chờ): thanh đỏ chạy tới PROGRESS_HOLD rồi nhích chậm tới PROGRESS_CREEP trong lúc chờ,
// có dữ liệu thì chạy nốt tới đầu. Chờ tối đa MAX_WAIT_MS (mạng chậm / mất mạng thì vẫn vào app).
private const val PROGRESS_HOLD = 0.80f
private const val PROGRESS_CREEP = 0.95f
private const val MAX_WAIT_MS = 3000
private const val SETTLE_MS = 120         // cho khung hình đầu của danh sách kịp dựng trước khi mờ intro
private const val FINISH_MS = 200

/** Cờ "đã nạp sẵn xong" do AppRoot bật khi feed Main đã có dữ liệu (hoặc báo lỗi). Bật một lần là giữ. */
object SplashGate {
    var ready by mutableStateOf(false)
}

@Composable
fun SplashIntro(onFinished: () -> Unit) {
    val logo: ImageBitmap = ImageBitmap.imageResource(R.drawable.nantube_logo)

    val pulse = remember { Animatable(1f) }      // 1 -> 0.8
    val morph = remember { Animatable(0f) }      // logo -> đường kẻ
    val bar = remember { Animatable(0f) }        // độ dài thanh
    val progress = remember { Animatable(0f) }   // thanh đỏ
    val exit = remember { Animatable(1f) }       // alpha cả màn

    LaunchedEffect(Unit) {
        delay(HOLD_MS.toLong())
        pulse.animateTo(SCALE_SHRINK, tween(PULSE_MS, easing = FastOutSlowInEasing))
        delay(PULSE_HOLD_MS.toLong())
        morph.animateTo(1f, tween(MORPH_MS, easing = LinearEasing))
        coroutineScope {
            launch { progress.animateTo(PROGRESS_HOLD, tween(PROGRESS_MS, easing = FastOutLinearInEasing)) }
            bar.animateTo(1f, tween(BAR_MS, easing = FastOutSlowInEasing))
        }
        // Đợi Main nạp xong (tối đa MAX_WAIT_MS), thanh đỏ nhích chậm cho khỏi đứng hình.
        if (!SplashGate.ready) {
            coroutineScope {
                val creep = launch { progress.animateTo(PROGRESS_CREEP, tween(MAX_WAIT_MS, easing = LinearEasing)) }
                withTimeoutOrNull(MAX_WAIT_MS.toLong()) { snapshotFlow { SplashGate.ready }.first { it } }
                creep.cancel()
            }
        }
        delay(SETTLE_MS.toLong())
        progress.animateTo(1f, tween(FINISH_MS, easing = FastOutSlowInEasing))
        exit.animateTo(0f, tween(EXIT_MS, easing = LinearEasing))
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = exit.value }
            .background(NanColors.Background)
            // Chặn chạm xuyên xuống app trong lúc intro chạy.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 720f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val m = morph.value

            // --- Logo: nhún 100% -> 80%, rồi bẹp ngang + mờ dần ---
            if (m < 1f) {
                val w = LOGO_W * u
                val h = w * LOGO_SRC_H / LOGO_SRC_W
                val sx = pulse.value * (1f + 0.12f * m)
                val sy = pulse.value * (1f - 0.75f * m)
                withTransform({ scale(sx, sy, pivot = Offset(cx, cy)) }) {
                    drawImage(
                        image = logo,
                        srcOffset = IntOffset(0, LOGO_SRC_Y),
                        srcSize = IntSize(LOGO_SRC_W, LOGO_SRC_H),
                        dstOffset = IntOffset((cx - w / 2f).toInt(), (cy - h / 2f).toInt()),
                        dstSize = IntSize(w.toInt(), h.toInt()),
                        alpha = 1f - m
                    )
                }
            }

            // --- Tam giác play + thanh xám + thanh đỏ ---
            if (m > 0f) {
                val half = (BAR_HALF_START + (BAR_HALF_END - BAR_HALF_START) * bar.value) * u
                val left = cx - half
                val right = cx + half
                val triW = TRI_W * u
                val triH = TRI_H * u
                val thick = BAR_THICK * u

                // Tam giác trắng (bo nhẹ góc bằng nét viền tròn).
                val tri = Path().apply {
                    moveTo(left, cy - triH / 2f)
                    lineTo(left + triW, cy)
                    lineTo(left, cy + triH / 2f)
                    close()
                }
                drawPath(tri, Color.White.copy(alpha = m), style = Fill)

                val barX = left + triW
                val barLen = right - barX
                drawRoundRect(
                    color = BarGray.copy(alpha = m),
                    topLeft = Offset(barX, cy - thick / 2f),
                    size = Size(barLen, thick),
                    cornerRadius = CornerRadius(1f * u)
                )

                val redLen = barLen * progress.value
                if (redLen > 0.5f) {
                    val fade = (TIP_FADE * u / redLen).coerceIn(0f, 1f)
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            0f to ProgressRed,
                            (1f - fade) to ProgressRed,
                            1f to BarGray,
                            startX = barX,
                            endX = barX + redLen
                        ),
                        topLeft = Offset(barX, cy - thick / 2f),
                        size = Size(redLen, thick),
                        cornerRadius = CornerRadius(1f * u)
                    )
                }
            }
        }
    }
}
