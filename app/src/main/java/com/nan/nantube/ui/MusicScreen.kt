package com.nan.nantube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.nan.nantube.data.Account
import com.nan.nantube.data.MusicItem
import com.nan.nantube.data.MusicRepository
import com.nan.nantube.data.MusicShelf
import com.nan.nantube.data.VideoItem

/** Trạng thái tab Music, giữ ở AppRoot: ô tìm, chồng màn chi tiết (album, nghệ sĩ…) và các feed đã tải. */
class MusicUi {
    var query by mutableStateOf("")
    /** Từ khóa đã bấm tìm; rỗng = trang chủ Music. */
    var submitted by mutableStateOf("")
    var stack by mutableStateOf(emptyList<MusicItem>())
        private set

    val home = singlePageFeed<MusicShelf> { account, q ->
        if (q.isBlank()) MusicRepository.home(account) else MusicRepository.search(q, account)
    }
    private val details = HashMap<MusicItem, PagedFeed<MusicShelf>>()

    fun detail(item: MusicItem) = details.getOrPut(item) {
        singlePageFeed<MusicShelf> { account, _ -> MusicRepository.open(item, account) }
    }

    fun open(item: MusicItem) { stack = stack + item }
    fun back() { stack = stack.dropLast(1) }
}

@Composable
fun MusicScreen(ui: MusicUi, account: Account?, onSaveToPlaylist: (VideoItem) -> Unit) {
    LaunchedEffect(account?.id, ui.submitted) { ui.home.load(account, ui.submitted) }

    val opened = ui.stack.lastOrNull()
    if (opened != null) {
        MusicDetail(ui.detail(opened), opened, account, onSaveToPlaylist, onOpen = ui::open, onBack = ui::back)
        return
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SearchBox(ui.query, "Tìm bài hát, nghệ sĩ", { ui.query = it }) { ui.submitted = ui.query.trim() }
        PagedBody(ui.home, onRetry = { ui.home.load(account, ui.submitted, force = true) }) {
            ShelfList(ui.home, account, onSaveToPlaylist, onOpen = ui::open)
        }
    }
}

@Composable
private fun MusicDetail(
    feed: PagedFeed<MusicShelf>,
    item: MusicItem,
    account: Account?,
    onSaveToPlaylist: (VideoItem) -> Unit,
    onOpen: (MusicItem) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(item, account?.id) { feed.load(account) }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            HistoryIcon(onClick = onBack) { Icon(Lucide.ArrowLeft, null, Modifier.size(24.dp)) }
            Text(item.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PagedBody(feed, onRetry = { feed.load(account, force = true) }) {
            ShelfList(feed, account, onSaveToPlaylist, onOpen)
        }
    }
}

@Composable
private fun ShelfList(
    feed: PagedFeed<MusicShelf>,
    account: Account?,
    onSaveToPlaylist: (VideoItem) -> Unit,
    onOpen: (MusicItem) -> Unit
) {
    val context = LocalContext.current
    // Bài hát: phát ngay trong app (trình phát nhạc), cả hàng nhạc cùng danh sách làm hàng đợi. Album / danh sách / nghệ sĩ: mở trang con.
    val tap: (MusicItem, List<MusicItem>, String?) -> Unit = { item, list, title ->
        if (item.videoId != null) MusicPlayer.playItems(context, account, list, item, title ?: "Nhạc") else onOpen(item)
    }
    LazyColumn(Modifier.fillMaxSize(), state = feed.listState, contentPadding = PaddingValues(bottom = 16.dp)) {
        items(feed.items) { shelf ->
            shelf.title?.let {
                Text(it, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp))
            }
            if (shelf.isList) {
                shelf.items.forEach { SongRow(it, account, onSaveToPlaylist) { item -> tap(item, shelf.items, shelf.title) } }
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shelf.items) { MusicCard(it) { item -> tap(item, shelf.items, shelf.title) } }
                }
            }
        }
    }
}

@Composable
private fun Cover(url: String?, size: Dp, corner: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(corner)).background(NanColors.Surface)) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SongRow(item: MusicItem, account: Account?, onSaveToPlaylist: (VideoItem) -> Unit, onClick: (MusicItem) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick(item) }.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(item.art, 52.dp, 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.subtitle?.let {
                Text(it, fontSize = 12.sp, color = NanColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item.asVideo()?.let { VideoMoreButton(it, account, onSaveToPlaylist) }
    }
}

@Composable
private fun MusicCard(item: MusicItem, onClick: (MusicItem) -> Unit) {
    Column(Modifier.width(140.dp).clickable { onClick(item) }) {
        Cover(item.art, 140.dp, 8.dp)
        Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        item.subtitle?.let {
            Text(it, fontSize = 12.sp, color = NanColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
