@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nan.nantube.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * Thanh trượt dùng chung toàn app, dáng như Zing MP3: vạch mảnh bo tròn hai đầu, nút tròn trắng nhỏ nằm sát vạch
 * (không có khe hở, không có vạch dọc, không có chấm cuối như Material 3 mới). Chỗ nào trong app cần thanh trượt thì dùng
 * NanSlider / NanRangeSlider thay cho Slider / RangeSlider của Material.
 */

private val ThumbSize = 14.dp
private val TrackHeight = 3.dp
private val DefaultActive = Color.White
private val DefaultInactive = Color(0x55FFFFFF)

@Composable
private fun NanThumb(color: Color) {
    Box(Modifier.size(ThumbSize).background(color, CircleShape))
}

/** Vẽ vạch: phần chưa chạy (xám) full chiều ngang, phần đã chạy từ [from] đến [to] (0..1 theo vị trí tâm nút). */
@Composable
private fun NanTrack(from: Float, to: Float, active: Color, inactive: Color) {
    Canvas(Modifier.fillMaxWidth().height(ThumbSize)) {
        val h = TrackHeight.toPx()
        val r = ThumbSize.toPx() / 2f
        val y = (size.height - h) / 2f
        val round = CornerRadius(h / 2f, h / 2f)
        drawRoundRect(inactive, Offset(0f, y), Size(size.width, h), round)
        // Tâm nút chạy từ r đến (width - r), nên vạch đã chạy phải đi theo đúng tâm nút.
        val span = (size.width - 2 * r).coerceAtLeast(0f)
        val x0 = if (from <= 0f) 0f else r + span * from
        val x1 = if (to >= 1f) size.width else r + span * to
        if (x1 > x0) drawRoundRect(active, Offset(x0, y), Size(x1 - x0, h), round)
    }
}

@Composable
fun NanSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    activeColor: Color = DefaultActive,
    inactiveColor: Color = DefaultInactive
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.alpha(if (enabled) 1f else 0.55f),
        enabled = enabled,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        thumb = { NanThumb(activeColor) },
        track = { st ->
            val span = valueRange.endInclusive - valueRange.start
            val f = if (span > 0f) ((st.value - valueRange.start) / span).coerceIn(0f, 1f) else 0f
            NanTrack(0f, f, activeColor, inactiveColor)
        }
    )
}

@Composable
fun NanRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    activeColor: Color = DefaultActive,
    inactiveColor: Color = DefaultInactive
) {
    RangeSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        startThumb = { NanThumb(activeColor) },
        endThumb = { NanThumb(activeColor) },
        track = { st ->
            val span = valueRange.endInclusive - valueRange.start
            fun frac(v: Float) = if (span > 0f) ((v - valueRange.start) / span).coerceIn(0f, 1f) else 0f
            NanTrack(frac(st.activeRangeStart), frac(st.activeRangeEnd), activeColor, inactiveColor)
        }
    )
}
