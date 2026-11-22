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

private const val SHORTS_OUTLINE_PATH =
    "M17.77 10.32l-1.2-.5L18 9.06c1.84-.96 2.53-3.23 1.56-5.06s-3.24-2.53-5.07-1.56L6 6.94c-1.29.68-2.07 2.04-2 3.49" +
        ".07 1.42.93 2.67 2.22 3.25.03.01 1.2.5 1.2.5L6 14.93c-1.83.97-2.53 3.24-1.56 5.07.97 1.83 3.24 2.53 5.07 1.56" +
        "l8.5-4.5c1.29-.68 2.06-2.04 1.99-3.49-.07-1.42-.94-2.68-2.23-3.25zm-.23 5.86l-8.5 4.5c-1.34.71-3.01.2-3.72-1.14" +
        "-.71-1.34-.2-3.01 1.14-3.72l2.04-1.08v-1.21l-.69-.28-1.11-.46c-.99-.41-1.65-1.35-1.7-2.41-.05-1.06.52-2.06 1.46-2.56" +
        "l8.5-4.5c1.34-.71 3.01-.2 3.72 1.14.71 1.34.2 3.01-1.14 3.72L15.5 9.26v1.21l1.8.74c.99.41 1.65 1.35 1.7 2.41" +
        ".05 1.06-.52 2.06-1.46 2.56zM10 14.65l5-2.65-5-2.65v5.3z"

/** Nhà đặc (tab Main đang chọn), giống app YouTube. */
val HouseFilled: ImageVector by lazy { filledIcon("HouseFilled", "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z") }

/** Logo Shorts đặc (đang chọn). */
val ShortsFilled: ImageVector by lazy { filledIcon("ShortsFilled", SHORTS_PATH) }

/** Logo Shorts viền (chưa chọn): viền mảnh, tam giác đặc như tab Shorts của YouTube. */
val ShortsOutline: ImageVector by lazy { filledIcon("ShortsOutline", SHORTS_OUTLINE_PATH) }

private fun strokeIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()

// Đường nét lấy theo Lucide (viewBox 24, nét 2), vẽ tay để khỏi phụ thuộc tên icon của bản icons-lucide.
val IconArrowLeft: ImageVector by lazy { strokeIcon("ArrowLeft", "m12 19-7-7 7-7", "M19 12H5") }
val IconClose: ImageVector by lazy { strokeIcon("Close", "M18 6 6 18", "m6 6 12 12") }
val IconMic: ImageVector by lazy {
    strokeIcon("Mic", "M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z", "M19 10v2a7 7 0 0 1-14 0v-2", "M12 19v3")
}
val IconHistory: ImageVector by lazy {
    strokeIcon("History", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5", "M12 7v5l4 2")
}
/** Mũi tên chéo lên-trái: chép gợi ý vào ô tìm kiếm mà chưa tìm. */
val IconFillQuery: ImageVector by lazy { strokeIcon("FillQuery", "M7 17V7h10", "M17 17 7 7") }
