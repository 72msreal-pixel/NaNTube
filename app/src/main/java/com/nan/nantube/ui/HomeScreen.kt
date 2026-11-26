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

/** Số video giữa hai hàng Short ở trang chủ: 2 dòng Short (mỗi dòng 2 Short), 6 video, rồi lặp lại (như YouTube). */
private const val VIDEOS_PER_SHORT_ROW = 6

/** Số dòng Short liên tiếp (mỗi dòng 2 ô) trước mỗi nhóm video. */
private const val ROWS_PER_BLOCK = 2

/** Nguồn Short (có tiêu đề + ảnh bìa) cho các hàng Short xen giữa trang chủ. */
fun homeShortsFeed() = PagedFeed<VideoItem>(
    first = { _, _ -> VideoRepository.shortsSearchPage(ShortDetector.account) },
    more = { account, token -> VideoRepository.shortsMore(account, token) },
    keyOf = { it.id }
)

/** Trang chủ kiểu YouTube: logo + nút tìm kiếm, hàng chip chủ đề, danh sách thẻ video lớn cuộn vô hạn. */
@Composable
fun MainFeedScreen(
    feed: PagedFeed<VideoItem>,
    shortsFeed: PagedFeed<VideoItem>,
    account: Account?,
    chip: Int,
    onChip: (Int) -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onSaveToPlaylist: (VideoItem) -> Unit
) {
    LaunchedEffect(account?.id, chip) { feed.load(account, chip.toString()) }
    // Hàng Short chỉ xen vào trang chủ (chip đầu), các chip chủ đề giữ danh sách video thuần.
    val withShorts = chip == 0
    LaunchedEffect(account?.id, withShorts) { if (withShorts) shortsFeed.load(account) }
    // Nhận diện nhạc cho thẻ không có icon ♪ (như YouTube), từng đợt nhỏ khi danh sách thêm video.
    LaunchedEffect(feed.items.size, account?.id) {
        ShortDetector.account = account
        MusicDetector.classify({ feed.items }) { ids ->
            feed.update { list -> list.map { if (it.id in ids) it.copy(music = true) else it } }
        }
    }

    // Bài nhạc mà feed chỉ gắn ♪ không ghi thời lượng (Tìm kiếm thì có): lấy độ dài từ /player để thẻ hiện "♪ 3:45".
    LaunchedEffect(feed.items.size, account?.id) {
        ShortDetector.account = account
        MusicDetector.fillDurations({ feed.items }) { found ->
            feed.update { list -> list.map { v -> found[v.id]?.let { v.copy(duration = it) } ?: v } }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        HomeTopBar(account, onSearch, onNotifications)
        ChipRow(HOME_CHIPS.map { it.label }, chip, onChip)
        PagedBody(feed, onRetry = { feed.load(account, chip.toString(), force = true) }) {
            LoadMoreEffect(feed.listState, feed.items.size) {
                feed.loadMore(account)
                // Hết Short để xen thì nạp thêm, để hàng nào cũng đủ 2 ô.
                if (withShorts && shortsFeed.items.size / (2 * ROWS_PER_BLOCK) <= feed.items.size / VIDEOS_PER_SHORT_ROW + 1) shortsFeed.loadMore(account)
            }
            YtPullRefresh(feed.loading, onRefresh = { feed.load(account, chip.toString(), force = true, keep = true) }) {
                LazyColumn(Modifier.fillMaxSize(), state = feed.listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                    val onHidden: (VideoItem, Boolean) -> Unit = { v, wholeChannel ->
                        // "Không đề xuất kênh này" ẩn luôn các video khác của kênh đó đang hiện.
                        feed.update { list ->
                            list.filterNot { x ->
                                x.id == v.id || (wholeChannel && (
                                    (v.channelId != null && x.channelId == v.channelId) ||
                                        (v.channelId == null && v.channel != null && x.channel == v.channel)))
                            }
                        }
                    }
                    if (!withShorts) {
                        items(feed.items, key = { it.id }) { VideoCard(it, account, onSaveToPlaylist, onHidden = onHidden) }
                    } else {
                        // Bố cục: [2 dòng x 2 Short] [6 video] lặp lại. Short đã có trong video thì bỏ để không trùng.
                        val videoIds = feed.items.map { it.id }.toHashSet()
                        val pairs = shortsFeed.items.filter { it.id !in videoIds }.shortPairs()
                        val videos = feed.items
                        val blocks = maxOf((pairs.size + ROWS_PER_BLOCK - 1) / ROWS_PER_BLOCK, (videos.size + VIDEOS_PER_SHORT_ROW - 1) / VIDEOS_PER_SHORT_ROW)
                        for (b in 0 until blocks) {
                            for (r in 0 until ROWS_PER_BLOCK) {
                                val pair = pairs.getOrNull(b * ROWS_PER_BLOCK + r) ?: continue
                                item(key = "short-row-" + pair.first().id) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        pair.forEach { ShortCard(it, account, onSaveToPlaylist, Modifier.weight(1f)) }
                                    }
                                }
                            }
                            val from = b * VIDEOS_PER_SHORT_ROW
                            if (from < videos.size) {
                                items(videos.subList(from, minOf(from + VIDEOS_PER_SHORT_ROW, videos.size)), key = { it.id }) {
                                    VideoCard(it, account, onSaveToPlaylist, onHidden = onHidden)
                                }
                            }
                        }
                    }
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(labels) { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) Color(0xFFF1F1F1) else NanColors.Surface)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp),
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
