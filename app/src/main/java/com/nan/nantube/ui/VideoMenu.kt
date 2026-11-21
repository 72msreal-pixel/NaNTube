package com.nan.nantube.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Lucide
import com.nan.nantube.data.Account
import com.nan.nantube.data.PlaylistOption
import com.nan.nantube.data.PlaylistRepository
import com.nan.nantube.data.TvPlayback
import com.nan.nantube.data.VideoDownloads
import com.nan.nantube.data.VideoItem
import com.nan.nantube.data.VideoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Việc chỉnh danh sách phát chạy ở scope riêng: người dùng đóng bảng ngay sau khi chạm thì yêu cầu vẫn được gửi đi
 * (và thông báo vẫn hiện), không bị hủy theo bảng.
 */
private val editScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

private fun showToast(context: Context, text: String) {
    Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
}

private val SheetScrim = Color(0x99000000)

// ---- Menu "⋯" của video ---------------------------------------------------------------------

/**
 * Nút ba chấm trên thẻ video; chạm vào hiện bảng menu nổi như của YouTube.
 * [onSaveToPlaylist] mở bảng "Lưu vào…" (nằm ở AppRoot để phủ cả thanh dưới).
 */
@Composable
fun VideoMoreButton(
    video: VideoItem,
    account: Account?,
    onSaveToPlaylist: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
    onRemoved: (VideoItem) -> Unit
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var showCast by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }

    Box(modifier) {
        MoreButton(width = 32.dp) { open = true }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = NanColors.Surface,
            tonalElevation = 0.dp
        ) {
            MenuRow(CastIcon, "Phát trên thiết bị khác") {
                open = false
                showCast = true
            }
            MenuRow(Lucide.Clock, "Lưu vào danh sách Xem sau") {
                open = false
                editScope.launch {
                    try {
                        PlaylistRepository.add(account, "WL", video.id)
                        showToast(context, "Đã lưu vào Xem sau")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        showToast(context, e.message ?: "Không lưu được video")
                    }
                }
            }
            MenuRow(BookmarkOutline, "Lưu vào danh sách phát") {
                open = false
                onSaveToPlaylist(video)
            }
            MenuRow(Lucide.Download, "Tải xuống") {
                open = false
                if (!downloading) {
                    downloading = true
                    editScope.launch {
                        try {
                            VideoDownloads.enqueue(context.applicationContext, account, video)
                            showToast(context, "Đã đưa video vào hàng đợi tải")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            showToast(context, "Không tải được video hoặc YouTube không cung cấp MP4 trực tiếp")
                        } finally {
                            downloading = false
                        }
                    }
                }
            }
            MenuRow(ShareIcon, "Chia sẻ") {
                open = false
                try {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, video.watchUrl)
                    context.startActivity(Intent.createChooser(send, null))
                } catch (_: Exception) {
                    showToast(context, "Không mở được trình chia sẻ")
                }
            }
            MenuRow(TrashIcon, "Xóa khỏi danh sách video đã xem") {
                open = false
                if (!removing) {
                    removing = true
                    editScope.launch {
                        try {
                            VideoRepository.removeFromHistory(account, video)
                            onRemoved(video)
                            showToast(context, "Đã xóa khỏi danh sách video đã xem")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            showToast(context, e.message ?: "Không xóa được video khỏi lịch sử")
                        } finally {
                            removing = false
                        }
                    }
                }
            }
        }
    }
    if (showCast) TvCodeDialog(video) { showCast = false }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = Color.White)
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 16.sp, color = Color.White, maxLines = 1)
    }
}

// ---- Bảng "Lưu vào…" ------------------------------------------------------------------------

@Composable
private fun SheetHandle() {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF606060))
        )
    }
}

/**
 * Bảng "Lưu vào…" trượt từ đáy: mỗi danh sách phát một dòng, chạm để lưu / bỏ lưu video
 * (dấu trang đặc = đã lưu), và nút "Danh sách phát mới".
 */
