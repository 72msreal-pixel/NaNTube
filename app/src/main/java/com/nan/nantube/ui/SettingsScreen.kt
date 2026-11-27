package com.nan.nantube.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Accessibility
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.FlaskConical
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Radio
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.Timer
import com.composables.icons.lucide.Tv
import com.composables.icons.lucide.User
import com.composables.icons.lucide.Users
import com.nan.nantube.BuildConfig
import com.nan.nantube.R

private const val EXPERIMENTS = "Thử nghiệm các tính năng mới"

private data class SettingItem(val icon: ImageVector, val title: String)

private val accountItems = listOf(
    SettingItem(Lucide.Settings, "Chung"),
    SettingItem(Lucide.User, "Chuyển đổi hoặc quản lý tài khoản"),
    SettingItem(Lucide.Users, "Trung tâm dành cho gia đình"),
    SettingItem(Lucide.Languages, "Ngôn ngữ"),
    SettingItem(Lucide.Timer, "Quản lý thời gian"),
    SettingItem(Lucide.Bell, "Thông báo"),
    SettingItem(Lucide.Tag, "Giao dịch mua và gói thành viên"),
    SettingItem(Lucide.CreditCard, "Lập hóa đơn và thanh toán"),
    SettingItem(Lucide.History, "Quản lý toàn bộ nhật ký hoạt động"),
    SettingItem(Lucide.ShieldCheck, "Dữ liệu của bạn trong NaNTube"),
    SettingItem(Lucide.Lock, "Quyền riêng tư"),
    SettingItem(Lucide.Link, "Ứng dụng đã kết nối"),
    SettingItem(Lucide.FlaskConical, EXPERIMENTS)
)

private val videoItems = listOf(
    SettingItem(Lucide.Sparkles, "Chất lượng"),
    SettingItem(Lucide.Play, "Phát"),
    SettingItem(Lucide.MessageSquare, "Phụ đề"),
    SettingItem(Lucide.SlidersHorizontal, "Tiết kiệm dữ liệu"),
    SettingItem(Lucide.Download, "Nội dung tải xuống"),
    SettingItem(Lucide.Radio, "Trò chuyện trực tiếp"),
    SettingItem(Lucide.Accessibility, "Hỗ trợ tiếp cận"),
    SettingItem(Lucide.Tv, "Xem trên TV")
)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var page by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(PipState.openExperiments) {
        if (PipState.openExperiments) {
            page = EXPERIMENTS
            PipState.openExperiments = false
        }
    }
    if (page == EXPERIMENTS) {
        ExperimentsScreen(onBack = { page = null })
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Lucide.ArrowLeft,
                null,
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack).padding(10.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text("Cài đặt", fontSize = 22.sp)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item { SectionHeader("Tài khoản") }
            items(accountItems) { item ->
                SettingRow(item.title, onClick = { if (item.title == EXPERIMENTS) page = EXPERIMENTS }) { Icon(item.icon, null, Modifier.size(24.dp)) }
            }
            item { HorizontalDivider(color = NanColors.Surface) }
            item { SectionHeader("Lựa chọn ưu tiên về video và âm thanh") }
            items(videoItems) { item ->
                SettingRow(item.title) { Icon(item.icon, null, Modifier.size(24.dp)) }
            }
            item { HorizontalDivider(color = NanColors.Surface) }
            item { SectionHeader("Giới thiệu") }
            item {
                SettingRow("NaNTube", "Phiên bản ${BuildConfig.VERSION_NAME}") {
                    Image(painterResource(R.drawable.nantube_logo), null, Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String? = null, onClick: () -> Unit = {}, leading: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(Modifier.width(20.dp))
        Column {
            Text(title, fontSize = 16.sp)
            if (subtitle != null) {
                Text(subtitle, fontSize = 13.sp, color = NanColors.TextSecondary)
            }
        }
    }
}
