package com.nan.nantube.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.nan.nantube.data.Account
import com.nan.nantube.data.MusicDetector
import com.nan.nantube.data.ShortDetector
import com.nan.nantube.data.VideoItem
import com.nan.nantube.data.VideoRepository
import com.nan.nantube.data.publishedIn
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

fun openLink(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        // Máy không có app mở link thì bỏ qua.
    }
}

/**
 * Short mở ngay trong app (trình phát dọc), video thường vẫn mở ngoài như cũ.
 * [queue] là danh sách đang hiện (Xem sau, Video đã thích, lịch sử…): các Short của nó thành hàng đợi quanh Short được chọn
 * (như `queueShorts` / `queueHistoryShorts` của meetube) nên vuốt lên/xuống xem các Short khác của chính danh sách đó,
 * không chỉ một video rồi chuyển sang đề xuất.
 */
fun openVideo(context: android.content.Context, video: VideoItem, queue: List<VideoItem> = emptyList(), asMusic: Boolean = false) {
    // Thẻ TV của danh sách phát không đánh dấu Short dài (≤ 3 phút). Chạm vào khi chưa kịp phân loại thì hỏi nhanh ngay lúc này
    // (có hạn chờ) thay vì mở nhầm link ngoài. Hỏi hay hết hạn đều định tuyến đúng một lần ([routeVideo]).
    val cached = ShortDetector.applyCached(video)
    if (!cached.isShort && ShortDetector.needsCheck(cached)) {
        MainScope().launch {
            val found = withTimeoutOrNull(3_000L) { ShortDetector.check(listOf(cached.id)) }
            routeVideo(context, if (found?.get(cached.id) == true) cached.copy(isShort = true) else cached, queue, asMusic)
        }
        return
    }
    routeVideo(context, cached, queue, asMusic)
}

private fun routeVideo(context: android.content.Context, video: VideoItem, queue: List<VideoItem>, asMusic: Boolean) {
    // Nhạc chỉ phát trong trình phát nhạc khi người dùng chọn "Mở dạng nhạc"; còn lại mở như video thường.
    if (!video.isShort && asMusic) {
        val songs = queue.map(ShortDetector::applyCached).map(MusicDetector::applyCached)
            .filter { !it.isShort && it.isMusic && it.id.length == 11 }
            .toMutableList()
        if (songs.none { it.id == video.id }) songs.add(0, video)
        WatchViewer.close()
        MusicPlayer.playVideos(context, ShortDetector.account, songs, video, "Danh sách phát", local = false)
        return
    }
    // Video dài: xem ngay trong app (màn kiểu YouTube, xem [WatchScreen]); đang phát trực tiếp thì vẫn mở ngoài như cũ.
    if (!video.isShort) {
        if (video.live || video.id.length != 11) return openLink(context, video.watchUrl)
        WatchViewer.open(video)
        return
    }
    val shorts = LinkedHashMap<String, VideoItem>()
    for (v in queue.map(ShortDetector::applyCached)) if (v.isShort && v.id.length == 11 && (v.id == video.id || !shorts.containsKey(v.id))) shorts.putIfAbsent(v.id, v)
    shorts.putIfAbsent(video.id, video)
    val list = shorts.values.toList()
    ShortsViewer.open(list, list.indexOfFirst { it.id == video.id })
}

/**
 * Lưới Short 2 cột kiểu YouTube (ô dọc sát nhau, tiêu đề đè lên ảnh), dựng bằng từng hàng đôi của LazyColumn
 * để dùng chung trạng thái cuộn của [PagedFeed]. Dùng cho tab Shorts và kết quả tìm kiếm lọc Shorts.
 */
fun LazyListScope.shortRows(videos: List<VideoItem>, account: Account?, onSaveToPlaylist: (VideoItem) -> Unit) {
    items(videos.shortPairs(), key = { it.first().id }) { pair ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pair.forEach { ShortCard(it, account, onSaveToPlaylist, Modifier.weight(1f)) }
        }
    }
}

