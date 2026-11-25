package com.nan.nantube.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import java.util.Locale
import kotlin.math.pow

/*
 * Chữ số đếm dùng chung (lấy từ CountUp của meetube, thêm hiệu ứng lăn khi số đổi):
 *  - Lần đầu số này hiện cho một [key] (vd. mã video): chạy từ 0 lên tới số thật (0,6 giây, chậm dần), "2,7 N" chạy 0,0 N..2,7 N.
 *  - Sau đó số đổi (thích: 110 -> 111): số cũ trượt đi, số mới trượt vào (tăng thì trượt lên, giảm thì trượt xuống).
 * Chữ không có số (vd. "Thích") thì hiện thẳng, không hiệu ứng. Dùng: CountText(text, key = "like|$id", style = ..., fontSize = ...)
 */

private val NUMBER = Regex("\\d+(?:[.,]\\d+)*")
private val DECELERATE = Easing { x -> 1f - (1f - x).pow(4) } // DecelerateInterpolator(2f)
private val ROLL = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val counted = HashSet<Any>()

private class Parsed(
    val before: String, val after: String, val target: Double,
    val format: String, val point: Char, val group: Char
)

private fun parseCount(text: String): Parsed? {
    val m = NUMBER.find(text) ?: return null
    val num = m.value
    // Dấu ngăn cuối là dấu thập phân trừ khi theo sau nó đúng ba chữ số ("1,234" khác "2,7").
    val sep = maxOf(num.lastIndexOf('.'), num.lastIndexOf(','))
    val decimals = if (sep >= 0 && num.length - sep - 1 != 3) num.length - sep - 1 else 0
    val point = if (decimals > 0) num[sep] else '.'
    val group = if (decimals == 0 && sep >= 0) num[sep] else if (point == '.') ',' else '.'
    val target = num.replace(group.toString(), "").replace(point, '.').toDoubleOrNull() ?: return null
    val format = (if (num.indexOf(group) >= 0) "%," else "%") + "." + decimals + "f"
    return Parsed(text.substring(0, m.range.first), text.substring(m.range.last + 1), target, format, point, group)
}

private fun partial(p: Parsed, fraction: Float): String {
    val digits = String.format(Locale.US, p.format, p.target * fraction)
    val mapped = buildString { digits.forEach { append(if (it == ',') p.group else if (it == '.') p.point else it) } }
    return p.before + mapped + p.after
}

@Composable
fun CountText(
    text: String,
    key: Any?,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified
) {
    val parsed = remember(text) { parseCount(text) }
    val countUp = remember(key, text) { key != null && parsed != null && key !in counted }
    var progress by remember(key, text) { mutableFloatStateOf(if (countUp) 0f else 1f) }
    var cur by remember { mutableStateOf(text) }
    var old by remember { mutableStateOf<String?>(null) }
    var up by remember { mutableStateOf(true) }
    val roll = remember { Animatable(1f) }

    LaunchedEffect(key, text) {
        if (countUp) {
            if (counted.size > 500) counted.clear()
            counted.add(key!!)
            old = null
            cur = text
            animate(0f, 1f, animationSpec = tween(600, easing = DECELERATE)) { value, _ -> progress = value }
            progress = 1f
        } else if (text != cur) {
            val from = cur
            old = from
            cur = text
            up = (parseCount(text)?.target ?: 0.0) >= (parseCount(from)?.target ?: 0.0)
            roll.snapTo(0f)
            roll.animateTo(1f, tween(220, easing = ROLL))
            old = null
        }
    }

    val previous = old
    val shown = if (progress < 1f && parsed != null) partial(parsed, progress) else cur
    Box(
        if (previous != null) modifier.clipToBounds() else modifier,
        contentAlignment = Alignment.Center
    ) {
        if (previous != null && progress >= 1f) {
            Text(
                previous, style = style, fontSize = fontSize, fontWeight = fontWeight, color = color, maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    translationY = (if (up) -1f else 1f) * size.height * roll.value
                    alpha = 1f - roll.value
                }
            )
            Text(
                cur, style = style, fontSize = fontSize, fontWeight = fontWeight, color = color, maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    translationY = (if (up) 1f else -1f) * size.height * (1f - roll.value)
                    alpha = roll.value
                }
            )
        } else {
            Text(shown, style = style, fontSize = fontSize, fontWeight = fontWeight, color = color, maxLines = 1)
        }
    }
}
