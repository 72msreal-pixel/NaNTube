package com.nan.nantube.ui

import android.view.ViewConfiguration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Nhấn đúp nửa trái / phải video để lùi / tiến theo số giây đã cài; nhấn tiếp liên tục thì cộng dồn
 * (giống YouTube). Một lần chạm đơn vẫn là hiện / ẩn nút điều khiển, nhưng chờ hết hạn nhấn đúp mới chạy.
 */
class DoubleTapSeek(private val scope: CoroutineScope) {
    /** -1 = đang lùi (nửa trái), 1 = đang tiến (nửa phải), 0 = không có chuỗi nào. */
    var dir by mutableIntStateOf(0)
        private set

    /** Số lần đã cộng dồn trong chuỗi hiện tại (giữ nguyên lúc đang mờ đi để chữ không nhảy về 0). */
    var steps by mutableIntStateOf(0)
        private set

    /** Số giây của mỗi lần nhấn trong chuỗi hiện tại (theo cài đặt lúc bắt đầu chuỗi). */
    var stepSeconds by mutableIntStateOf(10)
        private set

    private var base = 0L
    private var lastDir = 0
    private var pendingToggle: Job? = null
    private var hideJob: Job? = null

    fun tap(
        side: Int,
        enabled: Boolean,
        positionMs: Long,
        durationMs: Long,
        stepSeconds: Int,
        seekTo: (Long) -> Unit,
        hideControls: () -> Unit,
        toggleControls: () -> Unit
    ) {
        if (!enabled) {
            toggleControls()
            return
        }
        val secondTap = pendingToggle?.isActive == true && side == lastDir
        pendingToggle?.cancel()
        pendingToggle = null
        if (dir != 0 || secondTap) {
            step(side, positionMs, durationMs, stepSeconds, seekTo, hideControls)
        } else {
            lastDir = side
            pendingToggle = scope.launch {
                delay(ViewConfiguration.getDoubleTapTimeout().toLong())
                toggleControls()
            }
        }
    }

    private fun step(side: Int, positionMs: Long, durationMs: Long, seconds: Int, seekTo: (Long) -> Unit, hideControls: () -> Unit) {
        if (dir != side) {
            stepSeconds = seconds
            // Chuỗi mới, hoặc đổi sang nửa bên kia: đếm lại từ vị trí đang phát.
            dir = side
            steps = 0
            base = positionMs
            hideControls()
        }
        steps++
        val max = if (durationMs > 0) durationMs else Long.MAX_VALUE
        seekTo((base + side * steps * stepSeconds * 1000L).coerceIn(0L, max))
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(COMBO_MS)
            dir = 0
        }
    }

    private companion object {
        const val COMBO_MS = 500L
    }
}

/** Lớp phủ báo "⏪ -10s" ở nửa trái và "+10s ⏩" ở nửa phải. */
@Composable
fun DoubleTapSeekOverlay(state: DoubleTapSeek, modifier: Modifier = Modifier) {
    val seconds = state.steps * state.stepSeconds
    Row(modifier.fillMaxSize()) {
        SeekSide(visible = state.dir < 0, forward = false, seconds = seconds, modifier = Modifier.weight(1f))
        SeekSide(visible = state.dir > 0, forward = true, seconds = seconds, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SeekSide(visible: Boolean, forward: Boolean, seconds: Int, modifier: Modifier) {
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            // Không có lớp nền: chỉ icon + chữ, chữ có bóng nhẹ để vẫn đọc được trên video sáng.
            val shadow = Shadow(Color(0x5A000000), Offset(0f, 1f), 6f)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (forward) {
                    Text("+${seconds}s", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, style = TextStyle(shadow = shadow))
                    Icon(IconFastForward, null, Modifier.size(24.dp), tint = Color.White)
                } else {
                    Icon(IconRewind, null, Modifier.size(24.dp), tint = Color.White)
                    Text("-${seconds}s", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, style = TextStyle(shadow = shadow))
                }
            }
        }
    }
}
