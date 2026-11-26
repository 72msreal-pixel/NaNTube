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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.nan.nantube.data.PipPrefs

/** Trang con của Cài đặt → "Thử nghiệm các tính năng mới". */
@Composable
fun ExperimentsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
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
            Text("Thử nghiệm các tính năng mới", fontSize = 22.sp)
        }
        NanSwitchRow(
            title = "Tự vào PiP khi rời app",
            subtitle = "Short đang phát thì thu thành cửa sổ nhỏ khi bạn thoát ra màn hình chính. Tắt thì Short chỉ phát tiếng ở nền.",
            checked = PipPrefs.autoPip,
            onCheckedChange = { PipPrefs.updateAutoPip(it) }
        )
        // Hãng máy (realme / ColorOS, Xiaomi...) vẫn đóng băng app vài phút sau khi tắt màn hình nếu còn bị tối ưu pin.
        var refresh by remember { mutableIntStateOf(0) }
        val exempt = remember(refresh) { BackgroundPlay.isExempt(context) }
        Column(
            Modifier.fillMaxWidth().clickable {
                (context as? android.app.Activity)?.let { BackgroundPlay.request(it) }
                refresh++
            }.padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text("Cho phép chạy nền không giới hạn", fontSize = 16.sp)
            Text(
                if (exempt) "Đã cho phép: Short phát nền không bị hệ thống ngắt." else "Chưa cho phép: máy có thể ngắt tiếng sau vài phút tắt màn hình. Bấm để mở cài đặt.",
                fontSize = 13.sp,
                color = NanColors.TextSecondary
            )
        }
    }
}
