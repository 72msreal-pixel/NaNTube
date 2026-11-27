package com.nan.nantube.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Icon của màn xem video dài, lấy nguyên path từ drawable của meetube (mobile/src/stmobile/res/drawable: ic_watch_thumb_up,
 * ic_watch_thumb_down(_outline), ic_watch_share, ic_player_cc(_off), ic_player_fullscreen, ic_mobile_cast, ic_mobile_more_vert)
 * cùng bộ glyph Material cho nút phát / tạm dừng / trước / sau. Tất cả viewBox 24, tô đặc.
 */
private fun fill(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { d ->
            addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
        }
    }.build()

val WatchThumbUp: ImageVector by lazy {
    fill(
        "WatchThumbUp",
        "M1,21h4V9H1V21zM23,10c0,-1.1 -0.9,-2 -2,-2h-6.31l0.95,-4.57 0.03,-0.32c0,-0.41 -0.17,-0.79 -0.44,-1.06L14.17,1 7.59,7.59" +
            "C7.22,7.95 7,8.45 7,9v10c0,1.1 0.9,2 2,2h9c0.83,0 1.54,-0.5 1.84,-1.22l3.02,-7.05c0.09,-0.23 0.14,-0.47 0.14,-0.73V10z"
    )
}

val WatchThumbUpOutline: ImageVector by lazy {
    fill(
        "WatchThumbUpOutline",
        "M9,21h9c0.83,0 1.54,-0.5 1.84,-1.22l3.02,-7.05c0.09,-0.23 0.14,-0.47 0.14,-0.73v-2c0,-1.1 -0.9,-2 -2,-2h-6.31l0.95,-4.57" +
            " 0.03,-0.32c0,-0.41 -0.17,-0.79 -0.44,-1.06L14.17,1 7.58,7.59C7.22,7.95 7,8.45 7,9v10c0,1.1 0.9,2 2,2zM9,9l4.34,-4.34L12,10h9v2" +
            "l-3,7H9V9zM1,9h4v12H1z"
    )
}

val WatchThumbDown: ImageVector by lazy {
    fill(
        "WatchThumbDown",
        "M15,3H6c-0.83,0 -1.54,0.5 -1.84,1.22l-3.02,7.05c-0.09,0.23 -0.14,0.47 -0.14,0.73v2c0,1.1 0.9,2 2,2h6.31l-0.95,4.57" +
            " -0.03,0.32c0,0.41 0.17,0.79 0.44,1.06L9.83,23l6.59,-6.59c0.36,-0.36 0.58,-0.86 0.58,-1.41V5c0,-1.1 -0.9,-2 -2,-2zM19,3v12h4V3h-4z"
    )
}

val WatchThumbDownOutline: ImageVector by lazy {
    fill(
        "WatchThumbDownOutline",
        "M15,3H6c-0.83,0 -1.54,0.5 -1.84,1.22l-3.02,7.05c-0.09,0.23 -0.14,0.47 -0.14,0.73v2c0,1.1 0.9,2 2,2h6.31l-0.95,4.57" +
            " -0.03,0.32c0,0.41 0.17,0.79 0.44,1.06L9.83,23l6.59,-6.59c0.36,-0.36 0.58,-0.86 0.58,-1.41V5c0,-1.1 -0.9,-2 -2,-2zM15,15l-4.34,4.34" +
            "L12,14H3v-2l3,-7h9v10zM19,3h4v12h-4z"
    )
}

/** Chia sẻ kiểu YouTube: mũi tên cong viền rỗng (khoét giữa bằng EvenOdd). */
val WatchShare: ImageVector by lazy {
    ImageVector.Builder("WatchShare", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M15,5.63L20.66,12 15,18.37V14h-1c-3.96,0 -7.14,1 -9.75,3.09 1.84,-4.07 5.11,-6.4 9.89,-7.1l0.86,-0.13V5.63" +
                    "M14,3v6C6.22,10.13 3.11,15.33 2,21c2.78,-3.97 6.44,-6.05 12,-6v6l9,-9 -9,-9z"
            ).toNodes(),
            pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd,
            fill = SolidColor(Color.Black)
        )
    }.build()
}

