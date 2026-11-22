package com.nan.nantube.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.nan.nantube.R
import com.nan.nantube.data.NotificationRepository
import com.nan.nantube.data.TvStore
import com.nan.nantube.data.Account
import com.nan.nantube.data.HOME_CHIPS
import com.nan.nantube.data.VideoItem
import com.nan.nantube.data.VideoRepository
import kotlinx.coroutines.launch

/** Nguồn dữ liệu cho trang chủ: chip 0 là trang chủ YouTube, các chip khác là tìm kiếm theo chủ đề. */
fun homeFeed() = PagedFeed<VideoItem>(
    first = { account, arg ->
        val chip = HOME_CHIPS.getOrNull(arg.toIntOrNull() ?: 0) ?: HOME_CHIPS[0]
        if (chip.query == null) VideoRepository.homePage(account)
        else VideoRepository.chipPage(account, chip.query, chip.params, chip.music)
    },
    more = { account, token -> VideoRepository.homeMore(account, token) },
    keyOf = { it.id }
)

/** Trang chủ kiểu YouTube: logo + nút tìm kiếm, hàng chip chủ đề, danh sách thẻ video lớn cuộn vô hạn. */
@Composable
fun MainFeedScreen(
    feed: PagedFeed<VideoItem>,
    account: Account?,
    chip: Int,
    onChip: (Int) -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onSaveToPlaylist: (VideoItem) -> Unit
) {
    val scope = rememberCoroutineScope()
    val key = (account?.id ?: "") + "|" + chip
    LaunchedEffect(key) { if (feed.loadedKey != key) feed.load(account, chip.toString()) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        HomeTopBar(account, onSearch, onNotifications)
        ChipRow(HOME_CHIPS.map { it.label }, chip, onChip)
        PagedBody(feed, onRetry = { scope.launch { feed.load(account, chip.toString()) } }) {
            val listState = rememberLazyListState()
            LoadMoreEffect(listState, feed.items.size) { feed.loadMore(account) }
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                items(feed.items, key = { it.id }) { VideoCard(it, account, onSaveToPlaylist) }
                if (feed.loadingMore) item { LoadingFooter() }
            }
        }
    }
}

@Composable
private fun HomeTopBar(account: Account?, onSearch: () -> Unit, onNotifications: () -> Unit) {
    var showTv by remember { mutableStateOf(false) }
    var unseen by remember { mutableStateOf(0) }
    // Số đỏ trên chuông: số thông báo chưa xem của tài khoản đang chọn (khách thì không có).
    LaunchedEffect(account?.id) { unseen = NotificationRepository.unseenCount(account) }

    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo NaNTube (ảnh nền trong suốt, cùng ảnh với icon app) thay cho logo vẽ tay giống YouTube trước đây.
        Image(
            painterResource(R.drawable.nantube_logo),
            contentDescription = "NaNTube",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text("NaNTube", fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
        Spacer(Modifier.weight(1f))
        TopBarButton(CastIcon, "Kết nối với TV", tint = if (TvStore.paired != null) NanColors.Blue else Color.White) { showTv = true }
        Box {
            TopBarButton(Lucide.Bell, "Thông báo") {
                unseen = 0
                onNotifications()
            }
            if (unseen > 0) {
                Text(
                    if (unseen > 9) "9+" else unseen.toString(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFCC0000))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        TopBarButton(Lucide.Search, "Tìm kiếm", onClick = onSearch)
    }
    if (showTv) TvConnectDialog { showTv = false }
}

@Composable
private fun TopBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, Modifier.size(24.dp), tint = tint)
    }
}

/** Hàng chip ngang: chip đang chọn nền trắng chữ đen, còn lại nền xám. */
@Composable
fun ChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(labels) { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) Color(0xFFF1F1F1) else NanColors.Surface)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (on) Color.Black else Color.White,
                    maxLines = 1
                )
            }
        }
    }
}
