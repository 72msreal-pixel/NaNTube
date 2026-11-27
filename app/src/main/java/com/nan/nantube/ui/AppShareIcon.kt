package com.nan.nantube.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Icon Chia sẻ DUY NHẤT của app: mũi tên cong viền rỗng, bo tròn (vẽ lại từ ảnh icon người dùng đưa, viewBox 24, rộng 20).
 * Mọi chỗ cần nút Chia sẻ (video dài, Shorts, menu ⋯, trình phát nhạc, menu bình luận) đều trỏ về đây;
 * muốn đổi icon thì chỉ sửa path ở file này. Tô đặc kiểu even-odd nên phần giữa rỗng; màu do Icon(tint = ...) quyết định.
 */
val AppShareIcon: ImageVector by lazy {
    ImageVector.Builder("AppShareIcon", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M13.26,20.84L13.03,20.84L12.81,20.80L12.63,20.71L12.49,20.62L12.27,20.34L12.12,19.98L12.10,19.71L12.10,16.66L12.05,16.60L10.66,16.57L9.85,16.60L9.00,16.69L7.92,16.91L7.34,17.09L6.75,17.33L6.03,17.72L5.54,18.09L5.28,18.32L4.86,18.77L4.59,19.13L4.23,19.71L4.03,20.12L3.90,20.30L3.75,20.44L3.61,20.53L3.30,20.63L2.94,20.62L2.67,20.53L2.40,20.32L2.26,20.16L2.17,20.03L2.07,19.76L2.06,19.62L2.03,19.18L2.00,18.05L2.03,16.93L2.12,15.99L2.30,14.87L2.53,13.97L2.79,13.16L3.15,12.36L3.51,11.68L3.96,11.01L4.32,10.56L4.70,10.16L5.00,9.88L5.41,9.56L6.08,9.11L6.57,8.84L7.43,8.48L8.32,8.21L8.91,8.08L9.49,7.99L10.61,7.90L12.08,7.92L12.10,7.83L12.10,4.28L12.13,3.97L12.26,3.65L12.45,3.43L12.58,3.33L12.81,3.23L13.17,3.15L13.44,3.18L13.62,3.24L13.84,3.37L13.96,3.47L21.60,11.11L21.75,11.28L21.93,11.64L21.98,11.82L22.00,12.04L21.98,12.31L21.92,12.49L21.80,12.72L21.69,12.83L13.98,20.54L13.62,20.75ZM13.60,18.91L20.47,12.04L20.47,12.00L13.57,5.10L13.54,5.13L13.53,8.41L13.49,8.63L13.37,8.90L13.27,9.04L13.08,9.23L12.81,9.37L12.49,9.42L11.60,9.38L10.88,9.37L9.85,9.43L9.00,9.56L8.14,9.79L7.38,10.09L6.93,10.32L6.57,10.54L6.30,10.73L5.77,11.17L5.23,11.77L4.90,12.22L4.51,12.89L4.15,13.70L3.87,14.55L3.73,15.09L3.56,16.08L3.46,16.98L3.41,18.19L3.43,18.29L3.82,17.78L4.18,17.38L4.69,16.92L5.05,16.65L5.45,16.39L5.86,16.17L6.35,15.94L6.84,15.75L7.43,15.57L7.96,15.44L9.13,15.26L9.67,15.21L10.75,15.17L12.23,15.21L12.76,15.26L12.99,15.35L13.12,15.44L13.27,15.59L13.46,15.90L13.50,16.03L13.54,16.30L13.54,18.91L13.57,18.93Z"
            ).toNodes(),
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd
        )
    }.build()
}