val WatchMore: ImageVector by lazy {
    fill(
        "WatchMore",
        "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z" +
            "M12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z"
    )
}

val WatchCc: ImageVector by lazy {
    fill(
        "WatchCc",
        "M19,4H5C3.9,4 3,4.9 3,6v12c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V6C21,4.9 20.1,4 19,4zM19,18H5V6h14V18zM11,11H9.5v-0.5h-2v3h2V13" +
            "H11v1c0,0.55 -0.45,1 -1,1H7c-0.55,0 -1,-0.45 -1,-1v-4c0,-0.55 0.45,-1 1,-1h3c0.55,0 1,0.45 1,1V11zM18,11h-1.5v-0.5h-2v3h2V13H18v1" +
            "c0,0.55 -0.45,1 -1,1h-3c-0.55,0 -1,-0.45 -1,-1v-4c0,-0.55 0.45,-1 1,-1h3c0.55,0 1,0.45 1,1V11z"
    )
}

val WatchCast: ImageVector by lazy {
    fill(
        "WatchCast",
        "M1,18v3h3c0,-1.66 -1.34,-3 -3,-3zM1,14v2c2.76,0 5,2.24 5,5h2c0,-3.87 -3.13,-7 -7,-7zM1,10v2c4.97,0 9,4.03 9,9h2c0,-6.08 -4.93,-11 -11,-11z" +
            "M21,3L3,3c-1.1,0 -2,0.9 -2,2v3h2L3,5h18v14h-7v2h7c1.1,0 2,-0.9 2,-2L23,5c0,-1.1 -0.9,-2 -2,-2z"
    )
}

/** Phóng to video kiểu YouTube: hai mũi tên chéo đầu chữ L hướng ra góc trên phải / dưới trái. */
val WatchFullscreen: ImageVector by lazy {
    fill(
        "WatchFullscreen",
        "M14,3h7v7h-2V6.41l-5.29,5.3 -1.42,-1.42L17.59,5H14V3z",
        "M10,21H3v-7h2v3.59l5.29,-5.3 1.42,1.42L6.41,19H10v2z"
    )
}

val WatchPlay: ImageVector by lazy { fill("WatchPlay", "M8,5v14l11,-7z") }
val WatchPause: ImageVector by lazy { fill("WatchPause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z") }
val WatchSkipNext: ImageVector by lazy { fill("WatchSkipNext", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z") }
val WatchSkipPrev: ImageVector by lazy { fill("WatchSkipPrev", "M6,6h2v12H6zM9.5,12l8.5,6V6z") }
val WatchReplay: ImageVector by lazy {
    fill(
        "WatchReplay",
        "M12,5V1L7,6l5,5V7c3.31,0 6,2.69 6,6s-2.69,6 -6,6 -6,-2.69 -6,-6H4c0,4.42 3.58,8 8,8s8,-3.58 8,-8 -3.58,-8 -8,-8z"
    )
}

/** Phụ đề đang bật: nút CC tô đặc (ic_player_cc của meetube là bản đặc, ic_player_cc_off là bản viền như [WatchCc]). Hai chữ C khoét rỗng. */
val WatchCcOn: ImageVector by lazy {
    ImageVector.Builder("WatchCcOn", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M19,4H5C3.89,4 3,4.9 3,6v12c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6C21,4.9 20.1,4 19,4zM11,11H9.5v-0.5h-2v3h2V13H11v1" +
                    "c0,0.55 -0.45,1 -1,1H7c-0.55,0 -1,-0.45 -1,-1v-4c0,-0.55 0.45,-1 1,-1h3c0.55,0 1,0.45 1,1V11zM18,11h-1.5v-0.5h-2v3h2V13H18v1" +
                    "c0,0.55 -0.45,1 -1,1h-3c-0.55,0 -1,-0.45 -1,-1v-4c0,-0.55 0.45,-1 1,-1h3c0.55,0 1,0.45 1,1V11z"
            ).toNodes(),
            pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd,
            fill = SolidColor(Color.Black)
        )
    }.build()
}
