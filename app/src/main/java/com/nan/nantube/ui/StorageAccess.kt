package com.nan.nantube.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nan.nantube.data.NanStorage

/**
 * Hộp thoại xin quyền để app lưu dữ liệu vào /storage/emulated/0/NaNTube.
 * Hiện mỗi lần mở app cho tới khi được cấp; "Để sau" chỉ ẩn trong lần mở này. [tick] đổi mỗi khi app quay lại
 * từ màn cài đặt để kiểm tra lại quyền.
 */
@Composable
fun StorageAccessPrompt(tick: Int, onGrant: () -> Unit) {
    var dismissed by remember { mutableStateOf(false) }
    val granted = remember(tick) { NanStorage.hasAccess() }
    if (granted || dismissed) return
    AlertDialog(
        onDismissRequest = { dismissed = true },
        containerColor = NanColors.Popup,
        title = { Text("Giữ dữ liệu khi gỡ app") },
        text = {
            Text(
                "NaNTube sẽ lưu video đã tải và bình luận đã lưu (riêng từng tài khoản) vào thư mục NaNTube trong bộ nhớ máy. " +
                    "Gỡ app rồi cài lại, dữ liệu vẫn còn. Hãy bật \"Cho phép quản lý tất cả tệp\" cho NaNTube ở màn tiếp theo.",
                color = NanColors.TextSoft
            )
        },
        confirmButton = {
            TextButton(onClick = { dismissed = true; onGrant() }) { Text("Cấp quyền", color = NanColors.Blue) }
        },
        dismissButton = {
            TextButton(onClick = { dismissed = true }) { Text("Để sau", color = NanColors.TextSecondary) }
        }
    )
}
