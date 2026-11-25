package com.nan.nantube.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nan.nantube.data.TvPlayback
import com.nan.nantube.data.TvStore
import com.nan.nantube.data.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val FieldBlue = Color(0xFF8AB4F8)

/** Trạng thái mở/đóng màn TV: nút cast ở thanh trên gọi [show] không tham số, menu ⋯ của video gọi [show] kèm video. */
object TvScreen {
    var open by mutableStateOf(false)
        private set
    var video by mutableStateOf<VideoItem?>(null)
        private set

    fun show(video: VideoItem? = null) {
        this.video = video
        open = true
    }

    fun close() {
        open = false
        video = null
    }
}

/**
 * Màn "Thêm thiết bị" dựng giống app YouTube, dùng chung cho hai việc:
 * - mở từ nút cast: nhập mã TV 12 số để ghép (TV được nhớ lại), đang có TV thì hiện tên + Ngắt kết nối;
 * - mở từ menu ⋯ của video ([TvScreen.video] != null): ghép xong thì phát luôn video đó, hoặc phát thẳng lên TV đã ghép.
 */
@Composable
fun TvConnectScreen() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val video = TvScreen.video
    val paired = TvStore.paired
    // Có video + đã ghép TV thì mặc định phát thẳng; "Dùng mã TV khác" mới hiện ô nhập.
    var useCode by remember { mutableStateOf(paired == null) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ready = !busy && (!useCode || code.length == 12)

    BackHandler { if (!busy) TvScreen.close() }

    fun launchTv(block: suspend () -> Unit, done: String) {
        busy = true
        error = null
        scope.launch {
            try {
                block()
                Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
                TvScreen.close()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.shortMessage ?: "Không kết nối được TV. Kiểm tra mã TV và mạng rồi thử lại."
            } finally {
                busy = false
            }
        }
    }

    fun submit() {
        if (!ready) return
        if (useCode) {
            launchTv({
                val tv = TvPlayback.pair(code)
                TvStore.save(tv)
                if (video != null) TvPlayback.playOn(tv, video.id)
            }, if (video != null) "Đã gửi lệnh phát tới TV" else "Đã kết nối với ${TvStore.paired?.name ?: "TV"}")
        } else if (video != null && paired != null) {
            launchTv({ TvPlayback.playOn(paired, video.id) }, "Đã gửi lệnh phát tới TV")
        }
    }

    Column(Modifier.overlayScreen()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(enabled = !busy) { TvScreen.close() },
                contentAlignment = Alignment.Center
            ) { Icon(IconArrowLeft, "Quay lại", Modifier.size(24.dp)) }
            Text(
                if (video != null) "Phát trên thiết bị khác" else "Thêm thiết bị",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 20.dp)
            )
        }

        Column(
            Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!useCode && paired != null) {
                Text(
                    if (video != null) "Phát video này trên ${paired.name}?" else "Đang kết nối với ${paired.name}",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(Modifier.height(20.dp))
                if (video != null) {
                    PillButton(if (busy) "Đang gửi…" else "Phát", enabled = ready) { submit() }
                    Text(
                        "Dùng mã TV khác",
                        fontSize = 15.sp,
                        color = NanColors.Blue,
                        modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !busy) { useCode = true; error = null }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                } else {
                    PillButton("Ngắt kết nối", enabled = true) {
                        TvStore.clear()
                        TvScreen.close()
                    }
                }
            } else {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { ch -> ch in '0'..'9' }.take(12) },
                    label = { Text("Nhập mã TV") },
                    singleLine = true,
                    enabled = !busy,
                    textStyle = TextStyle(fontSize = 18.sp, color = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FieldBlue,
                        unfocusedBorderColor = NanColors.Divider,
                        focusedLabelColor = FieldBlue,
                        unfocusedLabelColor = NanColors.TextSecondary,
                        cursorColor = FieldBlue
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(0.5f).focusRequester(focus)
                )
                Spacer(Modifier.height(20.dp))
                PillButton(
                    if (busy) "Đang kết nối…" else if (video != null) "Phát" else "Thêm",
                    enabled = ready
                ) { submit() }
            }
            error?.let {
                Text(
                    it,
                    fontSize = 13.sp,
                    color = NanColors.TextSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 12.dp)
                )
            }
        }

        if (useCode || paired == null) {
            HorizontalDivider(color = NanColors.Surface)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                Text("Cách lấy mã TV", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(16.dp))
                val style = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, color = NanColors.TextSoft)
                Text("1. Mở ứng dụng YouTube trên TV.", style = style)
                Text("2. Chuyển đến phần Cài đặt.", style = style)
                Text(
                    buildAnnotatedString {
                        append("3. Di chuyển xuống phần ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) { append("Liên kết bằng mã TV") }
                        append(". Một mã TV màu xanh dương sẽ xuất hiện trên TV.")
                    },
                    style = style
                )
                Text("4. Nhập mã đó vào ô phía trên.", style = style)
            }
        } else {
            HorizontalDivider(color = NanColors.Surface)
        }
    }
}

/** Nút viên thuốc của màn này: xám mờ khi chưa bấm được, xanh khi sẵn sàng. */
@Composable
private fun PillButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) NanColors.Blue else NanColors.Divider)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (enabled) Color.Black else Color(0xFF777777))
    }
}
