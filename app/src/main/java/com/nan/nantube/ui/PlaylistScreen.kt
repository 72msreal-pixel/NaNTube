package com.nan.nantube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.nan.nantube.data.Account
import com.nan.nantube.data.VideoDownloads
import com.nan.nantube.data.VideoItem


/** Một mục thư viện được mở ra: [key] là LibraryCovers.LIST_*. */
data class OpenList(val key: String, val title: String, val owner: String? = null) {
    /** Danh sách phát của người khác (mở từ kết quả tìm kiếm / trang chủ), không phải thư viện của tài khoản. */
    val isPublic: Boolean get() = key.startsWith(PUBLIC_PREFIX)
    val playlistId: String get() = key.removePrefix(PUBLIC_PREFIX).removePrefix("VL")

    companion object {
        const val PUBLIC_PREFIX = "pl:"
        fun of(id: String, title: String, owner: String?) = OpenList(PUBLIC_PREFIX + id, title, owner)
    }
}

/** Danh sách phát người khác đang mở toàn màn hình (vẽ ở AppRoot, đóng bằng Back hoặc đổi tab). */
object PlaylistViewer {
    var open by androidx.compose.runtime.mutableStateOf<OpenList?>(null)
}

private enum class ListKind { All, Video, Shorts, Music }

/**
 * Trang một danh sách (Video đã thích, Xem sau, Video của bạn) theo kiểu YouTube: nền mờ từ ảnh bìa,
 * ảnh bìa = video đầu, tên, chủ sở hữu, nút Phát tất cả, chip Tất cả/Video/Shorts, danh sách tải tiếp khi vuốt.
 */
