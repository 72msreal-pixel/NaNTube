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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

fun musicFeed() = FeedState<MusicShelf> { account, query ->
    if (query.isBlank()) MusicRepository.home(account) else MusicRepository.search(query, account)
}

@Composable
fun MusicScreen(
    state: FeedState<MusicShelf>,
    account: Account?,
    query: String,
    onSaveToPlaylist: (VideoItem) -> Unit,
    onQueryChange: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var stack by remember { mutableStateOf(emptyList<MusicItem>()) }
    val accountKey = (account?.id ?: "") + "|"
    LaunchedEffect(accountKey) {
        if (state.loadedKey?.startsWith(accountKey) != true) state.load(account)
    }

    val opened = stack.lastOrNull()
    if (opened != null) {
        MusicDetail(opened, account, onSaveToPlaylist, onOpen = { stack = stack + it }, onBack = { stack = stack.dropLast(1) })
        return
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SearchBox(query, "Tìm bài hát, nghệ sĩ", onQueryChange) {
            scope.launch { state.load(account, query.trim()) }
        }
        FeedBody(state, onRetry = { scope.launch { state.load(account, query.trim()) } }) {
            ShelfList(state.items, account, onSaveToPlaylist, onOpen = { stack = stack + it })
        }
    }
}

@Composable
private fun MusicDetail(
    item: MusicItem,
    account: Account?,
    onSaveToPlaylist: (VideoItem) -> Unit,
    onOpen: (MusicItem) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val state = remember(item) { FeedState<MusicShelf> { acc, _ -> MusicRepository.open(item, acc) } }
    LaunchedEffect(item) { state.load(account) }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            HistoryIcon(onClick = onBack) { Icon(Lucide.ArrowLeft, null, Modifier.size(24.dp)) }
            Text(item.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FeedBody(state, onRetry = { scope.launch { state.load(account) } }) {
            ShelfList(state.items, account, onSaveToPlaylist, onOpen)
        }
    }
}

@Composable
private fun ShelfList(
    shelves: List<MusicShelf>,
    account: Account?,
    onSaveToPlaylist: (VideoItem) -> Unit,
    onOpen: (MusicItem) -> Unit
) {
    val context = LocalContext.current
    val tap: (MusicItem) -> Unit = { item -> item.watchUrl?.let { openLink(context, it) } ?: onOpen(item) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(shelves) { shelf ->
            shelf.title?.let {
                Text(it, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp))
            }
            if (shelf.isList) {
                shelf.items.forEach { SongRow(it, account, onSaveToPlaylist) { item -> tap(item) } }
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shelf.items) { MusicCard(it) { item -> tap(item) } }
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
