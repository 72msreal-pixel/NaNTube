package com.nan.nantube.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Bộ icon cột nút bên phải của YouTube Shorts (tim, bình luận, lưu, chia sẻ, làm mới) vẽ theo glyph của YouTube:
 * tô đặc / nét 1.8 bo tròn, viewBox 24. Không dùng Lucide.
 */
private fun glyph(name: String, vararg paths: String, evenOdd: Boolean = false): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = SolidColor(Color.Black),
                pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero
            )
        }
    }.build()

private fun line(name: String, vararg paths: String, filled: Boolean = false, width: Float = 1.8f): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEachIndexed { index, d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = if (filled && index == 0) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()

/** Tim rỗng (chưa thích). */
val YtHeart: ImageVector by lazy {
    glyph(
        "YtHeart",
        "M16.5 3c-1.74 0-3.41.81-4.5 2.09C10.91 3.81 9.24 3 7.5 3 4.42 3 2 5.42 2 8.5c0 3.78 3.4 6.86 8.55 11.54L12 21.35" +
            "l1.45-1.32C18.6 15.36 22 12.28 22 8.5 22 5.42 19.58 3 16.5 3zm-4.4 15.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5" +
            " 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z"
    )
}

/** Tim đặc (đã thích). */
val YtHeartFilled: ImageVector by lazy {
    glyph(
        "YtHeartFilled",
        "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3" +
            " 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"
    )
}

/** Bong bóng bình luận: khung bo tròn, đuôi ở góc dưới trái, hai dòng chữ bên trong. */
val YtComment: ImageVector by lazy {
    line(
        "YtComment",
        "M6 3.5h12a2.5 2.5 0 0 1 2.5 2.5v8a2.5 2.5 0 0 1-2.5 2.5h-6.3L7.4 20.6a.5.5 0 0 1-.9-.4v-3.7H6A2.5 2.5 0 0 1 3.5 14V6A2.5 2.5 0 0 1 6 3.5z",
        "M8 8.5h8",
        "M8 12h5"
    )
}

/** Dấu trang rỗng (Lưu). */
val YtBookmark: ImageVector by lazy {
    line("YtBookmark", "M7 3.5h10a1 1 0 0 1 1 1v16.2a.4.4 0 0 1-.64.32L12 17.2l-5.36 3.82a.4.4 0 0 1-.64-.32V4.5a1 1 0 0 1 1-1z")
}

/** Dấu trang đặc (đã lưu). */
val YtBookmarkFilled: ImageVector by lazy {
    line("YtBookmarkFilled", "M7 3.5h10a1 1 0 0 1 1 1v16.2a.4.4 0 0 1-.64.32L12 17.2l-5.36 3.82a.4.4 0 0 1-.64-.32V4.5a1 1 0 0 1 1-1z", filled = true)
}

/** Mũi tên cong sang phải (Chia sẻ), đúng hình mũi tên chia sẻ của YouTube. */
val YtShare: ImageVector by lazy {
    glyph(
        "YtShare",
        "M15 5.63 20.66 12 15 18.37V14h-1c-3.96 0-7.14 1-9.75 3.09 1.84-4.07 5.11-6.4 9.89-7.1l.86-.13V5.63M14 3v6C6.22 10.13 3.11 15.33 2 21" +
            "c2.78-3.97 6.44-6.05 12-6v6l9-9-9-9z"
    )
}

/** Mũi tên xoay vòng: làm mới để lấy Short mới. */
val YtRefresh: ImageVector by lazy {
    glyph(
        "YtRefresh",
        "M17.65 6.35A7.958 7.958 0 0 0 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08" +
            "A5.99 5.99 0 0 1 12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"
    )
}

/** Kính lúp và ba chấm dọc của thanh trên cùng (nét đặc như YouTube). */
val YtSearch: ImageVector by lazy {
    glyph(
        "YtSearch",
        "M20.87 20.17l-5.59-5.59A6.96 6.96 0 0 0 17 10c0-3.87-3.13-7-7-7s-7 3.13-7 7 3.13 7 7 7c1.71 0 3.28-.62 4.5-1.65l5.67 5.67.7-.85z" +
            "M10 16c-3.31 0-6-2.69-6-6s2.69-6 6-6 6 2.69 6 6-2.69 6-6 6z"
    )
}
