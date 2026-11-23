@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nan.nantube.ui

import android.content.Intent
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.ThumbsUp
import com.nan.nantube.R
import com.nan.nantube.data.Account
import com.nan.nantube.data.CommentItem
import com.nan.nantube.data.ChannelNotify
import com.nan.nantube.data.ChannelSubscriptions
import com.nan.nantube.data.CommentsRepository
import com.nan.nantube.data.SearchResult
import com.nan.nantube.data.ShortDescription
import com.nan.nantube.data.ShortInfo
import com.nan.nantube.data.ShortsMedia
import com.nan.nantube.data.LikeCounts
import com.nan.nantube.data.ShortsPlayback
import com.nan.nantube.data.ShortsSeen
import com.nan.nantube.data.VideoItem
import com.nan.nantube.data.VideoRepository
import com.nan.nantube.data.WatchHistory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Màn xem Short phủ toàn màn hình, mở từ bất kỳ Short nào ngoài tab Short (tìm kiếm, lịch sử, danh sách phát...). */
object ShortsViewer {
    var items by mutableStateOf<List<VideoItem>?>(null)
        private set
    var serial by mutableIntStateOf(0)
        private set

    /** Vị trí Short được chọn trong [items] (mở từ danh sách: các Short khác của danh sách nằm trước / sau nó, như hàng đợi của meetube). */
    var start by mutableIntStateOf(0)
        private set

    fun open(list: List<VideoItem>, startAt: Int = 0) {
        items = list
        start = startAt.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        serial++
    }

    fun close() {
        items = null
    }
}

/** Bảng mô tả Short đang mở: AppRoot đọc để ẩn thanh tab dưới. */
object ShortsSheet {
    var open by mutableStateOf(false)
}

/** Chiều cao bảng mô tả (tỉ lệ vùng Short): mở mặc định và mở rộng hết cỡ. */
private const val SHEET_PEEK = 0.62f
private const val SHEET_FULL = 0.92f

/** Vị trí đang xem của tab Short, giữ ở AppRoot để đổi tab rồi quay lại không mất chỗ. */
class ShortsTabState {
    var page by mutableIntStateOf(0)

    /** Tăng lên là tab Short tải lại danh sách mới (nút làm mới, hoặc quay lại app sau một lúc lâu). */
    var epoch by mutableIntStateOf(0)
    var loadedEpoch = 0

    /** Bộ danh sách mà [page] thuộc về: bộ mới về thì bắt đầu lại từ video đầu. */
    var pageVersion = -1

    fun pageFor(version: Int) = if (version == pageVersion) page else 0

    /** Làm mới: video đang xem vẫn phát, bộ mới về xong mới đổi sang. */
    fun refresh() {
        epoch++
    }
}

fun shortsFeed() = PagedFeed(
    // Bộ mới về thì nạp sẵn video đầu (link + khung hình đầu + số thích / bình luận) trước khi đổi sang: hiện ra là phát luôn.
    first = { account, _ -> VideoRepository.shortsPage(account).also { ShortsPool.preloadHead(account, it.items) } },
    more = { account, token -> VideoRepository.shortsMore(account, token) },
    keyOf = { it: VideoItem -> it.id }
)

/** Tab Short: mở vào là tự phát video đầu tiên, vuốt lên/xuống để xem video kế như YouTube. Nút làm mới lấy bộ Short mới. */
@Composable
fun ShortsFeedScreen(
    feed: PagedFeed<VideoItem>,
    state: ShortsTabState,
    account: Account?,
    onSearch: () -> Unit,
    onSaveToPlaylist: (VideoItem) -> Unit
) {
    LaunchedEffect(account?.id, state.epoch) {
        val force = state.epoch != state.loadedEpoch
        state.loadedEpoch = state.epoch
        feed.load(account, force = force, keep = force)
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        ShortsHeader(
            onBack = null,
            onSearch = onSearch,
            video = feed.items.getOrNull(state.pageFor(feed.version)),
            account = account,
            onSave = onSaveToPlaylist
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PagedBody(feed, onRetry = { feed.load(account, force = true) }) {
                YtPullRefresh(feed.loading, onRefresh = { state.refresh() }) {
                    // Bộ mới về (version đổi) thì dựng lại pager từ video đầu; trước đó video cũ vẫn phát.
                    key(feed.version) {
                        ShortsPager(
                            items = feed.items,
                            startPage = state.pageFor(feed.version),
                            account = account,
                            active = ShortsViewer.items == null,
                            bottomInset = false,
                            onPage = { state.page = it; state.pageVersion = feed.version },
                            onNearEnd = { feed.loadMore(account) },
                            onSave = onSaveToPlaylist
                        )
                    }
                }
            }
        }
    }
}

