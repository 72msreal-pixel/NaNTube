package com.nan.nantube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
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
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Lucide.ArrowLeft, null, Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack).padding(10.dp))
            Spacer(Modifier.width(12.dp))
            Text("Chất lượng", fontSize = 22.sp)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                "Bạn có thể đặt chất lượng phát trực tuyến ưu tiên làm chế độ mặc định cho mọi video và đổi chất lượng này cho từng video ở mục cài đặt của trình phát.",
                fontSize = 15.sp,
                color = NanColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            QualitySection("Chất lượng video trên mạng di động", VIDEO_OPTIONS, QualityPrefs.mobile, QualityPrefs::updateMobile)
            QualitySection("Chất lượng video trên mạng Wi-Fi", VIDEO_OPTIONS, QualityPrefs.wifi, QualityPrefs::updateWifi)
            QualitySection("Chất lượng âm thanh", AUDIO_OPTIONS, QualityPrefs.audio, QualityPrefs::updateAudio)
        }
    }
}

@Composable
private fun <T> QualitySection(title: String, options: List<QualityOption<T>>, selected: T, onSelect: (T) -> Unit) {
    HorizontalDivider(color = NanColors.Surface)
    Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
    options.forEach { option ->
        Row(
            Modifier.fillMaxWidth().clickable { onSelect(option.value) }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(option.title, fontSize = 16.sp)
                Text(option.subtitle, fontSize = 13.sp, color = NanColors.TextSecondary)
            }
            RadioButton(
                selected = option.value == selected,
                onClick = { onSelect(option.value) },
                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF3EA6FF), unselectedColor = NanColors.TextSecondary)
            )
        }
    }
}
