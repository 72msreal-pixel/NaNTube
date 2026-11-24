package com.nan.nantube.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object NanColors {
    val Background = Color(0xFF0F0F0F)
    val Surface = Color(0xFF272727)
    val Popup = Color(0xFF212121)
    val Divider = Color(0xFF3F3F3F)
    val TextSoft = Color(0xFFCCCCCC)
    val TextSecondary = Color(0xFFAAAAAA)
    val Blue = Color(0xFF3EA6FF)
}

private val scheme = darkColorScheme(
    primary = Color.White,
    background = NanColors.Background,
    surface = NanColors.Background,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun NaNTubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
