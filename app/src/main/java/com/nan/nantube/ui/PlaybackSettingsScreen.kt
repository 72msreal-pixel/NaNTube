package com.nan.nantube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nan.nantube.data.AccountStore
import com.nan.nantube.data.PipPrefs
import com.nan.nantube.data.WatchPrefs

/**
 * Trang con của Cài đặt → "Phát", bố cục như YouTube nhưng chỉ giữ những mục app đã có sẵn:
 * Tự động phát video tiếp theo ([WatchPrefs.autoplay]), Nhấn đúp để tua ([WatchPrefs.seekSeconds]) và
 * Hình trong hình ([PipPrefs.popup]). Mục "Thu phóng vừa với màn hình" của YouTube bị bỏ vì app chưa có tính năng đó.
 */
@Composable
fun PlaybackSettingsScreen(onBack: () -> Unit) {
    val account = AccountStore.selected
    LaunchedEffect(account?.id) { WatchPrefs.bind(account) }
    BackHandler(onBack = onBack)
    var seekDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        YtSettingsTopBar("Phát", onBack)
        YtSwitchRow(
            title = "Tự động phát video tiếp theo",
            subtitle = "Khi bật tính năng tự động phát, video được đề xuất sẽ tự động phát tiếp.",
            checked = WatchPrefs.autoplay,
            onCheckedChange = WatchPrefs::updateAutoplay
        )
        YtValueRow("Nhấn đúp để tua", "${WatchPrefs.seekSeconds} giây") { seekDialog = true }
        YtSwitchRow(
            title = "Hình trong hình",
            subtitle = "Nhấn vào nút Màn hình chính khi đang xem sẽ tiếp tục phát video trong trình phát thu nhỏ trên đầu các ứng dụng khác",
            checked = PipPrefs.popup,
            onCheckedChange = {
                PipPrefs.updatePopup(it)
                if (!it) {
                    ShortsMini.close()
                    WatchMini.close()
                }
            }
        )
    }
    if (seekDialog) {
        SeekDialog(selected = WatchPrefs.seekSeconds, onSelect = { WatchPrefs.updateSeekSeconds(it); seekDialog = false }, onDismiss = { seekDialog = false })
    }
}

/** Hàng chỉ có tên + giá trị hiện tại (bấm để mở hộp chọn). */
@Composable
private fun YtValueRow(title: String, value: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 17.5.dp)) {
        YtRowTitle(title)
        YtRowSub(value)
    }
}

/** Hộp chọn số giây tua (các mức [WatchPrefs.SEEK_STEPS]), kiểu hộp thoại YouTube: tiêu đề, danh sách radio, nút Hủy. */
@Composable
private fun SeekDialog(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(NanColors.Popup, RoundedCornerShape(28.dp)).padding(top = 24.dp, bottom = 8.dp)) {
            Text(
                "Nhấn đúp để tua",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = YtText,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
            )
            WatchPrefs.SEEK_STEPS.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(s) }.padding(start = 24.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$s giây", fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f))
                    RadioButton(
                        selected = s == selected,
                        onClick = { onSelect(s) },
                        colors = RadioButtonDefaults.colors(selectedColor = YtBlue, unselectedColor = NanColors.TextSecondary)
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(end = 16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                Text(
                    "Hủy",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = YtBlue,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }
        }
    }
}

