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