@Composable
fun PlaylistScreen(list: OpenList, account: Account?, onSaveToPlaylist: (VideoItem) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val downloads = list.key == LibraryCovers.LIST_DOWNLOADS
    val listAccount = account.takeUnless { downloads }
    val feed = remember(list.key, listAccount?.id, if (downloads) account?.id else null, context) {
        HistoryFeed { acc ->
            if (downloads) VideoDownloads.page(context, account) else LibraryCovers.page(list.key, acc)
        }
    }
    LaunchedEffect(feed) {
        if (downloads) VideoDownloads.changes(context).collect { feed.refresh(null) }
        else feed.refresh(listAccount)
    }
    // Thẻ TV của danh sách phát không đánh dấu Short (kể cả Short dài tới 3 phút): tự nhận ra để chip Shorts đúng và
    // chạm vào mở trình phát dọc, vuốt xem các Short khác của danh sách.
    LaunchedEffect(feed, feed.items.size) { if (!downloads) feed.classifyShorts() }
    // Bài hát mà YouTube không gắn ♪ (vd kênh "... Official"): hỏi YouTube Music để vào đúng chip Âm nhạc, không bị tính là video.
    LaunchedEffect(feed, feed.items.size) { if (!downloads) feed.classifyMusic() }

    var kind by remember { mutableStateOf(ListKind.All) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var voiceOpen by remember { mutableStateOf(false) }
    var voiceTick by remember { mutableStateOf(0) }
    val shown = remember(feed.items, kind, query, downloads) {
        val q = query.trim()
        feed.items.filter {
            when (kind) {
                ListKind.All -> true
                // Trong "Nội dung đã tải", music = file âm thanh (M4A/MP3): không phải video.
                ListKind.Video -> !it.isShort && !it.music
                ListKind.Shorts -> it.isShort
                ListKind.Music -> it.isMusic
            } && (q.isEmpty() || it.title.contains(q, ignoreCase = true) || (it.channel?.contains(q, ignoreCase = true) == true))
        }
    }

    val listState = rememberLazyListState()
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    // Gần chạm đáy thì lấy trang kế. Theo dõi cả loadingMore/moreFailed: lượt tải trúng lúc đang có lượt khác (hoặc bị lỗi) thoát
    // im lặng, nên khi lượt đó xong phải tự hỏi lại, nếu không phải vuốt lên rồi xuống mới tải tiếp.
    LaunchedEffect(nearEnd, feed.items.size, shown.size, feed.hasMore, feed.loading, feed.loadingMore, feed.moreFailed) {
        if (nearEnd && feed.hasMore && !feed.loading && !feed.loadingMore) {
            if (feed.moreFailed) kotlinx.coroutines.delay(MORE_RETRY_MS)
            // loadMore tự bật loadingMore (là key của effect này): không để việc đó huỷ chính lượt tải đang chạy.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { feed.loadMore(account) }
        }
    }

    val first = feed.items.firstOrNull()
    Box(Modifier.fillMaxSize()) {
        // Nền mờ theo ảnh bìa, cuộn đi cùng phần đầu trang.
        if (first != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(520.dp)
                    .graphicsLayer {
                        translationY = if (listState.firstVisibleItemIndex == 0) -listState.firstVisibleItemScrollOffset.toFloat() else -100000f
                    }
            ) {
                AsyncImage(
                    model = first.coverThumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(48.dp).alpha(0.6f)
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0x33000000), NanColors.Background))
                    )
                )
            }
        }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HistoryIcon(onClick = { if (searching) { searching = false; query = "" } else onBack() }) {
                    Icon(Lucide.ArrowLeft, null, Modifier.size(24.dp))
                }
                Spacer(Modifier.weight(1f))
                HistoryIcon(onClick = { searching = true }) { Icon(Lucide.Search, null, Modifier.size(24.dp)) }
                if (!downloads) HistoryIcon(onClick = { }) { VerticalDots() }
            }
            val focusManagerMic = androidx.compose.ui.platform.LocalFocusManager.current
            if (searching) {
                val requester = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(Unit) { requester.requestFocus() }
                // Mở lại thanh thì chữ cũ còn nguyên và con trỏ nằm cuối để gõ tiếp.
                var fieldValue by remember(voiceTick) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(query, androidx.compose.ui.text.TextRange(query.length))) }
                // Thanh tìm kiếm viên thuốc giống thanh ở tab Search: nền Surface, bo tròn, chữ 15sp, nút xóa chữ.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Row(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(NanColors.Surface)
                        .padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Tìm trong danh sách", fontSize = 15.sp, color = NanColors.TextSecondary)
                        BasicTextField(
                            value = fieldValue,
                            onValueChange = { fieldValue = it; query = it.text },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            modifier = Modifier.fillMaxWidth().then(Modifier.androidxFocus(requester))
                        )
                    }
                    if (query.isNotEmpty()) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable {
                                query = ""
                                fieldValue = androidx.compose.ui.text.input.TextFieldValue("")
                                try { requester.requestFocus() } catch (_: Exception) {}
                            },
                            contentAlignment = Alignment.Center
                        ) { Icon(IconClose, "Xóa chữ", Modifier.size(18.dp)) }
                    }
                }
                Spacer(Modifier.width(10.dp))
                // Nút mic bên cạnh thanh, giống thanh tìm kiếm ở tab Search.
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(NanColors.Surface).clickable {
                        focusManagerMic.clearFocus()
                        voiceOpen = true
                    },
                    contentAlignment = Alignment.Center
                ) { Icon(IconMic, "Tìm bằng giọng nói", Modifier.size(20.dp)) }
                }
            }
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

            LazyColumn(
                // Chạm ra ngoài (vào danh sách) khi đang tìm: ẩn bàn phím và đóng thanh tìm kiếm (chữ đã gõ vẫn giữ).
                // Chỉ theo dõi, không chặn cuộn / bấm.
                Modifier.fillMaxSize().pointerInput(searching) {
                    if (!searching) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        focusManager.clearFocus()
                        searching = false // giữ nguyên chữ đã gõ; bấm kính lúp để mở lại gõ tiếp
                    }
                },
                state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp)).background(NanColors.Surface)
                        ) {
                            if (first != null) {
                                AsyncImage(
                                    model = first.coverThumbnail,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Text(list.title, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                        if (list.isPublic) {
                            list.owner?.let { Text("của $it", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp)) }
                        } else if (listAccount != null) {
                            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(listAccount.name.take(1).uppercase(), 28.dp, photoUrl = listAccount.photo)
                                Text("của ${listAccount.name}", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        val meta = listOfNotNull(
                            if (downloads) "Trên thiết bị này" else "Danh sách phát",
                            "Riêng tư".takeIf { !downloads && !list.isPublic && list.key != LibraryCovers.LIST_UPLOADS },
                            feed.total
                        ).joinToString(" • ")
                        Text(meta, fontSize = 14.sp, color = NanColors.TextSoft, modifier = Modifier.padding(top = 10.dp))
                        if (!downloads) Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFFF1F1F1))
                                    .clickable(enabled = first != null) { first?.let { openVideo(context, it, feed.items) } },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Lucide.Play, null, Modifier.size(22.dp), tint = Color.Black)
                                Text("  Phát tất cả", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                            Spacer(Modifier.width(12.dp))
                            Box(
                                Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF3F3F3F)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Lucide.Download, null, Modifier.size(24.dp)) }
                        }
                    }
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ListKind.entries.toList()) { k ->
                            val on = k == kind
                            Text(
                                when (k) { ListKind.All -> "Tất cả"; ListKind.Video -> "Video"; ListKind.Shorts -> "Shorts"; ListKind.Music -> "Âm nhạc" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (on) Color.Black else Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (on) Color(0xFFF1F1F1) else NanColors.Surface)
                                    .clickable { kind = k }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                when {
                    feed.loading && feed.items.isEmpty() -> item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                        }
                    }
                    shown.isEmpty() -> item {
                        Text(
                            when {
                                feed.error != null && feed.items.isEmpty() -> feed.error.orEmpty()
                                feed.loading || feed.loadingMore || feed.classifying -> "Đang tải…"
                                kind == ListKind.Shorts -> "Không có Short nào."
                                kind == ListKind.Music -> "Không có nhạc nào."
                                else -> "Không có video nào."
                            },
                            color = NanColors.TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp)
                        )
                    }
                    else -> items(shown, key = { "${it.id}|${it.music}" }) { row ->
                        // File âm thanh đã tải: chạm là phát bằng trình phát nhạc của app, cả các bài nhạc đã tải làm hàng đợi.
                        val play: (() -> Unit)? = if (downloads && row.music) {
                            { MusicPlayer.playVideos(context, account, shown.filter { it.music }, row, "Nhạc đã tải", local = true) }
                        } else null
                        HistoryVideoRow(row, listAccount, onSaveToPlaylist, displayOnly = downloads, queue = shown, onClick = play)
                    }
                }
                if (feed.loadingMore) {
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
        if (voiceOpen) {
            VoiceSearchOverlay(
                onResult = { said -> query = said; voiceTick++; searching = true; voiceOpen = false },
                onClose = { voiceOpen = false }
            )
        }
    }
}

private fun Modifier.androidxFocus(requester: androidx.compose.ui.focus.FocusRequester): Modifier =
    this.then(Modifier.focusRequester(requester))
