package com.nan.nantube.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Icon nét mảnh theo kiểu Lucide (viewBox 24, nét 2) cho menu "⋯" và bảng "Lưu vào…". [filled] tô đặc phần bên trong. */
private fun lucide(name: String, vararg paths: String, filled: Boolean = false): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()

private const val BOOKMARK_PATH = "M19 21l-7-4l-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z"

/** Dấu trang rỗng = chưa lưu vào danh sách. */
val BookmarkOutline: ImageVector by lazy { lucide("BookmarkOutline", BOOKMARK_PATH) }

/** Dấu trang đặc = đã lưu vào danh sách. */
val BookmarkFilled: ImageVector by lazy { lucide("BookmarkFilled", BOOKMARK_PATH, filled = true) }

val CastIcon: ImageVector by lazy {
    lucide(
        "CastIcon",
        "M2 8V6a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-6",
        "M2 12a9 9 0 0 1 8 8",
        "M2 16a5 5 0 0 1 4 4",
        "M2 20h.01"
    )
}

/** Mũi tên chia sẻ (như nút Chia sẻ của YouTube). */
val ShareIcon: ImageVector by lazy { lucide("ShareIcon", "M15 14l5-5l-5-5", "M4 20v-7a4 4 0 0 1 4-4h12") }

val TrashIcon: ImageVector by lazy {
    lucide(
        "TrashIcon",
        "M3 6h18",
        "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6",
        "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2",
        "M10 11v6",
        "M14 11v6"
    )
}

val PlusIcon: ImageVector by lazy { lucide("PlusIcon", "M5 12h14", "M12 5v14") }

val GlobeIcon: ImageVector by lazy {
    lucide(
        "GlobeIcon",
        "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z",
        "M12 2a14.5 14.5 0 0 0 0 20a14.5 14.5 0 0 0 0-20",
        "M2 12h20"
    )
}

val LinkIcon: ImageVector by lazy {
    lucide(
        "LinkIcon",
        "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
        "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"
    )
}

val LockIcon: ImageVector by lazy {
    lucide(
        "LockIcon",
        "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z",
        "M7 11V7a5 5 0 0 1 10 0v4"
    )
}