/** Bản phủ toàn màn hình của [ShortsViewer]: phát Short được chọn rồi nối tiếp Short đề xuất. */
@Composable
fun ShortsViewerOverlay(account: Account?, onSearch: () -> Unit, onSave: (VideoItem) -> Unit) {
    val initial = ShortsViewer.items ?: return
    val serial = ShortsViewer.serial
    key(serial) {
        var list by remember { mutableStateOf(initial) }
        var token by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        // Đã lấy trang đầu của feed Short đề xuất chưa (hàng đợi từ danh sách chỉ nối feed khi vuốt gần hết).
        var feedStarted by remember { mutableStateOf(false) }
        val startAt = ShortsViewer.start
        var page by remember { mutableIntStateOf(startAt) }
        val scope = rememberCoroutineScope()

        fun append(items: List<VideoItem>) {
            val seen = list.map { it.id }.toHashSet()
            list = list + items.filter { seen.add(it.id) }
        }

        LaunchedEffect(Unit) {
            // Còn ≥ 5 Short của danh sách phía sau thì chưa cần nối Short đề xuất.
            if (initial.size - startAt >= 5) return@LaunchedEffect
            feedStarted = true
            try {
                val first = VideoRepository.shortsPage(account)
                append(first.items)
                token = first.token
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Không có đề xuất thì chỉ xem video đã chọn.
            }
        }

        Column(Modifier.fillMaxSize().background(Color.Black).blockTouchThrough()) {
            ShortsHeader(
                onBack = { ShortsViewer.close() },
                onSearch = onSearch,
                video = list.getOrNull(page),
                account = account,
                onSave = onSave
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ShortsPager(
                    items = list,
                    startPage = startAt,
                    account = account,
                    active = true,
                    bottomInset = true,
                    onPage = { page = it },
                    onNearEnd = {
                        val t = token
                        if (t == null && !feedStarted && !busy) {
                            // Hết các Short của danh sách: nối Short đề xuất để vuốt tiếp không bị dừng.
                            feedStarted = true
                            busy = true
                            scope.launch {
                                try {
                                    val first = VideoRepository.shortsPage(account)
                                    append(first.items)
                                    token = first.token
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                } finally {
                                    busy = false
                                }
                            }
                        } else if (t != null && !busy) {
                            busy = true
                            scope.launch {
                                try {
                                    val next = VideoRepository.shortsMore(account, t)
                                    append(next.items)
                                    token = next.token?.takeIf { next.items.isNotEmpty() }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    onSave = onSave
                )
            }
        }
    }
}

/** Quá thời gian này mà chưa lên hình đầu tiên thì coi là link kẹt và đổi client. */
private const val FIRST_FRAME_TIMEOUT_MS = 9_000L

/** Chờ tối đa chừng này cho số thích / bình luận / kênh của một Short; quá hạn thì hiện luôn với nhãn mặc định (không để kẹt). */
private const val DETAILS_TIMEOUT_MS = 6_000L

/** Khoảng cách giữa các lần hỏi lại số thích / bình luận khi chưa lấy được (không giới hạn số lần, lấy được thì dừng). */
private const val DETAILS_POLL_MS = 1_000L

/** Số Short phía sau được tải sẵn thông tin để vuốt tới là hiện đủ ngay. */
private const val DETAILS_AHEAD = 3

/** Xem Short chừng này giây thì ghi vào lịch sử xem. */
private const val WATCH_MARK_DELAY_MS = 2_500L

/** Cập nhật lại thời gian xem định kỳ khi vẫn ở Short này (Short lặp lại cũng được tính). */
private const val WATCH_REFRESH_MS = 10_000L

/** Số lần tự đổi client + phát tiếp trước khi hiện nút "Thử lại". */
private const val MAX_AUTO_RECOVERY = 4

@Composable
private fun ShortsPager(
    items: List<VideoItem>,
    startPage: Int,
    account: Account?,
    active: Boolean,
    bottomInset: Boolean,
    onPage: (Int) -> Unit,
    onNearEnd: () -> Unit,
    onSave: (VideoItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = startPage.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    val page = pagerState.settledPage
    val latest by rememberUpdatedState(items)

    // Các ô phát (đang xem + kế + trước) nằm trong ShortsPool, sống ngoài pager để làm mới có thể nạp sẵn video đầu của bộ mới.
    remember(account?.id) { ShortsPool.bind(context, account) }
    DisposableEffect(Unit) { onDispose { ShortsPool.pagerGone() } }

    var userPaused by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var tick by remember { mutableIntStateOf(0) }
    val likeTexts = remember(account?.id) { mutableStateMapOf<String, String>() }
    var commentsFor by remember { mutableStateOf<VideoItem?>(null) }
    var notifyFor by remember { mutableStateOf<SearchResult.Channel?>(null) }
    var subBusy by remember { mutableStateOf(false) }
    val likeEffect = rememberTapEffect()
    val subEffect = rememberTapEffect()
    // Trạng thái tim đang hiện: luôn lấy lại từ YouTube mỗi lần Short này thành Short đang xem (không nhớ kết quả cũ), bấm tim thì đổi ngay.
    val liked = remember(account?.id) { mutableStateMapOf<String, Boolean>() }
    // Kênh + trạng thái đăng ký thật của từng Short (hỏi song song với việc lấy link, không chờ video tải xong).
    val infos = remember(account?.id) { mutableStateMapOf<String, ShortInfo>() }
    fun isLiked(id: String): Boolean = liked[id] ?: false
    // Số bình luận: `next` thường không kèm, nên hỏi trang bình luận (như khi mở bảng bình luận) và nhớ lại.
    val commentCounts = remember { mutableStateMapOf<String, String>() }
    // Short nào đã có đủ số thích + bình luận + kênh (hoặc đã quá hạn chờ): chỉ lúc đó mới hiện Short, nên chữ không nhảy "Thích" → "226 N".
    val settled = remember(account?.id) { mutableStateMapOf<String, Boolean>() }
    val settling = remember(account?.id) { HashSet<String>() }
    val pausedNow by rememberUpdatedState(userPaused)
    // Bảng mô tả khi bấm tiêu đề: chiều cao bảng đi theo ngón tay, video co giãn theo (như kéo trình phát YouTube).
    // Thả tay thì tự về mốc gần nhất: đóng / mở mặc định / mở hết cỡ.
    val sheet = remember { Animatable(0f) }
    var descOpen by remember { mutableStateOf(false) }
    var boxHeight by remember { mutableIntStateOf(0) }
    val sheetFraction = sheet.value

    fun openSheet() {
        descOpen = true
        scope.launch { sheet.animateTo(SHEET_PEEK, tween(240)) }
    }

    fun closeSheet() {
        scope.launch {
            sheet.animateTo(0f, tween(220))
            descOpen = false
        }
    }

    fun dragSheet(dyPx: Float) {
        if (boxHeight <= 0) return
        scope.launch { sheet.snapTo((sheet.value - dyPx / boxHeight).coerceIn(0f, SHEET_FULL)) }
    }

    /** [velocity] px/s, dương = đang kéo xuống. */
    fun settleSheet(velocity: Float) {
        val v = sheet.value
        val target = when {
            velocity > 1200f -> if (v > SHEET_PEEK + 0.05f) SHEET_PEEK else 0f
            velocity < -1200f -> if (v < SHEET_PEEK - 0.05f) SHEET_PEEK else SHEET_FULL
            v < SHEET_PEEK * 0.55f -> 0f
            v < (SHEET_PEEK + SHEET_FULL) / 2f -> SHEET_PEEK
            else -> SHEET_FULL
        }
        scope.launch {
            sheet.animateTo(target, tween(220))
            if (target == 0f) descOpen = false
        }
    }

    // Đổi Short / rời tab thì đóng bảng ngay.
    LaunchedEffect(page, active) {
        if (descOpen) {
            sheet.snapTo(0f)
            descOpen = false
        }
    }
    // Báo cho AppRoot ẩn thanh tab dưới khi bảng đang mở.
    LaunchedEffect(descOpen, active) { ShortsSheet.open = descOpen && active }
    DisposableEffect(Unit) { onDispose { ShortsSheet.open = false } }
    BackHandler(enabled = descOpen) { closeSheet() }

    val curVideo = items.getOrNull(page)
    val curSlot = curVideo?.let { ShortsPool.slot(it.id) }
    val curSettled = curVideo != null && settled[curVideo.id] == true
    // Đang vuốt sang Short khác: settledPage vẫn là trang cũ cho tới khi vuốt xong nên tiếng Short cũ còn vang thêm một lúc.
    // Cứ lệch khỏi trang đã dừng là dừng tiếng ngay; vuốt trả về chỗ cũ thì phát lại.
    val swiping by remember {
        androidx.compose.runtime.derivedStateOf {
            pagerState.currentPage != pagerState.settledPage ||
                (pagerState.isScrollInProgress && kotlin.math.abs(pagerState.currentPageOffsetFraction) > 0.02f)
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) ShortsPool.pauseAll()
            else if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Đổi trang (hoặc vào lại tab): nạp sẵn video đang xem + kế + trước. Ô kế / trước đã đứng ở khung đầu nên vuốt tới là phát luôn.
    LaunchedEffect(page, active, items.size, account?.id) {
        val list = latest
        if (!active) {
            ShortsPool.pauseAll()
            return@LaunchedEffect
        }
        onPage(page)
        if (page >= list.size - 4) onNearEnd()
        ShortsPool.window(listOfNotNull(list.getOrNull(page), list.getOrNull(page + 1), list.getOrNull(page - 1)))
        // Video thứ 2 phía sau: chỉ lấy link sẵn (không nạp trình phát) để tới lượt là có ngay.
        list.getOrNull(page + 2)?.let { ShortsPlayback.warm(context, account, it, bytes = false) }
    }
    LaunchedEffect(page) { userPaused = false }

    // Chỉ ô của video đang xem được phát, và chỉ khi đã có đủ số thích / bình luận (hoặc quá hạn chờ) để chữ không nhảy.
    LaunchedEffect(page, active, userPaused, curSettled, curSlot, tick, swiping) {
        ShortsPool.setActive(if (active) curVideo?.id else null, active && !userPaused && curSettled && !swiping)
    }

    // Điền kênh + số thích + số bình luận của Short: hỏi mỗi DETAILS_POLL_MS cho tới khi lấy được thì dừng (không giới hạn số lần).
    // Hỏi tới đâu hiện tới đó; chỉ hỏi lại phần còn thiếu.
    suspend fun fillDetails(video: VideoItem) {
        var firstRound = true
        while (true) {
            if (!firstRound) delay(DETAILS_POLL_MS)
            // Mô tả (lượt xem / ngày đăng / số liệu) hỏi song song ngay từ đầu để bảng mô tả mở ra là có sẵn, không nhảy số.
            val descAsk = if (firstRound) ShortsPlayback.description(video.id) else null
            val info = try {
                ShortsPlayback.info(context, account, video).await().also { infos[video.id] = it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                infos[video.id]
            }
            // `next` đã kèm số bình luận thì khỏi hỏi trang bình luận.
            info?.comments?.let { commentCounts[video.id] = it }
            if (commentCounts[video.id] == null) {
                try {
                    ShortsPlayback.commentCount(video.id).await()?.let { commentCounts[video.id] = it }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
            // Số thích: `next` của tài khoản web hay không kèm, nên lấy ô "Lượt thích" từ `next` ẩn danh (cùng nguồn với bảng mô tả).
            if (likeTexts[video.id] == null) {
                // Đã nhớ số thích của video này (kèm trạng thái đã đếm) thì dùng lại để khớp; chưa có mới lấy từ mạng.
                val likes = LikeCounts.raw(video.id) ?: info?.likes ?: try {
                    ShortsPlayback.description(video.id).await()?.likesText
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                likes?.let { likeTexts[video.id] = LikeCounts.remember(video.id, it) }
            }
            if (descAsk != null) {
                try {
                    descAsk.await()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
            if (firstRound) {
                // Xong vòng hỏi đầu tiên là cho hiện Short (có số nào hiện số đó), phần còn thiếu hỏi tiếp ở nền.
                firstRound = false
                settled[video.id] = true
                settling.remove(video.id)
            }
            if (likeTexts[video.id] != null && commentCounts[video.id] != null) return
        }
    }

    // Số thích hiện theo meetube: số YouTube trả về lệch 1 theo lần thích / bỏ thích của mình (110 -> 111), không hỏi lại mạng
    // (hỏi lại ngay sau khi bấm hay trả số cũ chưa kịp cập nhật làm số đứng yên).
    fun likeLabel(video: VideoItem): String? {
        val id = video.id
        if (likeTexts[id] != null) return LikeCounts.show(id, isLiked(id)) ?: likeTexts[id]
        return infos[id]?.likes
    }

    val fills = remember(account?.id) { HashMap<String, Job>() }
    LaunchedEffect(page, items.size, account?.id) {
        val wanted = (page - 1 until page + DETAILS_AHEAD).mapNotNull { latest.getOrNull(it)?.id }.toHashSet()
        // Video đã trôi xa thì thôi hỏi (không hỏi mãi cho video không còn ai xem).
        val gone = fills.keys.filter { it !in wanted }
        gone.forEach { fills.remove(it)?.cancel() }
        for (i in page until page + DETAILS_AHEAD) {
            val video = latest.getOrNull(i) ?: break
            if (fills.containsKey(video.id)) continue
            fills[video.id] = scope.launch {
                // Phòng khi vòng hỏi đầu tiên kẹt (mạng treo): vẫn cho hiện Short sau DETAILS_TIMEOUT_MS, số về sau sẽ tự cập nhật.
                launch {
                    delay(DETAILS_TIMEOUT_MS)
                    settled[video.id] = true
                    settling.remove(video.id)
                }
                fillDetails(video)
            }
        }
    }

    // Short đang xem đã lên hình là đã xem: lần mở app sau không đề xuất lại (ô nạp sẵn chưa tính).
    LaunchedEffect(curVideo?.id, curSlot?.frame) { if (curSlot?.frame == true) ShortsSeen.add(curVideo?.id) }

    // Mỗi lần Short này thành Short đang xem (đổi trang, về lại tab, mở lại app) hỏi YouTube trạng thái tim thật, như meetube đọc `next`.
    // Đang chờ kết quả hay vừa bấm tim thì không ghi đè lần bấm.
    LaunchedEffect(curVideo?.id, account?.id, active, tick) {
        val id = curVideo?.id
        if (active && id != null && account != null) {
            val clickedBefore = liked[id]
            val status = ShortsPlayback.likeStatus(account, id)
            if (status != null && liked[id] == clickedBefore) {
                liked[id] = status
                LikeCounts.noteState(id, status)
            }
        }
    }

    // Xem được vài giây là ghi vào "Video đã xem" của tài khoản (theo TrackingService của MediaServiceCore); trước đây không ghi gì cả.
    // Như VideoStateController.updateHistory của meetube: ghi khi đang xem (vị trí tối thiểu 3 giây), cập nhật định kỳ,
    // và ghi lại vị trí cuối khi rời Short (vuốt đi, về tab khác) nên chỉ xem một chút vẫn vào "Video đã xem".
    LaunchedEffect(curVideo?.id, curSlot?.frame, account?.id, active) {
        val id = curVideo?.id
        val slot = curSlot
        if (active && id != null && account != null && slot != null && slot.frame) {
            fun position(): Float = try { slot.player.currentPosition / 1000f } catch (_: Exception) { 0f }
            fun length(): Float? = try { slot.player.duration.takeIf { it > 0 }?.let { it / 1000f } } catch (_: Exception) { null }
            var lastPosition = 0f
            try {
                delay(WATCH_MARK_DELAY_MS)
                while (true) {
                    lastPosition = position()
                    WatchHistory.update(account, id, lastPosition, length())
                    delay(WATCH_REFRESH_MS)
                }
            } finally {
                // Rời Short (hoặc đóng trình xem) trước mốc đầu tiên cũng được ghi, giống meetube ghi khi dừng/chuyển video.
                // Ô phát có thể đã được dùng lại cho video khác (frame=false) thì chỉ tin vị trí đã đọc trước đó.
                val end = if (slot.frame) maxOf(lastPosition, position()) else lastPosition
                WatchHistory.update(account, id, end, length())
            }
        }
    }

    LaunchedEffect(curSlot, curSlot?.frame) {
        val slot = curSlot
        if (slot == null || !slot.frame) {
            progress = 0f
            return@LaunchedEffect
        }
        while (true) {
            val duration = slot.player.duration
            progress = if (duration > 0) (slot.player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
            delay(100)
        }
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun like(video: VideoItem) {
        if (account == null) return toast("Hãy đăng nhập để thích video")
        val now = !isLiked(video.id)
        // Chưa biết lúc đếm số thích tài khoản đã thích chưa thì coi là trạng thái trước lúc bấm.
        LikeCounts.noteState(video.id, !now)
        liked[video.id] = now
        likeEffect.play(burst = now)
        scope.launch {
            try {
                ShortsPlayback.like(account, video.id, now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                liked[video.id] = !now
                toast(e.message ?: "Không thích được video")
            }
        }
    }

    fun setSubscribed(channel: SearchResult.Channel, subscribe: Boolean) {
        if (account == null) return toast("Hãy đăng nhập để đăng ký kênh")
        if (subBusy) return
        subBusy = true
        scope.launch {
            try {
                ChannelSubscriptions.set(account, channel, subscribe)
                if (subscribe) subEffect.play()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.message ?: "Không đổi được trạng thái đăng ký")
            } finally {
                subBusy = false
            }
        }
    }

    fun share(video: VideoItem) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, video.watchUrl)
        try {
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { boxHeight = it.height }) {
        if (items.isNotEmpty()) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !descOpen,
                beyondViewportPageCount = 1,
                key = { index -> items.getOrNull(index)?.id ?: index }
            ) { index ->
                val video = items[index]
                val current = index == page
                val info = infos[video.id]
                val channel = info?.toChannel()
                ShortPage(
                    video = video,
                    current = current,
                    slot = ShortsPool.slot(video.id),
                    likes = likeLabel(video),
                    detailsReady = settled[video.id] == true,
                    sheetFraction = if (current) sheetFraction else 0f,
                    onTitle = { openSheet() },
                    paused = userPaused,
                    progress = progress,
                    bottomInset = bottomInset,
                    liked = isLiked(video.id),
                    likeEffect = if (current) likeEffect else null,
                    subEffect = if (current) subEffect else null,
                    account = account,
                    info = info,
                    commentCount = info?.comments ?: commentCounts[video.id],
                    channel = channel,
                    subscribed = channel != null && ChannelSubscriptions.isSubscribed(account, channel),
                    onTap = { userPaused = !userPaused },
                    onRetry = { ShortsPool.slot(video.id)?.let { ShortsPool.retry(it) } },
                    onLike = { like(video) },
                    onComments = { commentsFor = video },
                    onSave = { onSave(video) },
                    onShare = { share(video) },
                    onSubscribe = {
                        when {
                            channel == null -> toast("Đang tải thông tin kênh…")
                            account == null -> toast("Hãy đăng nhập để đăng ký kênh")
                            ChannelSubscriptions.isSubscribed(account, channel) -> notifyFor = channel
                            else -> setSubscribed(channel, true)
                        }
                    }
                )
            }
        }

        if (descOpen) {
            items.getOrNull(page)?.let { cur ->
                ShortDescriptionSheet(
                    video = cur,
                    info = infos[cur.id],
                    likesHint = likeLabel(cur),
                    fraction = sheetFraction,
                    // Thanh tab dưới đã ẩn khi mở bảng nên bảng tự chừa thanh điều hướng của hệ thống.
                    bottomInset = true,
                    onDrag = { dragSheet(it) },
                    onRelease = { settleSheet(it) },
                    onClose = { closeSheet() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        commentsFor?.let { CommentsSheet(it, onClose = { commentsFor = null }) }

        // Bấm "Đã đăng ký": bảng chọn nhận / không nhận thông báo hoặc hủy đăng ký (cùng bảng với thẻ kênh ở Tìm kiếm).
        notifyFor?.let { ch ->
            NotificationSheet(
                ch = ch,
                account = account,
                onDismiss = { notifyFor = null },
                onUnsubscribe = { notifyFor = null; setSubscribed(ch, false) }
            )
        }
    }
}

private val ShortShadow = TextStyle(shadow = Shadow(color = Color(0x80000000), offset = Offset(0f, 1f), blurRadius = 4f))
private val LikedRed = Color(0xFFFF3B47)

/** Thanh trên cùng đen như YouTube: (Back) ... Làm mới, Tìm kiếm, ⋯. Video nằm dưới thanh này nên không bị đè chữ. */
@Composable
private fun ShortsHeader(
    onBack: (() -> Unit)?,
    onSearch: () -> Unit,
    video: VideoItem?,
    account: Account?,
    onSave: (VideoItem) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding().height(48.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) TopIcon(IconArrowLeft, onBack)
        Spacer(Modifier.weight(1f))
        TopIcon(YtSearch, onSearch)
        video?.let { VideoMoreButton(it, account, onSave) }
    }
}

@Composable
private fun TopIcon(icon: ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).plainClickable(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(24.dp), tint = Color.White)
    }
}

@Composable
private fun ShortPage(
    video: VideoItem,
    current: Boolean,
    slot: ShortSlot?,
    likes: String?,
    detailsReady: Boolean,
    sheetFraction: Float,
    onTitle: () -> Unit,
    paused: Boolean,
    progress: Float,
    bottomInset: Boolean,
    liked: Boolean,
    account: Account?,
    info: ShortInfo?,
    commentCount: String?,
    channel: SearchResult.Channel?,
    subscribed: Boolean,
    onTap: () -> Unit,
    onRetry: () -> Unit,
    onLike: () -> Unit,
    onComments: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onSubscribe: () -> Unit,
    likeEffect: TapEffectState? = null,
    subEffect: TapEffectState? = null
) {
    val context = LocalContext.current
    val ready = slot?.frame == true
    val buffering = slot?.buffering == true
    val error = slot?.error
    val vertical = slot?.vertical ?: true
    // Ô đã nạp xong nhưng khung đầu chưa lên màn (nạp khi chưa có PlayerView): tua về chỗ cũ để ExoPlayer vẽ lại khung đầu lên bề mặt mới.
    LaunchedEffect(slot, slot?.ready) {
        if (slot != null && slot.ready && !slot.frame) {
            delay(400)
            if (!slot.frame) slot.player.seekTo(slot.player.currentPosition)
        }
    }
    val meta = ShortsPlayback.meta(video.id)
    val inset = if (bottomInset) Modifier.navigationBarsPadding() else Modifier
    val author = info?.author ?: meta?.author ?: video.channel
    val channelId = info?.channelId ?: meta?.channelId ?: video.channelId
    val title = info?.title ?: meta?.title ?: video.title
    // Nút Đăng ký luôn có: hiện ngay khi đang tải thông tin kênh, chỉ ẩn khi đã hỏi xong mà vẫn không biết kênh.
    val showSubscribe = info == null || channel != null

    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }) {
        // Bảng mô tả đang mở: video thu lại phần trên màn hình (phần còn lại là bảng).
        val videoArea = Modifier.fillMaxWidth().fillMaxHeight(1f - sheetFraction)
        AsyncImage(
            model = video.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = videoArea
        )
        // Cả trang bên cạnh cũng có PlayerView riêng gắn với ô của nó: kéo là thấy khung hình thật của video kế dưới ngón tay.
        if (slot != null) {
            val holder = remember { arrayOfNulls<PlayerView>(1) }
            DisposableEffect(Unit) { onDispose { holder[0]?.player = null } }
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.shorts_player_view, null) as PlayerView).also {
                        holder[0] = it
                        it.player = slot.player
                    }
                },
                update = {
                    it.player = slot.player
                    it.resizeMode = if (vertical && sheetFraction == 0f) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                modifier = videoArea
            )
        }

        // Chưa có đủ số thích / bình luận thì che bằng ảnh bìa, xong mới lộ video + chữ.
        if (!detailsReady) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(
                    model = video.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (sheetFraction == 0f) Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(230.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))))
        )

        if (detailsReady && sheetFraction == 0f && current && ready && paused) {
            Icon(Lucide.Play, null, Modifier.align(Alignment.Center).size(64.dp), tint = Color(0xCCFFFFFF))
        }
        if (sheetFraction == 0f && error == null && (!detailsReady || (current && (!ready || buffering) && !paused && slot != null))) {
            CircularProgressIndicator(
                color = Color.White, strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(36.dp)
            )
        }
        if (error != null) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Thử lại", Color(0xFFF1F1F1), Color.Black, onClick = onRetry)
                    PillButton("Mở trên YouTube", Color(0x33FFFFFF), Color.White, onClick = { openLink(context, video.watchUrl) })
                }
            }
        }

        // Cột nút bên phải như Shorts của YouTube: tim, bình luận, lưu, chia sẻ (có số khi YouTube trả về).
        if (detailsReady && sheetFraction == 0f) Column(
            Modifier.align(Alignment.BottomEnd).then(inset).padding(end = 4.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ShortAction(if (liked) YtHeartFilled else YtHeart, likes ?: "Thích", if (liked) LikedRed else Color.White, onLike, likeEffect, LikedRed, "like|${video.id}")
            ShortAction(YtComment, commentCount ?: "Bình luận", Color.White, onComments, countKey = "comment|${video.id}")
            ShortAction(YtBookmark, "Lưu", Color.White, onSave)
            ShortAction(YtShare, "Chia sẻ", Color.White, onShare)
        }

        // Kênh + Đăng ký + tiêu đề ở góc dưới trái.
        if (detailsReady && sheetFraction == 0f) Column(
            Modifier.align(Alignment.BottomStart).then(inset).fillMaxWidth(0.80f).padding(start = 14.dp, bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelAvatar(
                    name = author,
                    size = 40.dp,
                    directUrl = info?.avatar ?: video.avatar,
                    channelId = channelId,
                    videoId = video.id,
                    fallbackLetter = title
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    author ?: "",
                    style = ShortShadow,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (showSubscribe) {
                    Spacer(Modifier.width(10.dp))
                    if (subscribed && channel != null) {
                        // Đã đăng ký: chuông + mũi tên, bấm mở bảng Thông báo / Hủy đăng ký.
                        PillButton(
                            "Đã đăng ký", Color(0x33FFFFFF), Color.White,
                            leading = if (ChannelNotify.isOn(account, channel.id, channel.title)) BellRingIcon else BellOffIcon,
                            trailing = Lucide.ChevronDown,
                            modifier = Modifier.tapEffect(subEffect),
                            onClick = onSubscribe
                        )
                    } else {
                        PillButton("Đăng ký", Color(0xFFF1F1F1), Color.Black, modifier = Modifier.tapEffect(subEffect), onClick = onSubscribe)
                    }
                }
            }
            if (title.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    title, style = ShortShadow, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.plainClickable(onTitle)
                )
            }
        }

        // Thanh tiến độ mảnh sát đáy.
        if (sheetFraction == 0f && current && ready) {
            Box(Modifier.align(Alignment.BottomStart).then(inset).fillMaxWidth().height(2.dp).background(Color(0x55FFFFFF))) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color.White))
            }
        }
    }
}

