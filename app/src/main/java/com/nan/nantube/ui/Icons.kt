package com.nan.nantube.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Icon "house" của Lucide (cùng đường nét, viewBox 24, nét 2).
 * Vẽ tay từ path vì bản icons-lucide đang dùng có thể chưa có tên House/Home.
 */
val LucideHouse: ImageVector by lazy {
    val stroke = SolidColor(Color.Black)
    ImageVector.Builder(
        name = "LucideHouse",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        listOf(
            "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
            "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"
        ).forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()
}

private fun filledIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { d -> addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black)) }
    }.build()

private const val SHORTS_PATH =
    "M10 14.65v-5.3L15 12l-5 2.65zm7.77-4.33c-.77-.32-1.2-.5-1.2-.5L18 9.06c1.84-.96 2.53-3.23 1.56-5.06" +
        "s-3.24-2.53-5.07-1.56L6 6.94c-1.29.68-2.07 2.04-2 3.49.07 1.42.93 2.67 2.22 3.25.03.01 1.2.5 1.2.5" +
        "L6 14.93c-1.83.97-2.53 3.24-1.56 5.07.97 1.83 3.24 2.53 5.07 1.56l8.5-4.5c1.29-.68 2.06-2.04 1.99-3.49" +
        "-.07-1.42-.94-2.68-2.23-3.25z"

/** Nhà đặc (tab Main đang chọn), giống app YouTube. */
val HouseFilled: ImageVector by lazy { filledIcon("HouseFilled", "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z") }

/** Logo Shorts đặc (đang chọn). */
val ShortsFilled: ImageVector by lazy { filledIcon("ShortsFilled", SHORTS_PATH) }

/** Logo Shorts viền (chưa chọn). */
val ShortsOutline: ImageVector by lazy {
    ImageVector.Builder("ShortsOutline", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = PathParser().parsePathString(SHORTS_PATH).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}
