package com.nan.nantube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Thông số đo từ màn hình "Chất lượng" của YouTube thật (dp / sp).
internal val YtText = Color(0xFFF1F1F1)
internal val YtDivider = Color.White.copy(alpha = 0.12f)
internal val YtBlue = Color(0xFF3EA6FF)

/** Mũi tên quay lại kiểu YouTube: đầu mũi tên Material nhưng thân dài hơn (18.2 x 16dp trong khung 24dp). */
internal val YtBackArrow: ImageVector by lazy {
    ImageVector.Builder("YtBack", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(21.2f, 11f)
            horizontalLineTo(6.83f)
            lineTo(12.42f, 5.41f)
            lineTo(11f, 4f)
            lineTo(3f, 12f)
            lineTo(11f, 20f)
            lineTo(12.42f, 18.59f)
            lineTo(6.83f, 13f)
            horizontalLineTo(21.2f)
            close()
        }
    }.build()
}


/** Thanh tiêu đề trang con của Cài đặt kiểu YouTube: cao 48dp, nút back 48dp (icon 24dp cách lề 16dp), chữ tiêu đề bắt đầu ở 72dp. */
@Composable
internal fun YtSettingsTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(48.dp).padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(YtBackArrow, null, Modifier.size(24.dp), tint = YtText)
        }
        Spacer(Modifier.width(20.dp))
        Text(title, fontSize = 19.5.sp, color = YtText)
    }
}

/** Tên mục (15sp, cao 1,25 lần như YouTube). */
@Composable
internal fun YtRowTitle(text: String) = Text(text, fontSize = 15.sp, lineHeight = 18.75.sp, color = Color.White)

/** Mô tả mục (13,5sp), cách tên mục 2dp. */
@Composable
internal fun YtRowSub(text: String) =
    Text(text, fontSize = 13.5.sp, lineHeight = 17.sp, color = NanColors.TextSecondary, modifier = Modifier.padding(top = 2.dp))

/** Tên nhóm (đậm 13,5sp): cách phía trên 24dp, phía dưới 7dp. */
@Composable
internal fun YtSectionHeader(text: String) {
    Text(
        text,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = YtText,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 7.dp)
    )
}

/** Vạch ngăn dày giữa hai nhóm (cao 6dp, xám #3F3F3F). */
@Composable
internal fun YtThickDivider() {
    Box(Modifier.fillMaxWidth().height(6.dp).background(NanColors.Divider))
}

/** Hàng bấm được chỉ có tên (và mô tả nếu có): lề ngang 16dp, lề dọc 17,5dp. Không có [subtitle] thì cao 54dp. */
@Composable
internal fun YtClickRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 17.5.dp)) {
        YtRowTitle(title)
        if (subtitle != null) YtRowSub(subtitle)
    }
}

/** Hàng có công tắc: lề trái 16dp, lề phải 24dp, lề dọc 17,5dp. Không có [subtitle] thì cao 54dp. */
@Composable
internal fun YtSwitchRow(title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(start = 16.dp, end = 24.dp, top = 17.5.dp, bottom = 17.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            YtRowTitle(title)
            if (subtitle != null) YtRowSub(subtitle)
        }
        Spacer(Modifier.width(16.dp))
        NanSwitch(checked, onCheckedChange)
    }
}
