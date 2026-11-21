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
import com.nan.nantube.data.VideoItem


/** Một mục thư viện được mở ra: [key] là LibraryCovers.LIST_*. */
data class OpenList(val key: String, val title: String)

private enum class ListKind { All, Video, Shorts }

/**
 * Trang một danh sách (Video đã thích, Xem sau, Video của bạn) theo kiểu YouTube: nền mờ từ ảnh bìa,
 * ảnh bìa = video đầu, tên, chủ sở hữu, nút Phát tất cả, chip Tất cả/Video/Shorts, danh sách tải tiếp khi vuốt.
 */
@Composable
fun PlaylistScreen(list: OpenList, account: Account?, onSaveToPlaylist: (VideoItem) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val feed = remember(list.key, account?.id) {
        HistoryFeed { acc -> LibraryCovers.page(list.key, acc) }
    }
    LaunchedEffect(feed) { feed.refresh(account) }

    var kind by remember { mutableStateOf(ListKind.All) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val shown = remember(feed.items, kind, query) {
        val q = query.trim()
        feed.items.filter {
            when (kind) {
                ListKind.All -> true
                ListKind.Video -> !it.isShort
                ListKind.Shorts -> it.isShort
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
    // Lọc làm danh sách ngắn thì tự lấy tiếp trang kế cho đủ màn hình.
    LaunchedEffect(nearEnd, feed.items.size, shown.size, feed.hasMore, feed.loading) {
        if (nearEnd && feed.hasMore && !feed.loading) feed.loadMore(account)
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
                HistoryIcon(onClick = { }) { VerticalDots() }
            }
            if (searching) {
                val requester = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(Unit) { requester.requestFocus() }
                Box(Modifier.fillMaxWidth().background(NanColors.Surface).padding(horizontal = 16.dp, vertical = 14.dp)) {
                    if (query.isEmpty()) Text("Tìm trong danh sách", fontSize = 16.sp, color = NanColors.TextSecondary)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().then(Modifier.androidxFocus(requester))
                    )
                }
            }

            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
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
                        if (account != null) {
                            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(account.name.take(1).uppercase(), 28.dp, photoUrl = account.photo)
                                Text("của ${account.name}", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        val meta = listOfNotNull(
                            "Danh sách phát",
                            "Riêng tư".takeIf { list.key != LibraryCovers.LIST_UPLOADS },
                            feed.total
                        ).joinToString(" • ")
                        Text(meta, fontSize = 14.sp, color = NanColors.TextSoft, modifier = Modifier.padding(top = 10.dp))
                        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFFF1F1F1))
                                    .clickable(enabled = first != null) { first?.let { openVideo(context, it) } },
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
                                when (k) { ListKind.All -> "Tất cả"; ListKind.Video -> "Video"; ListKind.Shorts -> "Shorts" },
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
                                feed.hasMore || feed.loadingMore -> "Đang tải…"
                                else -> "Không có video nào."
                            },
                            color = NanColors.TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp)
                        )
                    }
                    else -> items(shown, key = { it.id }) { HistoryVideoRow(it, account, onSaveToPlaylist) }
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
    }
}

private fun Modifier.androidxFocus(requester: androidx.compose.ui.focus.FocusRequester): Modifier =
    this.then(Modifier.focusRequester(requester))
