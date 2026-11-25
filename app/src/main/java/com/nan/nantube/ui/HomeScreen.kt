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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.nan.nantube.data.ChannelAvatars
import com.nan.nantube.data.HOME_CHIPS
import com.nan.nantube.data.MusicDetector
import com.nan.nantube.data.ShortDetector
import com.nan.nantube.data.VideoItem
import com.nan.nantube.data.VideoRepository

/** Nguồn dữ liệu cho trang chủ: chip 0 là trang chủ YouTube, các chip khác là tìm kiếm theo chủ đề. */
fun homeFeed() = PagedFeed<VideoItem>(
    first = { account, arg ->
        val chip = HOME_CHIPS.getOrNull(arg.toIntOrNull() ?: 0) ?: HOME_CHIPS[0]
        if (chip.query == null) VideoRepository.homePage(account)
        else VideoRepository.chipPage(account, chip.query, chip.params, chip.music)
    },
    more = { account, token -> VideoRepository.homeMore(account, token) },
    keyOf = { it.id },
    prepare = { ChannelAvatars.prefetchFor(it) }
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
    LaunchedEffect(account?.id, chip) { feed.load(account, chip.toString()) }
    // Nhận diện nhạc cho thẻ không có icon ♪ (như YouTube), từng đợt nhỏ khi danh sách thêm video.
    LaunchedEffect(feed.items.size, account?.id) {
        ShortDetector.account = account
        MusicDetector.classify({ feed.items }) { ids ->
            feed.update { list -> list.map { if (it.id in ids) it.copy(music = true) else it } }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        HomeTopBar(account, onSearch, onNotifications)
        ChipRow(HOME_CHIPS.map { it.label }, chip, onChip)
        PagedBody(feed, onRetry = { feed.load(account, chip.toString(), force = true) }) {
            LoadMoreEffect(feed.listState, feed.items.size) { feed.loadMore(account) }
            YtPullRefresh(feed.loading, onRefresh = { feed.load(account, chip.toString(), force = true, keep = true) }) {
                LazyColumn(Modifier.fillMaxSize(), state = feed.listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(feed.items, key = { it.id }) { VideoCard(it, account, onSaveToPlaylist) }
                    if (feed.loadingMore) item { LoadingFooter() }
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(account: Account?, onSearch: () -> Unit, onNotifications: () -> Unit) {
    var unseen by remember { mutableStateOf(0) }
    // Số đỏ trên chuông: số thông báo chưa xem của tài khoản đang chọn (khách thì không có).
    LaunchedEffect(account?.id) { unseen = NotificationRepository.unseenCount(account) }

    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo NaNTube (ảnh nền trong suốt, cùng ảnh với icon app) thay cho logo vẽ tay giống YouTube trước đây.
        Image(
            painterResource(R.drawable.nantube_logo),
            contentDescription = "NaNTube",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text("NaNTube", fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
        Spacer(Modifier.weight(1f))
        TopBarButton(CastIcon, "Kết nối với TV", tint = if (TvStore.paired != null) NanColors.Blue else Color.White, onClick = { TvScreen.show() })
        Box {
            TopBarButton(Lucide.Bell, "Thông báo") {
                unseen = 0
                onNotifications()
            }
            if (unseen > 0) {
                // Chấm đỏ như YouTube: hình tròn cố định 16dp (số 2 chữ số thì giãn thành viên thuốc), đè lên vai chuông.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 3.dp)
                        .height(14.dp)
                        .widthIn(min = 14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFCC0000))
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (unseen > 9) "9+" else unseen.toString(),
                        fontSize = 9.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }
        TopBarButton(Lucide.Search, "Tìm kiếm", onClick = onSearch)
    }
}

@Composable
private fun TopBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, Modifier.size(23.dp), tint = tint)
    }
}

/** Hàng chip ngang: chip đang chọn nền trắng chữ đen, còn lại nền xám. */
@Composable
fun ChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(labels) { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) Color(0xFFF1F1F1) else NanColors.Surface)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (on) Color.Black else Color.White,
                    maxLines = 1
                )
            }
        }
    }
}
