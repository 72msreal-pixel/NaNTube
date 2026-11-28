package com.nan.nantube.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nan.nantube.data.AccountStore
import com.nan.nantube.data.DownloadPrefs
import com.nan.nantube.data.VideoDownloads
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Trang trợ giúp tải video của YouTube (tiếng Việt), mở bằng trình duyệt. */
private const val DOWNLOAD_HELP_URL = "https://support.google.com/youtube/answer/6141269?hl=vi"

/**
 * Trang con của Cài đặt → "Nội dung tải xuống", bố cục như YouTube, chỉ giữ những mục app có logic thật:
 * - Chất lượng tải xuống ([DownloadPrefs.quality]): "Hỏi mỗi lần" hoặc mức đồ họa tải sẵn, bảng Tải xuống dùng luôn mức này;
 * - Chỉ tải xuống qua Wi-Fi ([DownloadPrefs.wifiOnly]): đang dùng mạng di động thì không bắt đầu tải;
 * - Trợ giúp tải xuống: mở trang trợ giúp của YouTube;
 * - Xóa tất cả video tải xuống ([VideoDownloads.deleteAll]);
 * - Dung lượng còn trống: dung lượng app đã dùng cho video tải và dung lượng còn trống của máy.
 * Mục "Đề xuất video tải xuống" của YouTube bị bỏ vì app không có tính năng tự đề xuất / tự tải video.
 */
@Composable
fun DownloadSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account = AccountStore.selected
    LaunchedEffect(account?.id) { DownloadPrefs.bind(account) }
    BackHandler(onBack = onBack)

    var qualityDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    // Đổi số này để đọc lại dung lượng (sau khi xóa).
    var reload by remember { mutableIntStateOf(0) }
    var usedBytes by remember { mutableLongStateOf(0L) }
    var freeBytes by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(reload) {
        usedBytes = runCatching { VideoDownloads.usedBytes(context.applicationContext) }.getOrDefault(0L)
        freeBytes = withContext(Dispatchers.IO) {
            runCatching { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }.getOrDefault(-1L)
        }
    }

    Column(Modifier.fillMaxSize()) {
        YtSettingsTopBar("Nội dung tải xuống", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            YtSectionHeader("Tải xuống")
            YtClickRow("Chất lượng tải xuống", DownloadPrefs.qualityLabel(DownloadPrefs.quality)) { qualityDialog = true }
            YtSwitchRow(
                title = "Chỉ tải xuống qua Wi-Fi",
                checked = DownloadPrefs.wifiOnly,
                onCheckedChange = DownloadPrefs::updateWifiOnly
            )
            YtClickRow("Trợ giúp tải xuống", "Tìm câu trả lời cho câu hỏi của bạn về cách tải xuống video") {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_HELP_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            YtClickRow("Xóa tất cả video tải xuống") { if (!deleting) confirmDelete = true }
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(thickness = Dp.Hairline, color = YtDivider)
            YtSectionHeader("Dung lượng còn trống")
            HorizontalDivider(thickness = Dp.Hairline, color = YtDivider)
            StorageBar(usedBytes, freeBytes)
            // Chừa chỗ để mục cuối cuộn lên trên thanh điều hướng dưới.
            Spacer(Modifier.height(96.dp))
        }
    }

    if (qualityDialog) {
        QualityDialog(
            selected = DownloadPrefs.quality,
            onSelect = { DownloadPrefs.updateQuality(it); qualityDialog = false },
            onDismiss = { qualityDialog = false }
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Xóa tất cả video tải xuống?",
            text = "Mọi video và âm thanh đã tải bằng NaNTube sẽ bị xóa khỏi thiết bị này.",
            confirm = "Xóa",
            onConfirm = {
                confirmDelete = false
                deleting = true
                scope.launch {
                    try {
                        val count = VideoDownloads.deleteAll(context.applicationContext, account)
                        Toast.makeText(
                            context.applicationContext,
                            if (count > 0) "Đã xóa $count mục đã tải" else "Không có video nào để xóa",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Toast.makeText(context.applicationContext, "Không xóa được: ${e.message ?: "lỗi không rõ"}", Toast.LENGTH_SHORT).show()
                    }
                    deleting = false
                    reload++
                }
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

/**
 * Thanh dung lượng như YouTube: ray xám nhạt cao 8dp là chỗ còn trống, phần xanh bên trái là dung lượng video tải;
 * dưới thanh có chú thích "Đã sử dụng ..." (ô xanh) bên trái và "... miễn phí" (ô xám) bên phải.
 */
@Composable
private fun StorageBar(usedBytes: Long, freeBytes: Long) {
    val total = if (freeBytes >= 0) usedBytes + freeBytes else 0L
    val fraction = if (total > 0) (usedBytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.5.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(8.dp).background(StorageFree)) {
            // Đã dùng chút xíu thì vẫn chừa vạch xanh tối thiểu để nhìn thấy.
            if (usedBytes > 0) Box(Modifier.weight(fraction.coerceIn(0.01f, 1f)).fillMaxSize().background(YtBlue))
            if (fraction < 0.99f) Box(Modifier.weight((1f - fraction).coerceAtLeast(0.01f)).fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(YtBlue))
            Spacer(Modifier.width(6.dp))
            Text("Đã sử dụng ${sizeText(usedBytes)}", fontSize = 13.5.sp, color = YtText, modifier = Modifier.weight(1f))
            Text(
                if (freeBytes >= 0) "${sizeText(freeBytes)} miễn phí" else "—",
                fontSize = 13.5.sp,
                color = YtText
            )
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(8.dp).background(StorageFree))
        }
    }
}

private val StorageFree = Color(0xFFCCCCCC)

/** "0 MB", "512 MB", "22,88 GB": dưới 1 GB tính bằng MB, từ 1 GB trở lên có 2 số lẻ, dấu phẩy thập phân như tiếng Việt. */
private fun sizeText(bytes: Long): String {
    val gb = 1024.0 * 1024 * 1024
    val vi = Locale("vi", "VN")
    return if (bytes >= gb) String.format(vi, "%.2f GB", bytes / gb)
    else String.format(vi, "%d MB", bytes / (1024L * 1024))
}

/** Hộp chọn chất lượng tải xuống, kiểu hộp thoại YouTube: tiêu đề, danh sách radio, nút Hủy. */
@Composable
private fun QualityDialog(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(NanColors.Popup, RoundedCornerShape(28.dp)).padding(top = 24.dp, bottom = 8.dp)) {
            Text(
                "Chất lượng tải xuống",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = YtText,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
            )
            DownloadPrefs.QUALITY_STEPS.forEach { step ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(step) }.padding(start = 24.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(DownloadPrefs.qualityLabel(step), fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f))
                    RadioButton(
                        selected = step == selected,
                        onClick = { onSelect(step) },
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

/** Hộp xác nhận hai nút (Hủy / [confirm]) cùng kiểu với hộp chọn ở trên. */
@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(NanColors.Popup, RoundedCornerShape(28.dp)).padding(top = 24.dp, bottom = 8.dp)) {
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = YtText,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
            )
            Text(
                text,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = NanColors.TextSoft,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
            )
            Row(Modifier.fillMaxWidth().padding(end = 16.dp), horizontalArrangement = Arrangement.End) {
                Text(
                    "Hủy",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = YtBlue,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 12.dp)
                )
                Text(
                    confirm,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = YtBlue,
                    modifier = Modifier.clickable(onClick = onConfirm).padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }
        }
    }
}
