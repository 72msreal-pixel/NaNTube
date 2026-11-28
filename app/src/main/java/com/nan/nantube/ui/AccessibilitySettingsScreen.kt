package com.nan.nantube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nan.nantube.data.AccountStore
import com.nan.nantube.data.WatchPrefs

/**
 * Trang con của Cài đặt → "Hỗ trợ tiếp cận", bố cục như YouTube:
 * - Trình phát hỗ trợ tiếp cận ([WatchPrefs.a11yPlayer]): bật thì trình phát có hai nút tua ±N giây ở giữa (thay cho nhấn đúp),
 *   chạm vào video không ẩn được các nút, chỉ nút X ở góc trên mới ẩn (xem WatchPlayerArea);
 * - Ẩn các nút điều khiển trình phát ([WatchPrefs.hideControlsSeconds]): Không bao giờ hoặc tự ẩn sau vài giây khi đang phát.
 *   Mờ đi và không bấm được khi công tắc trên đang tắt.
 */
@Composable
fun AccessibilitySettingsScreen(onBack: () -> Unit) {
    val account = AccountStore.selected
    LaunchedEffect(account?.id) { WatchPrefs.bind(account) }
    // Mở màn này sau khi đã quá hạn tự tắt thì công tắc phải hiện đã tắt.
    LaunchedEffect(Unit) { WatchPrefs.checkA11yExpiry() }
    BackHandler(onBack = onBack)
    var hideDialog by remember { mutableStateOf(false) }
    var autoOffDialog by remember { mutableStateOf(false) }
    val on = WatchPrefs.a11yPlayer
    Column(Modifier.fillMaxSize()) {
        YtSettingsTopBar("Hỗ trợ tiếp cận", onBack)
        YtSwitchRow(
            title = "Trình phát hỗ trợ tiếp cận",
            subtitle = "Hiển thị các nút điều khiển hỗ trợ tiếp cận trong trình phát",
            checked = on,
            onCheckedChange = WatchPrefs::updateA11yPlayer
        )
        Column(Modifier.alpha(if (on) 1f else 0.4f)) {
            YtClickRow("Ẩn các nút điều khiển trình phát", hideLabel(WatchPrefs.hideControlsSeconds)) {
                if (on) hideDialog = true
            }
            YtClickRow("Tự động tắt trình phát hỗ trợ tiếp cận", autoOffLabel(WatchPrefs.a11yAutoOffDays)) {
                if (on) autoOffDialog = true
            }
        }
    }
    if (hideDialog) {
        ChoiceDialog(
            title = "Ẩn các nút điều khiển trình phát",
            options = WatchPrefs.HIDE_STEPS,
            label = ::hideLabel,
            selected = WatchPrefs.hideControlsSeconds,
            onSelect = { WatchPrefs.updateHideControlsSeconds(it); hideDialog = false },
            onDismiss = { hideDialog = false }
        )
    }
    if (autoOffDialog) {
        ChoiceDialog(
            title = "Tự động tắt trình phát hỗ trợ tiếp cận",
            options = WatchPrefs.AUTO_OFF_DAYS,
            label = ::autoOffLabel,
            selected = WatchPrefs.a11yAutoOffDays,
            onSelect = { WatchPrefs.updateA11yAutoOffDays(it); autoOffDialog = false },
            onDismiss = { autoOffDialog = false }
        )
    }
}

private fun hideLabel(seconds: Int): String = if (seconds <= 0) "Không bao giờ" else "$seconds giây"

private fun autoOffLabel(days: Int): String = if (days <= 0) "Không bao giờ" else "$days ngày"

/** Hộp chọn một mức trong [options], kiểu hộp thoại YouTube: tiêu đề, danh sách radio, nút Hủy. */
@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Int>,
    label: (Int) -> String,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(NanColors.Popup, RoundedCornerShape(28.dp)).padding(top = 24.dp, bottom = 8.dp)) {
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = YtText,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
            )
            options.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(s) }.padding(start = 24.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label(s), fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f))
                    RadioButton(
                        selected = s == selected,
                        onClick = { onSelect(s) },
                        colors = RadioButtonDefaults.colors(selectedColor = YtBlue, unselectedColor = NanColors.TextSecondary)
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(end = 16.dp), horizontalArrangement = Arrangement.End) {
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
