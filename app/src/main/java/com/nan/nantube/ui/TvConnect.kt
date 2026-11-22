package com.nan.nantube.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nan.nantube.data.TvPlayback
import com.nan.nantube.data.TvStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Nút "Kết nối với TV" ở thanh trên (như nút cast của YouTube): nhập mã TV 12 số để ghép, TV được nhớ lại
 * và mục "Phát trên thiết bị khác" của menu video sẽ phát thẳng lên TV này. Đang có TV thì hiện tên + Ngắt kết nối.
 */
@Composable
fun TvConnectDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val paired = TvStore.paired
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ready = !busy && code.length == 12

    fun connect() {
        if (!ready) return
        busy = true
        error = null
        scope.launch {
            try {
                val tv = TvPlayback.pair(code)
                TvStore.save(tv)
                Toast.makeText(context, "Đã kết nối với ${tv.name}", Toast.LENGTH_SHORT).show()
                onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Không kết nối được TV. Kiểm tra mã TV và mạng rồi thử lại."
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = NanColors.Popup,
        title = { Text(if (paired != null) "Đã kết nối TV" else "Kết nối với TV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (paired != null) {
                    Text("Đang kết nối với ${paired.name}. Chọn \"Phát trên thiết bị khác\" ở menu ba chấm của video để phát lên TV này.")
                } else {
                    Text("Trên YouTube của TV, mở Cài đặt → Liên kết bằng mã TV rồi nhập mã vào đây.")
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter { ch -> ch in '0'..'9' }.take(12) },
                        label = { Text("Mã TV") },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { connect() })
                    )
                    error?.let { Text(it, color = NanColors.TextSoft) }
                }
            }
        },
        confirmButton = {
            if (paired != null) {
                TextButton(onClick = { TvStore.clear(); onDismiss() }) { Text("Ngắt kết nối") }
            } else {
                TextButton(enabled = ready, onClick = { connect() }) { Text(if (busy) "Đang kết nối…" else "Kết nối") }
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(if (paired != null) "Đóng" else "Hủy") } }
    )
}
