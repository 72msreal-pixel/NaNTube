package com.nan.nantube.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay

/**
 * Ô tròn đen trong suốt, không viền + biểu tượng phát / dừng nháy giữa Short mỗi lần chạm, y hệt flashPlayState của meetube:
 * hiện ngay, nảy từ 0.85 lên 1 trong 90 ms, giữ 220 ms rồi mờ dần trong 140 ms.
 */
class PlayFlash {
    private val alpha = Animatable(0f)
    private val scale = Animatable(0.85f)
    var playing by mutableStateOf(false)
        private set

    val shownAlpha get() = alpha.value
    val shownScale get() = scale.value

    suspend fun show(playing: Boolean) {
        this.playing = playing
        alpha.snapTo(1f)
        scale.snapTo(0.85f)
        scale.animateTo(1f, tween(90))
        delay(220)
        alpha.animateTo(0f, tween(140))
    }
}

@Composable
fun PlayFlashDisc(flash: PlayFlash, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(72.dp)
            .graphicsLayer {
                alpha = flash.shownAlpha
                scaleX = flash.shownScale
                scaleY = flash.shownScale
            }
            .background(Color(0x66000000), CircleShape)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(if (flash.playing) FlashPause else FlashPlay, null, Modifier.size(40.dp), tint = Color.White)
    }
}