/** Tô xanh các #hashtag như YouTube. */
private fun hashtagged(text: String, tagColor: Color) = buildAnnotatedString {
    var last = 0
    Regex("#[^\\s#]+").findAll(text).forEach { m ->
        append(text.substring(last, m.range.first))
        withStyle(SpanStyle(color = tagColor)) { append(m.value) }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

/**
 * Ngày đăng → (năm, "28 thg 8"). Nhận "2025-08-28", "28 thg 8, 2025", "Aug 28, 2025"; dạng khác (vd. "4 giờ trước") thì giữ nguyên chữ.
 */
private fun dateFrom(vararg sources: String?): Pair<String, String>? {
    for (raw in sources) {
        val t = raw?.trim()?.takeIf { it.isNotEmpty() } ?: continue
        Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(t)?.let { m ->
            return m.groupValues[1] to "${m.groupValues[3].toInt()} thg ${m.groupValues[2].toInt()}"
        }
        Regex("(\\d{1,2})\\s*thg\\s*(\\d{1,2})\\D{0,3}(\\d{4})").find(t)?.let { m ->
            return m.groupValues[3] to "${m.groupValues[1]} thg ${m.groupValues[2]}"
        }
        Regex("([A-Za-z]{3,9})\\.?\\s+(\\d{1,2}),?\\s+(\\d{4})").find(t)?.let { m ->
            return m.groupValues[3] to "${m.groupValues[2]} ${m.groupValues[1]}"
        }
        return t to "Ngày đăng"
    }
    return null
}

@Composable
private fun StatItem(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 14.sp, color = NanColors.TextSecondary)
    }
}

