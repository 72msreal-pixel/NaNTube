package com.nan.nantube.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import com.composables.icons.lucide.Check
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.nan.nantube.data.Account
import com.nan.nantube.data.VideoItem
import com.nan.nantube.data.VideoRepository

private enum class LocalKind { All, Video, Shorts }

/** Một chip lọc: có [token] thì lọc ở phía YouTube (Âm nhạc…), không thì lọc ngay trên danh sách đã tải. */
private class HistoryTab(val label: String, val token: String? = null, val kind: LocalKind = LocalKind.All)

private sealed interface HistoryRow {
    val key: String

    class Header(val title: String, index: Int) : HistoryRow {
        override val key = "h$index"
    }

    class Video(val item: VideoItem) : HistoryRow {
        override val key = "v${item.id}"
    }
}

/**
 * Trang "Video đã xem" đầy đủ như YouTube: tìm trong lịch sử, chip lọc, nhóm theo ngày,
 * vuốt tới cuối thì tải trang kế (continuation) cho tới hết.
 */
@Composable
fun HistoryScreen(base: HistoryFeed, account: Account?, onSaveToPlaylist: (VideoItem) -> Unit, onBack: () -> Unit) {
    var searching by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf("Tất cả") }
    val focus = LocalFocusManager.current

    // Chip: "Tất cả" + chip do YouTube trả về; nếu YouTube không trả chip nào thì lọc Video/Shorts tại chỗ.
    val tabs = remember(base.chips) {
        buildList {
            add(HistoryTab("Tất cả"))
            if (base.chips.isNotEmpty()) {
                base.chips.forEach { add(HistoryTab(it.label, token = it.token)) }
            } else {
                add(HistoryTab("Video", kind = LocalKind.Video))
                add(HistoryTab("Shorts", kind = LocalKind.Shorts))
            }
        }
    }
    val tab = tabs.firstOrNull { it.label == selected } ?: tabs.first()

    // Nguồn dữ liệu: tìm kiếm > chip phía YouTube > danh sách gốc dùng chung với hàng ở Info.
    val chipToken = tab.token
    val feed: HistoryFeed = when {
        applied.isNotBlank() -> remember(applied) { HistoryFeed { acc -> VideoRepository.historyPage(acc, applied) } }
        chipToken != null -> remember(chipToken) { HistoryFeed { acc -> VideoRepository.continuePage(acc, chipToken) } }
        else -> base
    }
    LaunchedEffect(feed, account?.id) {
        if (feed !== base || base.loadedKey == null) feed.refresh(account)
    }
    // Short mà YouTube không gắn nhãn (Short dài...) được nhận ra để chip Shorts đúng và chạm vào mở trình phát dọc.
    LaunchedEffect(feed, feed.items.size) { feed.classifyShorts() }
    LaunchedEffect(feed, feed.items.size) { feed.classifyMusic() }

    val visible = remember(feed.items, tab.kind) {
        feed.items.filter {
            when (tab.kind) {
                LocalKind.All -> true
                LocalKind.Video -> !it.isShort && !it.music
                LocalKind.Shorts -> it.isShort
            }
        }
    }
    val rows = remember(visible) {
        val out = ArrayList<HistoryRow>()
        var prev: String? = null
        visible.forEach { v ->
            if (v.section != null && v.section != prev) {
                out.add(HistoryRow.Header(v.section, out.size))
                prev = v.section
            }
            out.add(HistoryRow.Video(v))
        }
        out
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
    LaunchedEffect(nearEnd, feed, feed.items.size, feed.hasMore, feed.loading, feed.loadingMore, feed.moreFailed) {
        if (nearEnd && feed.hasMore && !feed.loading && !feed.loadingMore) {
            if (feed.moreFailed) kotlinx.coroutines.delay(MORE_RETRY_MS)
            // loadMore tự bật loadingMore (là key của effect này): không để việc đó huỷ chính lượt tải đang chạy.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { feed.loadMore(account) }
        }
    }

    fun closeSearch() {
        searching = false
        typed = ""
        applied = ""
        selected = "Tất cả"
        focus.clearFocus()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HistoryIcon(onClick = { if (searching) closeSearch() else onBack() }) {
                Icon(Lucide.ArrowLeft, null, Modifier.size(24.dp))
            }
            Spacer(Modifier.weight(1f))
            HistoryIcon(onClick = { searching = true }) {
                Icon(Lucide.Search, null, Modifier.size(24.dp))
            }
            HistoryIcon(onClick = { }) { VerticalDots() }
        }
        Text(
            "Video đã xem",
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
        )
        if (searching) {
            val requester = remember { FocusRequester() }
            LaunchedEffect(Unit) { requester.requestFocus() }
            Row(
                Modifier.fillMaxWidth().background(NanColors.Surface),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Lucide.Search, null,
                    Modifier.padding(start = 16.dp).size(22.dp),
                    tint = NanColors.TextSecondary
                )
                Box(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 14.dp)) {
                    if (typed.isEmpty()) {
                        Text("Tìm kiếm trong danh sách video đã xem", fontSize = 16.sp, color = NanColors.TextSecondary, maxLines = 1)
                    }
                    BasicTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            applied = typed.trim()
                            selected = "Tất cả"
                            focus.clearFocus()
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(requester)
                    )
                }
                Text(
                    "Hủy",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { closeSearch() }
                        .background(Color(0xFF3A3A3A))
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tabs, key = { it.label }) { t ->
                val on = t.label == tab.label
                // Chip như YouTube: cao 32dp, chữ 14sp, đệm ngang 12dp, bo 8dp.
                Box(
                    Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) Color(0xFFF1F1F1) else NanColors.Surface)
                        .clickable { selected = t.label }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        t.label,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (on) Color.Black else Color.White,
                        maxLines = 1
                    )
                }
            }
        }

        when {
            feed.loading && feed.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
            }
            rows.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        account == null -> "Đăng nhập để xem lịch sử video."
                        feed.error != null -> feed.error.orEmpty()
                        feed.loading || feed.loadingMore || feed.classifying -> "Đang tải…"
                        applied.isNotBlank() -> "Không tìm thấy video nào."
                        else -> "Chưa có video nào."
                    },
                    color = NanColors.TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                itemsIndexed(rows, key = { _, r -> r.key }) { _, row ->
                    when (row) {
                        is HistoryRow.Header -> Text(
                            row.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
                        )
                        is HistoryRow.Video -> HistoryVideoRow(row.item, account, onSaveToPlaylist, queue = visible, onRemoved = {
                            base.remove(account, it.id)
                            if (feed !== base) feed.remove(account, it.id)
                        })
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
    }
}

