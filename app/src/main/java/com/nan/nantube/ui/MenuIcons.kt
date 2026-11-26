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

/** "Không quan tâm" (vòng tròn gạch chéo) và "Không đề xuất kênh này" (vòng tròn dấu ×) ở menu ⋯ của thẻ video. */
val HideVideoIcon: ImageVector by lazy { lucide("HideVideoIcon", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0z", "M4.9 4.9l14.2 14.2") }
val HideChannelIcon: ImageVector by lazy { lucide("HideChannelIcon", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0z", "M15 9l-6 6", "M9 9l6 6") }

/** Nốt nhạc đặc của meetube (res/drawable/ic_badge_music.xml): đứng trước thời lượng của video nhạc trên thẻ. */
val MusicBadgeIcon: ImageVector by lazy {
    ImageVector.Builder("MusicBadgeIcon", 13.dp, 13.dp, 24f, 24f).apply {
        addPath(
            pathData = PathParser().parsePathString("M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z").toNodes(),
            fill = SolidColor(Color.White)
        )
    }.build()
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

/** Ba chấm đặc và đậm như nút "..." của bình luận trên YouTube; [vertical] cho nút "⋮". */
private fun moreDots(name: String, vertical: Boolean): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        listOf(5f, 12f, 19f).forEach { c ->
            val (x, y) = if (vertical) 12f to c else c to 12f
            addPath(
                pathData = PathParser().parsePathString("M${x - 2.4f} ${y}a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0 -4.8 0z").toNodes(),
                fill = SolidColor(Color.Black)
            )
        }
    }.build()

/** Ba chấm dọc (nút "⋮" ở màn Thông báo và màn phát nhạc). */
val MoreVerticalIcon: ImageVector by lazy { moreDots("MoreVerticalIcon", vertical = true) }

/** Ba chấm ngang. */
val MoreHorizontalIcon: ImageVector by lazy { moreDots("MoreHorizontalIcon", vertical = false) }

/** Mắt gạch chéo = ẩn. */
val EyeOffIcon: ImageVector by lazy {
    lucide(
        "EyeOffIcon",
        "M9.88 9.88a3 3 0 1 0 4.24 4.24",
        "M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68",
        "M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61",
        "M2 2l20 20"
    )
}

/** Chuông gạch chéo = tắt thông báo. */
val BellOffIcon: ImageVector by lazy {
    lucide(
        "BellOffIcon",
        "M8.7 3A6 6 0 0 1 18 8a21.3 21.3 0 0 0 .6 5",
        "M17 17H3s3-2 3-9a4.67 4.67 0 0 1 .3-1.7",
        "M10.3 21a1.94 1.94 0 0 0 3.4 0",
        "M2 2l20 20"
    )
}

/** Chuông đang rung = nhận tất cả thông báo. */
val BellRingIcon: ImageVector by lazy {
    lucide(
        "BellRingIcon",
        "M10.268 21a2 2 0 0 0 3.464 0",
        "M22 8c0-2.3-.8-4.3-2-6",
        "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
        "M4 2C2.8 3.7 2 5.7 2 8"
    )
}

/** Người kèm dấu x = hủy đăng ký. */
val UserXIcon: ImageVector by lazy {
    lucide(
        "UserXIcon",
        "M2 21a8 8 0 0 1 11.873-7",
        "M10 13a5 5 0 1 0 0-10a5 5 0 0 0 0 10z",
        "M17.5 17.5l5 5",
        "M22.5 17.5l-5 5"
    )
}