/**
 * Bảng mô tả của Short (bấm vào tiêu đề): tiêu đề đầy đủ, kênh, lượt thích / lượt xem / ngày đăng và mô tả.
 * Nội dung vuốt lên xuống để cuộn như bình thường; chỉ kéo ở thanh nắm trên đỉnh mới đổi chiều cao cả bảng (đi theo ngón tay,
 * thả tay thì tự về mốc gần nhất, xem settleSheet). Nút X hoặc Back cũng đóng.
 */
@Composable
private fun ShortDescriptionSheet(
    video: VideoItem,
    info: ShortInfo?,
    likesHint: String?,
    fraction: Float,
    bottomInset: Boolean,
    onDrag: (Float) -> Unit,
    onRelease: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier
) {
    val meta = ShortsPlayback.meta(video.id)
    val title = info?.title ?: meta?.title ?: video.title
    val author = info?.author ?: meta?.author ?: video.channel
    val channelId = info?.channelId ?: meta?.channelId ?: video.channelId
    // Mô tả + ngày đăng + số liệu từ `next` (như MediaServiceCore), nhận về muộn thì bảng tự cập nhật.
    // Đã tải sẵn từ lúc Short hiện (fillDetails) thì có ngay khi mở bảng; chưa có thì chờ và chưa hiện số lấy từ nguồn khác để khỏi nhảy.
    var desc by remember(video.id) { mutableStateOf(ShortsPlayback.cachedDescription(video.id)) }
    var descDone by remember(video.id) { mutableStateOf(desc != null) }
    LaunchedEffect(video.id) {
        if (descDone) return@LaunchedEffect
        desc = try {
            ShortsPlayback.description(video.id).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        descDone = true
    }
    val facts = desc?.factoids.orEmpty()
    fun fact(vararg keys: String) = facts.firstOrNull { f -> keys.any { f.second.contains(it, ignoreCase = true) } }
    val likeFact = fact("thích", "like")
    val viewFact = fact("xem", "view")
    // Ô còn lại (không phải thích / xem) là ô ngày: giá trị là năm, nhãn là "28 thg 8".
    val dateFact = facts.firstOrNull { it != likeFact && it != viewFact }
    val description = desc?.description?.takeIf { it.isNotBlank() } ?: meta?.description?.takeIf { it.isNotBlank() } ?: title
    val likes = likesHint ?: info?.likes ?: likeFact?.first
    val views = viewFact?.first ?: if (!descDone) null else (meta?.viewCount?.let { ShortsPlayback.countLabel(it) }
        ?: desc?.viewsText?.replace(Regex("\\s*(lượt xem|views?)", RegexOption.IGNORE_CASE), "")?.trim())
    val date = dateFact?.let { it.first to it.second.ifBlank { "Ngày đăng" } }
        ?: if (!descDone) null else dateFrom(desc?.publishText, desc?.isoDate, meta?.publishDate)

    val scroll = rememberScrollState()
    // Cuộn tới đầu / cuối mô tả mà còn dư cử chỉ thì nuốt luôn, không cho tràn lên YtPullRefresh bên ngoài (gây vòng tròn tải lại).
    val swallowOverscroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }
    val dragNow by rememberUpdatedState(onDrag)
    val releaseNow by rememberUpdatedState(onRelease)
    Column(
        modifier.fillMaxWidth().fillMaxHeight(fraction.coerceIn(0f, 1f))
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(NanColors.Background)
            .plainClickable { }
    ) {
        // Thanh nắm: kéo để đổi chiều cao bảng.
        Box(
            Modifier.fillMaxWidth().height(40.dp).draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { dragNow(it) },
                onDragStopped = { releaseNow(it) }
            ),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(NanColors.TextSecondary))
        }
        Box(Modifier.weight(1f).fillMaxWidth().nestedScroll(swallowOverscroll)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).then(if (bottomInset) Modifier.navigationBarsPadding() else Modifier)
                    .padding(start = 18.dp, end = 18.dp, bottom = 28.dp)
            ) {
                Text(
                    hashtagged(title, NanColors.Blue),
                    fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 40.dp)
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChannelAvatar(
                        name = author, size = 30.dp, directUrl = info?.avatar ?: video.avatar,
                        channelId = channelId, videoId = video.id, fallbackLetter = title
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(author ?: "", fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth()) {
                    StatItem(likes ?: "—", "Lượt thích", Modifier.weight(1f))
                    StatItem(views ?: "—", "Lượt xem", Modifier.weight(1f))
                    StatItem(date?.first ?: "—", date?.second ?: "Ngày đăng", Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    hashtagged(description, NanColors.Blue),
                    fontSize = 16.sp, lineHeight = 23.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NanColors.Surface).padding(14.dp)
                )
            }
            // Nút X ngang với dòng đầu của tiêu đề (cao 30dp = lineHeight tiêu đề, nên tâm hai bên trùng nhau).
            Box(
                Modifier.align(Alignment.TopEnd).padding(end = 12.dp).size(30.dp).clip(CircleShape).plainClickable(onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(IconClose, "Đóng", Modifier.size(22.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun ShortAction(
    icon: ImageVector, label: String, tint: Color, onClick: () -> Unit,
    effect: TapEffectState? = null, ringColor: Color = Color.White, countKey: Any? = null
) {
    Column(
        Modifier.plainClickable(onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, Modifier.size(30.dp).tapEffect(effect, ringColor), tint = tint)
        Spacer(Modifier.height(3.dp))
        if (countKey != null) CountText(label, countKey, style = ShortShadow, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        else Text(label, style = ShortShadow, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun PillButton(
    text: String,
    background: Color,
    textColor: Color,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(background).plainClickable(onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.let { Icon(it, null, Modifier.size(16.dp), tint = textColor); Spacer(Modifier.width(5.dp)) }
        Text(text, color = textColor, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        trailing?.let { Spacer(Modifier.width(3.dp)); Icon(it, null, Modifier.size(16.dp), tint = textColor) }
    }
}

// ---- Bình luận ---------------------------------------------------------------------------------

@Composable
private fun CommentsSheet(video: VideoItem, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var comments by remember { mutableStateOf<List<CommentItem>>(emptyList()) }
    var token by remember { mutableStateOf<String?>(null) }
    var total by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val replies = remember { mutableStateMapOf<String, List<CommentItem>>() }
    val loadingReplies = remember { mutableStateListOf<String>() }

    LaunchedEffect(video.id) {
        try {
            val page = CommentsRepository.first(video.id)
            comments = page.items
            token = page.token
            total = page.total
            if (page.items.isEmpty()) error = "Chưa có bình luận nào."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Không tải được bình luận."
        } finally {
            loading = false
        }
    }

    fun loadMore() {
        val t = token ?: return
        if (loadingMore) return
        loadingMore = true
        scope.launch {
            try {
                val page = CommentsRepository.more(t)
                val seen = comments.map { it.id }.toHashSet()
                comments = comments + page.items.filter { seen.add(it.id) }
                token = page.token?.takeIf { page.items.isNotEmpty() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                loadingMore = false
            }
        }
    }
    LoadMoreEffect(listState, comments.size) { loadMore() }

    fun toggleReplies(c: CommentItem) {
        if (replies.containsKey(c.id)) {
            replies.remove(c.id)
            return
        }
        val t = c.repliesToken ?: return
        if (c.id in loadingReplies) return
        loadingReplies.add(c.id)
        scope.launch {
            try {
                replies[c.id] = CommentsRepository.replies(t).items
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                loadingReplies.remove(c.id)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0x99000000)).plainClickable(onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.72f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(NanColors.Background)
                .plainClickable { }
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                    .size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(NanColors.Divider)
            )
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Bình luận", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                total?.let { Text(it, fontSize = 15.sp, color = NanColors.TextSecondary, modifier = Modifier.padding(start = 10.dp)) }
                Spacer(Modifier.weight(1f))
                TopIcon(IconClose, onClose)
            }
            HorizontalDivider(color = NanColors.Surface)
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                }
                error != null && comments.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(error ?: "", color = NanColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
                else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(comments, key = { it.id }) { c ->
                        Column {
                            CommentRow(c)
                            if (c.repliesToken != null) {
                                val open = replies.containsKey(c.id)
                                Row(
                                    Modifier.padding(start = 64.dp, top = 2.dp, bottom = 6.dp).plainClickable { toggleReplies(c) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        (c.replies?.let { "$it phản hồi" } ?: "Xem phản hồi"),
                                        fontSize = 13.sp, fontWeight = FontWeight.Medium, color = NanColors.Blue
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    if (c.id in loadingReplies) {
                                        CircularProgressIndicator(color = NanColors.Blue, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                    } else {
                                        Icon(Lucide.ChevronDown, null, Modifier.size(18.dp).rotate(if (open) 180f else 0f), tint = NanColors.Blue)
                                    }
                                }
                            }
                            replies[c.id]?.forEach { CommentRow(it) }
                        }
                    }
                    if (loadingMore) item { LoadingFooter() }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(c: CommentItem) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (c.isReply) 64.dp else 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp)
    ) {
        Avatar(c.author.trimStart('@').take(1).uppercase(), if (c.isReply) 24.dp else 36.dp, photoUrl = c.avatar)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(c.author, c.published).joinToString(" • "),
                fontSize = 12.sp, color = NanColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(c.text, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.ThumbsUp, null, Modifier.size(16.dp), tint = NanColors.TextSecondary)
                c.likes?.let { Text(it, fontSize = 12.sp, color = NanColors.TextSecondary, modifier = Modifier.padding(start = 6.dp)) }
            }
        }
    }
}