@Composable
fun HistoryIcon(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Ba chấm dọc (không phụ thuộc bộ icon). */
@Composable
fun VerticalDots() {
    Canvas(Modifier.size(width = 4.dp, height = 16.dp)) {
        val r = size.width / 2f
        for (y in listOf(r, size.height / 2f, size.height - r)) {
            drawCircle(Color.White, r, Offset(r, y))
        }
    }
}

@Composable
fun HistoryVideoRow(
    video: VideoItem,
    account: Account?,
    onSaveToPlaylist: (VideoItem) -> Unit,
    displayOnly: Boolean = false,
    onRemoved: ((VideoItem) -> Unit)? = null,
    queue: List<VideoItem> = emptyList(),
    onClick: (() -> Unit)? = null,
    removeFromList: RemoveFromList? = null,
    onDeleteDownload: (suspend (VideoItem) -> Unit)? = null,
    selectMode: Boolean = false,
    selected: Boolean = false
) {
    val context = LocalContext.current
    val click = when {
        onClick != null -> Modifier.clickable { onClick() }
        displayOnly -> Modifier
        else -> Modifier.clickable { openVideo(context, video, queue) }
    }
    Row(
        Modifier.fillMaxWidth().then(click)
            .background(if (selectMode && selected) Color(0x26FFFFFF) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            Modifier
                .width(180.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(NanColors.Surface)
        ) {
            AsyncImage(
                model = video.coverThumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (video.isShort) {
                ShortsBadge()
            } else {
                DurationBadge(video.duration, video.isMusic, video.live)
            }
            WatchProgress(video.watchedProgress())
            if (selectMode) SelectDot(selected, Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            // Như dòng video của YouTube: tiêu đề 14sp (dòng 20dp), kênh / lượt xem 12sp (dòng 18dp).
            Text(
                video.title,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            video.channel?.let {
                Text(it, fontSize = 12.sp, lineHeight = 18.sp, color = NanColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            video.views?.let {
                Text(it, fontSize = 12.sp, lineHeight = 18.sp, color = NanColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selectMode) Unit
        else if (!displayOnly) VideoMoreButton(video, account, onSaveToPlaylist, onRemoved = onRemoved, removeFromList = removeFromList)
        else if (onDeleteDownload != null) DownloadedMoreButton(video, onDeleteDownload)
    }
}

/** Vòng chọn ở góc ảnh bìa khi chọn nhiều video: rỗng = chưa chọn, trắng có dấu tích = đã chọn. */
@Composable
fun SelectDot(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) Color.White else Color(0x66000000))
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Lucide.Check, null, Modifier.size(16.dp), tint = Color.Black)
    }
}