@Composable
fun SaveToPlaylistSheet(video: VideoItem, account: Account?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var options by remember { mutableStateOf<List<PlaylistOption>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var covers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // Trạng thái người dùng vừa đổi (ghi đè lên trạng thái YouTube trả về) và các danh sách đang chờ phản hồi.
    var saved by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var pending by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showCreate by remember { mutableStateOf(false) }

    BackHandler(onBack = onDismiss)

    LaunchedEffect(video.id, account?.id) {
        try {
            options = PlaylistRepository.options(account, video.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Không tải được danh sách phát."
        }
    }
    // Ảnh bìa mỗi danh sách = video đầu tiên của nó (như YouTube); lỗi thì giữ ô trống.
    LaunchedEffect(options) {
        val list = options ?: return@LaunchedEffect
        coroutineScope {
            list.forEach { o ->
                launch {
                    val cover = try {
                        VideoRepository.playlistPage(account, o.id).items.firstOrNull()?.coverThumbnail
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    if (cover != null) covers = covers + (o.id to cover)
                }
            }
        }
    }

    fun toggle(o: PlaylistOption) {
        if (o.id in pending) return
        val target = !(saved[o.id] ?: o.saved)
        saved = saved + (o.id to target)
        pending = pending + o.id
        editScope.launch {
            try {
                if (target) PlaylistRepository.add(account, o.id, video.id)
                else PlaylistRepository.remove(account, o.id, video.id)
                showToast(context, if (target) "Đã lưu vào ${o.title}" else "Đã xóa khỏi ${o.title}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saved = saved + (o.id to !target)
                showToast(context, e.message ?: "Không cập nhật được danh sách phát")
            } finally {
                pending = pending - o.id
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(SheetScrim).plainClickable(onDismiss)) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.75f).dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(NanColors.Popup)
                    .plainClickable { }
                    .navigationBarsPadding()
            ) {
                SheetHandle()
                Text(
                    "Lưu vào...",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
                val list = options
                when {
                    error != null -> Text(
                        error.orEmpty(),
                        fontSize = 14.sp,
                        color = NanColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                    )
                    list == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                    }
                    else -> LazyColumn(Modifier.weight(1f, fill = false)) {
                        items(list, key = { it.id }) { o ->
                            PlaylistChoiceRow(o, covers[o.id], saved[o.id] ?: o.saved) { toggle(o) }
                        }
                    }
                }
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF3F3F3F))
                        .clickable { showCreate = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(PlusIcon, null, Modifier.size(22.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Danh sách phát mới", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
                }
            }
        }
        if (showCreate) {
            NewPlaylistDialog(
                video = video,
                account = account,
                onClose = { showCreate = false },
                onCreated = onDismiss
            )
        }
    }
}

@Composable
private fun PlaylistChoiceRow(option: PlaylistOption, coverUrl: String?, saved: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(80.dp)) {
            Box(
                Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(Color(0xFF7A7A7A))
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NanColors.Surface)
            ) {
                if (!coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(option.title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            option.privacyLabel?.let { Text(it, fontSize = 14.sp, color = NanColors.TextSecondary, maxLines = 1) }
        }
        Icon(
            if (saved) BookmarkFilled else BookmarkOutline,
            null,
            Modifier.size(28.dp),
            tint = Color.White
        )
    }
}

// ---- Hộp thoại "Danh sách phát mới" ---------------------------------------------------------

private enum class PrivacyChoice(val api: String, val label: String, val hint: String, val icon: ImageVector) {
    Public("PUBLIC", "Công khai", "Mọi người có thể tìm kiếm và xem", GlobeIcon),
    Unlisted("UNLISTED", "Không công khai", "Bất kỳ ai có đường liên kết đều có thể xem", LinkIcon),
    Private("PRIVATE", "Riêng tư", "Chỉ bạn mới có thể xem", LockIcon)
}

@Composable
private fun NewPlaylistDialog(video: VideoItem, account: Account?, onClose: () -> Unit, onCreated: () -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var title by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(PrivacyChoice.Private) }
    var showPrivacy by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val field = RoundedCornerShape(12.dp)
    val canCreate = title.isNotBlank() && !creating

    BackHandler(enabled = !showPrivacy, onBack = onClose)
    LaunchedEffect(Unit) { requester.requestFocus() }

    fun create() {
        val name = title.trim()
        if (name.isEmpty() || creating) return
        creating = true
        editScope.launch {
            try {
                PlaylistRepository.create(account, name, privacy.api, video.id)
                showToast(context, "Đã lưu vào $name")
                onCreated()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                creating = false
                showToast(context, e.message ?: "Không tạo được danh sách phát")
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(SheetScrim).plainClickable(onClose).imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .padding(horizontal = 28.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(NanColors.Popup)
                    .plainClickable { }
                    .padding(24.dp)
            ) {
                Text("Danh sách phát mới", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(Modifier.height(20.dp))

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(field)
                        .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else NanColors.Divider, field)
                        .plainClickable { requester.requestFocus() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Tiêu đề", fontSize = 14.sp, lineHeight = 18.sp, color = NanColors.TextSecondary)
                    BasicTextField(
                        value = title,
                        onValueChange = { if (it.length <= 150) title = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { create() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .focusRequester(requester)
                            .onFocusChanged { focused = it.isFocused }
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(field)
                        .border(1.dp, NanColors.Divider, field)
                        .clickable {
                            focus.clearFocus()
                            showPrivacy = true
                        }
                        .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Chế độ hiển thị", fontSize = 14.sp, lineHeight = 18.sp, color = NanColors.TextSecondary)
                        Text(privacy.label, fontSize = 16.sp, color = Color.White)
                    }
                    Icon(Lucide.ChevronDown, null, Modifier.size(24.dp), tint = Color.White)
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val pill = RoundedCornerShape(50)
                    Box(
                        Modifier
                            .height(40.dp)
                            .clip(pill)
                            .border(1.dp, NanColors.Divider, pill)
                            .clickable(onClick = onClose)
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Hủy", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .height(40.dp)
                            .clip(pill)
                            .background(if (canCreate) Color(0xFFF1F1F1) else Color(0xFF3F3F3F))
                            .clickable(enabled = canCreate) { create() }
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Tạo",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (canCreate) Color.Black else Color(0xFF8A8A8A)
                        )
                    }
                }
            }
        }
        if (showPrivacy) {
            PrivacySheet(
                current = privacy,
                onPick = { privacy = it; showPrivacy = false },
                onDismiss = { showPrivacy = false }
            )
        }
    }
}

/** Bảng "Đặt chế độ hiển thị" (Công khai / Không công khai / Riêng tư) mở từ hộp thoại tạo danh sách. */
@Composable
private fun PrivacySheet(current: PrivacyChoice, onPick: (PrivacyChoice) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(SheetScrim).plainClickable(onDismiss)) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(NanColors.Popup)
                .plainClickable { }
                .navigationBarsPadding()
        ) {
            SheetHandle()
            Text(
                "Đặt chế độ hiển thị",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
            HorizontalDivider(color = NanColors.Divider)
            PrivacyChoice.entries.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(option) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(option.icon, null, Modifier.size(24.dp), tint = Color.White)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(option.label, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White)
                        Text(option.hint, fontSize = 14.sp, color = NanColors.TextSecondary)
                    }
                    if (option == current) Icon(Lucide.Check, null, Modifier.size(24.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TvCodeDialog(video: VideoItem, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var code by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ready = !sending && code.length == 12
    fun send() {
        if (!ready || sending) return
        sending = true
        error = null
        editScope.launch {
            try {
                TvPlayback.play(code, video.id)
                showToast(context, "Đã gửi lệnh phát tới TV")
                onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Không phát được trên TV. Kiểm tra mã TV và kết nối mạng rồi thử lại."
            } finally {
                sending = false
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        containerColor = NanColors.Popup,
        title = { Text("Phát trên thiết bị khác") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Trên YouTube của TV, mở Cài đặt → Liên kết bằng mã TV.")
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { ch -> ch in '0'..'9' }.take(12) },
                    label = { Text("Mã TV") },
                    singleLine = true,
                    enabled = !sending,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { send() })
                )
                error?.let { Text(it, color = NanColors.TextSoft) }
            }
        },
        confirmButton = { TextButton(enabled = ready, onClick = { send() }) { Text(if (sending) "Đang kết nối…" else "Phát") } },
        dismissButton = { TextButton(enabled = !sending, onClick = onDismiss) { Text("Hủy") } }
    )
}
