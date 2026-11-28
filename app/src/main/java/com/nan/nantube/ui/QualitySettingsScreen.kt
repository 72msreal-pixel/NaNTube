package com.nan.nantube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nan.nantube.data.AccountStore
import com.nan.nantube.data.AudioQualityMode
import com.nan.nantube.data.QualityPrefs
import com.nan.nantube.data.VideoQualityMode

private class QualityOption<T>(val value: T, val title: String, val subtitle: String)

private val VIDEO_OPTIONS = listOf(
    QualityOption(VideoQualityMode.AUTO, "Tự động (khuyên dùng)", "Điều chỉnh để có trải nghiệm tuyệt vời nhất theo điều kiện của bạn"),
    QualityOption(VideoQualityMode.HIGH, "Chất lượng hình ảnh cao hơn", "Dùng nhiều dữ liệu hơn"),
    QualityOption(VideoQualityMode.SAVER, "Tiết kiệm dữ liệu", "Chất lượng hình ảnh thấp hơn")
)

private val AUDIO_OPTIONS = listOf(
    QualityOption(AudioQualityMode.HIGH, "Chất lượng âm thanh cao hơn", "Chọn luồng âm thanh có bitrate cao nhất của video. Dùng nhiều dữ liệu hơn."),
    QualityOption(AudioQualityMode.NORMAL, "Bình thường", "Dùng ít dữ liệu hơn")
)

/** Trang con của Cài đặt → "Chất lượng": mức video riêng cho mạng di động / Wi-Fi và chất lượng âm thanh, lưu theo tài khoản. */
@Composable
fun QualitySettingsScreen(onBack: () -> Unit) {
    val account = AccountStore.selected
    LaunchedEffect(account?.id) { QualityPrefs.bind(account) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        YtSettingsTopBar("Chất lượng", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                "Bạn có thể đặt chất lượng phát trực tuyến ưu tiên làm chế độ mặc định cho mọi video và đổi chất lượng này cho từng video ở mục cài đặt của trình phát.",
                fontSize = 13.5.sp,
                lineHeight = 17.sp,
                color = NanColors.TextSecondary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)
            )
            QualitySection("Chất lượng video trên mạng di động", VIDEO_OPTIONS, QualityPrefs.mobile, QualityPrefs::updateMobile)
            QualitySection("Chất lượng video trên mạng Wi-Fi", VIDEO_OPTIONS, QualityPrefs.wifi, QualityPrefs::updateWifi)
            QualitySection("Chất lượng âm thanh", AUDIO_OPTIONS, QualityPrefs.audio, QualityPrefs::updateAudio)
            // Chừa chỗ để mục cuối cuộn lên trên thanh điều hướng dưới.
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun <T> QualitySection(title: String, options: List<QualityOption<T>>, selected: T, onSelect: (T) -> Unit) {
    HorizontalDivider(thickness = Dp.Hairline, color = YtDivider)
    YtSectionHeader(title)
    options.forEach { option ->
        Row(
            // Hàng: lề dọc 17,5dp; radio (hộp 48dp) cách lề phải 12dp.
            Modifier.fillMaxWidth().clickable { onSelect(option.value) }.padding(start = 16.dp, end = 12.dp, top = 17.5.dp, bottom = 17.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                YtRowTitle(option.title)
                YtRowSub(option.subtitle)
            }
            RadioButton(
                selected = option.value == selected,
                onClick = { onSelect(option.value) },
                colors = RadioButtonDefaults.colors(selectedColor = YtBlue, unselectedColor = NanColors.TextSecondary)
            )
        }
    }
}
