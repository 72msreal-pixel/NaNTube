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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
            title = "Xem video dạng popup",
            subtitle = "Bật: Short thu thành cửa sổ nhỏ trong app, và thành PiP khi bạn thoát ra màn hình chính. Tắt: không tạo popup nào, bấm nút Hình trong hình sẽ mở trang cài đặt này.",
            checked = PipPrefs.popup,
            onCheckedChange = {
                PipPrefs.updatePopup(it)
                if (!it) ShortsMini.close()
            }
        )
        NanSwitchRow(
            title = "Hỗ trợ phát nền video",
            subtitle = "Bật: Short vẫn phát tiếng khi tắt màn hình hoặc sang app khác, có thẻ điều khiển media. Tắt: chỉ xem video, rời app là dừng.",
            checked = PipPrefs.background,
            onCheckedChange = {
                PipPrefs.updateBackground(it)
                if (!it && ShortsBackground.appVisible) ShortsPlaybackService.stop(context)
                if (it) ShortsBackground.onPopupOpened()
            }
        )
        // Hãng máy (realme / ColorOS, Xiaomi...) vẫn đóng băng app vài phút sau khi tắt màn hình nếu còn bị tối ưu pin.
        var refresh by remember { mutableIntStateOf(0) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        val exempt = remember(refresh) { BackgroundPlay.isExempt(context) }
        NanSwitchRow(
            title = "Chạy nền không giới hạn",
            subtitle = when {
                !PipPrefs.unrestricted -> "Tắt: app không xin quyền này nữa, máy có thể ngắt tiếng sau vài phút tắt màn hình."
                exempt -> "Đã cho phép: video phát nền không bị hệ thống ngắt."
                else -> "Chưa cho phép: máy có thể ngắt tiếng sau vài phút tắt màn hình. Bấm công tắc để cấp quyền."
            },
            checked = PipPrefs.unrestricted,
            onCheckedChange = {
                PipPrefs.updateUnrestricted(it)
                if (it && !exempt) (context as? android.app.Activity)?.let { activity -> BackgroundPlay.request(activity) }
            }
        )
    }
}
