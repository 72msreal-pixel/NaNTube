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
val YtShare: ImageVector by lazy { AppShareIcon }

/** Mũi tên tải xuống đặc kèm vạch đáy (Material "download", giống nút Tải xuống của meetube). */
val YtDownload: ImageVector by lazy {
    glyph("YtDownload", "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z")
}

/** Tam giác phát và hai thanh dừng của ô tròn nháy giữa Short (glyph meetube: đặc + viền 1.5 bo tròn). */
private fun flashGlyph(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            fill = SolidColor(Color.White),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineJoin = StrokeJoin.Round
        )
    }.build()

val FlashPlay: ImageVector by lazy {
    flashGlyph("FlashPlay", "M8.75 6.37 13.18 9.18 13.18 14.82 8.75 17.63ZM13.18 9.18 17.6 12 13.18 14.82Z")
}

val FlashPause: ImageVector by lazy {
    flashGlyph("FlashPause", "M6.75 5.75 9.25 5.75 9.25 18.25 6.75 18.25ZM14.75 5.75 17.25 5.75 17.25 18.25 14.75 18.25Z")
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

private const val THUMB_UP = "M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z"
private const val THUMB_DOWN = "M9 18.12 10 14H4.17a2 2 0 0 1-1.92-2.56l2.33-8A2 2 0 0 1 6.5 2H20a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2.76a2 2 0 0 0-1.79 1.11L12 22a3.13 3.13 0 0 1-3-3.88Z"

/** Ngón cái thích / không thích của bình luận: rỗng = chưa chọn, đặc = đã chọn. */
val YtThumbUp: ImageVector by lazy { line("YtThumbUp", THUMB_UP, "M7 10v12") }
val YtThumbUpFilled: ImageVector by lazy { line("YtThumbUpFilled", THUMB_UP, "M7 10v12", filled = true) }
val YtThumbDown: ImageVector by lazy { line("YtThumbDown", THUMB_DOWN, "M17 14V2") }
val YtThumbDownFilled: ImageVector by lazy { line("YtThumbDownFilled", THUMB_DOWN, "M17 14V2", filled = true) }

/** Nút sắp xếp bình luận (hai thanh trượt). */
val YtSliders: ImageVector by lazy {
    line("YtSliders", "M21 5h-7", "M10 5H3", "M21 12h-9", "M8 12H3", "M21 19h-5", "M12 19H3", "M14 3v4", "M8 10v4", "M16 17v4", width = 2f)
}

/** Mũi tên gửi (máy bay giấy đặc). */
val YtSend: ImageVector by lazy { glyph("YtSend", "M3 20.5v-6.2l9-2.3-9-2.3V3.5L22 12 3 20.5z") }