/** Ghép Short thành từng cặp: Short lẻ ở cuối chưa có đôi thì chưa hiện (tải thêm trang là có đôi), nên hàng nào cũng đủ 2. */
fun List<VideoItem>.shortPairs(): List<List<VideoItem>> = windowed(2, 2)

@Composable
fun SearchBox(query: String, placeholder: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    val pill = RoundedCornerShape(50)
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(44.dp)
            .clip(pill)
            .border(1.dp, NanColors.Divider, pill)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Lucide.Search, null, Modifier.size(20.dp), tint = NanColors.TextSecondary)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(placeholder, fontSize = 16.sp, color = NanColors.TextSecondary)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun StatusBody(loading: Boolean, hasItems: Boolean, error: String?, onRetry: () -> Unit, list: @Composable () -> Unit) {
    when {
        loading && !hasItems -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
        }
        // Không còn báo lỗi / nút "Thử lại": lỗi mạng thì feed tự tải lại liên tục (vòng xoay như bên Short). Chỉ còn câu
        // "không có gì" khi YouTube trả về danh sách rỗng thật.
        error != null && !hasItems -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(error, color = NanColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        else -> list()
    }
}

/** Kênh · lượt xem · thời gian đăng · công chiếu (như MediaItemImpl.secondTitleItem); dòng lượt xem của thẻ TV đã kèm sẵn thời gian thì không thêm lần nữa. */
private fun subtitle(video: VideoItem): String =
    listOfNotNull(video.channel, video.views, video.published.takeIf { publishedIn(video.views) == null }, video.upcoming)
        .joinToString(" · ")

/** Thẻ video lớn kiểu trang chủ YouTube: ảnh 16:9, avatar kênh, tiêu đề 2 dòng, ♪ cho video nhạc. */
@Composable
fun VideoCard(
    item: VideoItem,
    account: Account?,
    onSaveToPlaylist: (VideoItem) -> Unit,
    onHidden: ((VideoItem, Boolean) -> Unit)? = null
) {
    // Tuyển tập / danh sách phát / đĩa nhạc lẫn trong trang chủ: vẽ thẻ danh sách như ở Tìm kiếm.
    item.playlist?.let { PlaylistCard(it, account); return }
    val context = LocalContext.current
    // Bài đã được nhận diện là nhạc (xem MusicDetector) thì hiện ♪ ngay, kể cả khi thẻ YouTube không gắn icon.
    val video = MusicDetector.applyCached(item)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { openVideo(context, video) }
            .padding(top = 6.dp, bottom = 10.dp)
    ) {
        Box(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .aspectRatio(if (video.artUrl != null && !video.isShort) 1f else 16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(NanColors.Surface)
        ) {
            // Ảnh 720p của YouTube; video nào không có thì rơi về ảnh nhỏ để khỏi trống.
            var url by remember(video.id) { mutableStateOf(video.artUrl?.takeIf { !video.isShort } ?: if (video.isShort) video.coverThumbnail else video.hqThumbnail) }
            AsyncImage(
                model = url,
                onError = { if (url != video.coverThumbnail) url = video.coverThumbnail },
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
        }
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp)) {
            LiveAwareAvatar(video, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = subtitle(video)
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                        color = NanColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            VideoMoreButton(video, account, onSaveToPlaylist, onHidden = onHidden)
        }
    }
}

@Composable
fun ShortCard(video: VideoItem, account: Account?, onSaveToPlaylist: (VideoItem) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(8.dp))
            .background(NanColors.Surface)
            .clickable { openVideo(context, video) }
    ) {
        AsyncImage(
            model = video.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Text(
            video.title,
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                .padding(start = 12.dp, end = 12.dp, top = 32.dp, bottom = 10.dp)
        )
        VideoMoreButton(video, account, onSaveToPlaylist, Modifier.align(Alignment.TopEnd))
        WatchProgress(video.watchedProgress())
    }
}
