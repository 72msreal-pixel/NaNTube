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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
        Row(
            Modifier.fillMaxWidth()
                .clickable { PipPrefs.setEnabled(context, !PipPrefs.enhanced) }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Cải thiện trải nghiệm dùng PiP", fontSize = 16.sp)
                Text(
                    "Khi rời app, Short đang phát thành cửa sổ nổi do NaNTube tự vẽ: chạm vào video để ẩn / hiện nút, kéo để di chuyển. " +
                        "Cần quyền \"Hiển thị trên các ứng dụng khác\".",
                    fontSize = 13.sp,
                    color = NanColors.TextSecondary
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = PipPrefs.enhanced, onCheckedChange = { PipPrefs.setEnabled(context, it) })
        }
    }
}
