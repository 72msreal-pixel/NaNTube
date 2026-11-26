@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nan.nantube.ui

import android.content.Intent
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.unit.IntSize
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
import com.composables.icons.lucide.ThumbsUp
import com.nan.nantube.R
import com.nan.nantube.data.Account
import com.nan.nantube.data.Connectivity
import com.nan.nantube.data.CaptionCue
import com.nan.nantube.data.ChannelNotify
import com.nan.nantube.data.ChannelSubscriptions
import com.nan.nantube.data.SearchResult
import com.nan.nantube.data.ShortDescription
import com.nan.nantube.data.ShortInfo
import com.nan.nantube.data.ShortsCaptions
import com.nan.nantube.data.ShortsFeedback
import com.nan.nantube.data.ShortsMedia
import com.nan.nantube.data.LikeCounts
import com.nan.nantube.data.ShortsPlayback
import com.nan.nantube.data.SavedComments
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

/** Các bảng kéo được của Short: chung một khung, một lúc chỉ mở một bảng. */
private enum class ShortPanel { Description, Menu, Transcript, Comments }

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
            hasVideo = feed.items.getOrNull(state.pageFor(feed.version)) != null
        )
        QuietFeedRetry(feed, account)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (feed.items.isEmpty() && feed.error != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ShortSpinner() }
            else PagedBody(feed, onRetry = { feed.load(account, force = true) }) {
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
                hasVideo = list.getOrNull(page) != null
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

/** Khung trang (rộng / cao) từ mức này trở lên thì Short dọc hiện nguyên khung (có viền đen) thay vì phóng to cắt mất hình. */
private const val MAX_ZOOM_ASPECT = 0.72f

/** Khoảng chờ giữa các lần tự tải lại bộ Short khi chưa có mạng. */
private const val FEED_RETRY_MS = 4_000L

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
    var flashTick by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var tick by remember { mutableIntStateOf(0) }
    val likeTexts = remember(account?.id) { mutableStateMapOf<String, String>() }
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
    val sheet = remember { PanelSheet(scope, SHEET_PEEK, SHEET_FULL) }
    var panel by remember { mutableStateOf<ShortPanel?>(null) }
    var boxHeight by remember { mutableIntStateOf(0) }
    // Khoảng từ đáy vùng Short tới đáy màn hình: cửa sổ tự co lại theo bàn phím thì phần này đã bằng bàn phím, ta không chừa thêm lần nữa.
    var gapBelowPx by remember { mutableIntStateOf(0) }
    val imeBottomPx = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)
    val imeOverlapPx = if (panel == ShortPanel.Comments) (imeBottomPx - gapBelowPx).coerceAtLeast(0) else 0
    val hostView = androidx.compose.ui.platform.LocalView.current
    val sheetFraction = sheet.fraction

    fun openSheet(kind: ShortPanel) {
        panel = kind
        sheet.open()
    }

    fun closeSheet() = sheet.close { panel = null }

    // Rời tab thì đóng bảng. Đổi Short thì bảng (mô tả / bình luận / bản ghi / menu) giữ nguyên, nội dung đổi theo video mới.
    LaunchedEffect(active) {
        if (panel != null && !active) {
            sheet.snapClosed()
            panel = null
        }
    }
    // Bấm ⋯ ở thanh trên: chỉ trình phát đang hiện (active) mới nhận yêu cầu mở menu, rồi hạ cờ xuống.
    LaunchedEffect(ShortsChrome.menuRequest, active) {
        if (ShortsChrome.menuRequest && active) {
            ShortsChrome.menuRequest = false
            openSheet(ShortPanel.Menu)
        }
    }
    // Chế độ làm sạch màn hình: trạng thái dùng chung với thanh trên (xem ShortsChrome).
    val clean = ShortsChrome.clean && active
    LaunchedEffect(active) { if (!active) ShortsChrome.reset() }
    DisposableEffect(Unit) { onDispose { ShortsChrome.reset() } }
    // Báo cho AppRoot ẩn thanh tab dưới khi có bảng đang mở.
    LaunchedEffect(panel, active) { ShortsSheet.open = panel != null && active }
    DisposableEffect(Unit) { onDispose { ShortsSheet.open = false } }
    BackHandler(enabled = panel != null) { closeSheet() }
    // Đang làm sạch màn hình: Back hiện lại mọi thứ trước (bảng menu có Back riêng, ưu tiên hơn).
    BackHandler(enabled = clean) { ShortsChrome.clean = false }

    val curVideo = items.getOrNull(page)
    val curSlot = curVideo?.let { ShortsPool.slot(it.id) }

    // Picture-in-Picture (như meetube): Short đang hiện báo cho MainActivity biết để tự vào PiP khi rời app, và nhận Trước / Phát-Tạm dừng / Tiếp từ cửa sổ PiP.
    // Biểu tượng / cờ "đang phát" theo thực tế: ExoPlayer tự dừng khi app khác giành âm thanh thì cũng tính là đang dừng.
    val shownPaused = userPaused || curSlot?.externalPaused == true
    LaunchedEffect(active, curVideo?.id, shownPaused, curSlot?.vertical) {
        if (!active) return@LaunchedEffect
        PipState.shortId = curVideo?.id
        PipState.video = curVideo
        PipState.playing = !shownPaused
        PipState.vertical = curSlot?.vertical ?: true
    }
    DisposableEffect(active) {
        val controls = if (active) PipControls(
            prev = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } },
            next = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost((latest.size - 1).coerceAtLeast(0))) } },
            toggle = {
                val id = latest.getOrNull(pagerState.currentPage)?.id
                if (id != null && ShortsPool.slot(id)?.externalPaused == true) ShortsPool.setActive(id, true) else userPaused = !userPaused
            }
        ) else null
        if (controls != null) {
            PipState.controls = controls
            PipState.snapshot = { latest to pagerState.currentPage }
        }
        onDispose {
            if (controls != null && PipState.controls === controls) {
                PipState.controls = null
                PipState.snapshot = null
                PipState.shortId = null
                PipState.video = null
                PipState.playing = false
            }
        }
    }
    // Bấm Hình trong hình: Short này thu thành cửa sổ nhỏ nổi trong app (ShortsMini), AppRoot đưa app về tab Main.
    LaunchedEffect(PipState.miniRequest, active) {
        if (PipState.miniRequest && active) {
            PipState.miniRequest = false
            ShortsMini.start(latest, pagerState.currentPage)
        }
    }
    // Vào PiP: chỉ giữ video (làm sạch màn hình, đóng bảng đang mở); ra khỏi PiP trả lại như cũ.
    var wasInPip by remember { mutableStateOf(false) }
    var cleanBeforePip by remember { mutableStateOf(false) }
    LaunchedEffect(PipState.inPip, active) {
        if (!active) return@LaunchedEffect
        if (PipState.inPip) {
            wasInPip = true
            cleanBeforePip = ShortsChrome.clean
            ShortsChrome.clean = true
            if (panel != null) {
                sheet.snapClosed()
                panel = null
            }
        } else if (wasInPip) {
            wasInPip = false
            ShortsChrome.clean = cleanBeforePip
            // PiP bị đóng (X) thì MainActivity gọi ShortsBackground.stopAll(); bấm mở rộng thì video cứ phát tiếp.
        }
    }
    val curMeta = curVideo?.let { ShortsPlayback.meta(it.id) }
    val curSettled = curVideo != null && settled[curVideo.id] == true
    // Rời app / về lại app do ShortsBackground lo (MainActivity gọi đúng chỗ, theo meetube): bộ phát không tự dừng ở ON_STOP nữa,
    // vì lúc đó video còn phải phát ở PiP hoặc phát nền. Ở đây chỉ nhắc hiệu ứng phát chạy lại khi app hiện lại.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // App hiện lại sau khi phiên nền đã chuyển Short khác (Tiếp / Trước / tự vuốt lúc app ẩn): cuộn tới đúng video đang phát.
    LaunchedEffect(PipState.returnId, active) {
        val id = PipState.returnId ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        val at = latest.indexOfFirst { it.id == id }
        if (at >= 0 && at != pagerState.currentPage) pagerState.scrollToPage(at)
        PipState.returnId = null
    }

    // Đổi trang (hoặc vào lại tab): nạp sẵn video đang xem + kế + trước. Ô kế / trước đã đứng ở khung đầu nên vuốt tới là phát luôn.
    LaunchedEffect(page, active, items.size, account?.id) {
        val list = latest
        if (!active) {
            if (!ShortsMini.open) ShortsPool.pauseAll()
            return@LaunchedEffect
        }
        onPage(page)
        if (page >= list.size - 4) onNearEnd()
        ShortsPool.window(listOfNotNull(list.getOrNull(page), list.getOrNull(page + 1), list.getOrNull(page - 1)))
        // Video thứ 2 phía sau: chỉ lấy link sẵn (không nạp trình phát) để tới lượt là có ngay.
        list.getOrNull(page + 2)?.let { ShortsPlayback.warm(context, account, it, bytes = false) }
    }
    LaunchedEffect(page) { userPaused = false }

    // Thả tay mà pager bắt đầu trượt nốt sang video khác (targetPage != settledPage) là dừng tiếng video cũ NGAY,
    // không đợi trượt xong (settledPage chỉ đổi sau hoạt ảnh trượt nên trước đây video cũ còn kêu thêm một đoạn).
    // Pager tự quyết định trượt sang trang khác hay bật về trang cũ, nên bật về thì targetPage == settledPage và video vẫn phát tiếp.
    // Đang giữ tay kéo thì chưa dừng. Cũng áp dụng cho tự vuốt (hết video / không quan tâm).
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()
    val leaving by remember(pagerState) { derivedStateOf { !dragged && pagerState.targetPage != pagerState.settledPage } }

    // Chỉ ô của video đang xem được phát, và chỉ khi đã có đủ số thích / bình luận (hoặc quá hạn chờ) để chữ không nhảy.
    LaunchedEffect(page, active, userPaused, curSettled, curSlot, tick, leaving, ShortsMini.open, PipState.returnId) {
        if (ShortsMini.open) return@LaunchedEffect // popup / phiên nền đang giữ các ô phát (kể cả khi chạy nền)
        if (PipState.returnId != null) return@LaunchedEffect // vừa từ nền về: chờ cuộn tới đúng video rồi mới phát
        ShortsPool.setActive(if (active) curVideo?.id else null, active && !userPaused && curSettled && !leaving)
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
    // Short có bình luận đã lưu: mở ra là tự mở bảng bình luận của nó.
    LaunchedEffect(curVideo?.id, active) {
        val id = curVideo?.id ?: return@LaunchedEffect
        if (active && panel == null && SavedComments.hasVideo(account, id)) openSheet(ShortPanel.Comments)
    }

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

    LaunchedEffect(curVideo?.id, curSlot, account?.id, active) {
        val video = curVideo ?: return@LaunchedEffect
        val slot = curSlot ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        WatchHistory.track(account, video.copy(isShort = true)) {
            if (!slot.released && slot.frame && (slot.player.isPlaying || slot.ended)) {
                WatchHistory.Playback(slot.player.currentPosition / 1000f,
                    slot.player.duration.takeIf { it > 0 }?.let { it / 1000f })
            } else null
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
            if (ShortsChrome.captionKey != null || panel == ShortPanel.Transcript) positionMs = slot.player.currentPosition
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
                toast(e.shortMessage ?: "Không thích được video")
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
                toast(e.shortMessage ?: "Không đổi được trạng thái đăng ký")
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

    // Phụ đề: bật ở menu thì tải bản của Short đang xem (khi đã nạp xong, lúc đó /player đã trả danh sách phụ đề) và tự vẽ theo vị trí phát.
    var cues by remember { mutableStateOf<List<CaptionCue>>(emptyList()) }
    val captionKey = ShortsChrome.captionKey
    LaunchedEffect(curVideo?.id, curSlot?.ready, captionKey) {
        cues = emptyList()
        val id = curVideo?.id ?: return@LaunchedEffect
        if (captionKey == null || curSlot?.ready != true) return@LaunchedEffect
        val tracks = ShortsPlayback.meta(id)?.captionTracks.orEmpty()
        val track = ShortsCaptions.pick(tracks, captionKey) ?: return@LaunchedEffect
        try {
            cues = ShortsCaptions.load(track)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Toast.makeText(context, e.message?.takeIf { it.startsWith("Không tải được phụ đề") } ?: "Không tải được phụ đề", Toast.LENGTH_LONG).show()
        }
    }
    val captionText by remember {
        androidx.compose.runtime.derivedStateOf { if (cues.isEmpty()) null else ShortsCaptions.textAt(cues, positionMs) }
    }

    // Tự vuốt: bật ở menu thì hết video là sang Short kế, kể cả khi đang mở mô tả / bản ghi / bình luận / menu ba chấm
    // (bảng còn mở, nội dung đổi theo video mới).
    val reading = false
    LaunchedEffect(reading) { ShortsPool.hold(reading) }
    DisposableEffect(Unit) { onDispose { ShortsPool.hold(false) } }
    val finished = ShortsPool.autoScroll && !reading && curSlot?.ended == true
    LaunchedEffect(finished, page) {
        if (finished && page + 1 < latest.size) {
            // Chờ đuôi âm thanh dứt hẳn rồi mới vuốt: vuốt là tắt tiếng video cũ ngay (xem `leaving`), nên vuốt sớm thì người nói bị cắt ngang.
            delay(AUTO_SWIPE_TAIL_MS)
            pagerState.animateScrollToPage(page + 1)
        }
    }
    // Đổi cỡ cửa sổ (chia đôi màn hình, xoay, gập mở): pager không được kẹt lưng chừng giữa hai Short.
    LaunchedEffect(boxHeight) {
        delay(50)
        if (!pagerState.isScrollInProgress && pagerState.currentPageOffsetFraction != 0f) pagerState.scrollToPage(pagerState.currentPage)
    }
    // Đang tự vuốt mà mở bảng (hoặc video bị tua về 0) thì animate bị huỷ giữa chừng, pager đứng lưng chừng hai Short: kéo về trang gần nhất.
    val scrolling = pagerState.isScrollInProgress
    LaunchedEffect(scrolling) {
        if (scrolling) return@LaunchedEffect
        delay(50)
        if (pagerState.currentPageOffsetFraction != 0f) pagerState.animateScrollToPage(pagerState.currentPage)
    }

    // Như meetube: đóng menu rồi gửi phản hồi; YouTube nhận thì sang Short kế (video này không hiện lại ở lần mở sau), không thì ở nguyên và báo lỗi.
    fun sendFeedback(kind: ShortsFeedback.Kind) {
        val video = curVideo ?: return
        closeSheet()
        scope.launch {
            try {
                ShortsFeedback.send(account, video, kind)
                ShortsSeen.add(video.id)
                toast(
                    if (kind == ShortsFeedback.Kind.NOT_INTERESTED) "Đã xóa video khỏi mục đề xuất"
                    else "Chúng tôi sẽ không đề xuất kênh này cho bạn nữa"
                )
                if (page + 1 < latest.size) pagerState.animateScrollToPage(page + 1)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(
                    if (e is IllegalStateException || e is IllegalArgumentException) e.message ?: "Không gửi được tới YouTube. Hãy thử lại."
                    else "Không gửi được tới YouTube. Hãy thử lại."
                )
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).onSizeChanged { boxHeight = it.height }
            .onGloballyPositioned { c ->
                // Cùng hệ toạ độ cửa sổ với số đo của bàn phím.
                val bottom = c.positionInWindow().y + c.size.height
                gapBelowPx = (hostView.rootView.height - bottom).toInt().coerceAtLeast(0)
            }
    ) {
        if (items.isNotEmpty()) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = panel == null,
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
                    sheetFraction = sheetFraction, // mọi trang cùng co lại theo bảng để vuốt sang Short kế không bị nhảy cỡ
                    clean = clean,
                    onTitle = { openSheet(ShortPanel.Description) },
                    paused = userPaused || ShortsPool.slot(video.id)?.externalPaused == true,
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
                    flashTick = flashTick,
                    onTap = {
                        // Đang dừng do app khác giành âm thanh (biểu tượng Phát): chạm là phát lại, không phải "tạm dừng".
                        if (ShortsPool.slot(video.id)?.externalPaused == true) ShortsPool.setActive(video.id, true)
                        else userPaused = !userPaused
                        flashTick++
                    },
                    onLike = { like(video) },
                    onComments = { openSheet(ShortPanel.Comments) },
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

        // Phụ đề nằm trên cột nút bên phải và dưới giữa màn; ẩn khi bảng mô tả đang mở.
        if (sheetFraction == 0f) captionText?.let { text ->
            CaptionOverlay(
                text,
                Modifier.align(Alignment.BottomCenter)
                    .then(if (bottomInset) Modifier.navigationBarsPadding() else Modifier)
                    .padding(start = 16.dp, end = 72.dp, bottom = 150.dp)
            )
        }

        // Làm sạch màn hình: chỉ còn nút nhỏ ở góc dưới bên phải để hiện lại mọi thứ.
        if (clean && sheetFraction == 0f && !PipState.inPip) {
            CleanRestoreButton(
                onClick = { ShortsChrome.clean = false },
                modifier = Modifier.align(Alignment.BottomEnd)
                    .then(if (bottomInset) Modifier.navigationBarsPadding() else Modifier)
                    .padding(end = 12.dp, bottom = 14.dp)
            )
        }

        val openPanel = panel
        if (openPanel != null && curVideo != null) {
            DraggablePanel(
                fraction = sheetFraction,
                onDrag = { sheet.drag(it, boxHeight) },
                onRelease = { sheet.settle(it) { panel = null } },
                modifier = Modifier.align(Alignment.BottomCenter),
                liftPx = imeOverlapPx
            ) {
                // key theo video: đổi Short thì nội dung bảng dựng lại cho video mới (cuộn về đầu, tải lại bình luận / mô tả).
                key(curVideo.id) { when (openPanel) {
                    ShortPanel.Description -> ShortDescriptionContent(
                        video = curVideo,
                        info = infos[curVideo.id],
                        likesHint = likeLabel(curVideo),
                        // Thanh tab dưới đã ẩn khi mở bảng nên bảng tự chừa thanh điều hướng của hệ thống.
                        bottomInset = true,
                        onClose = { closeSheet() }
                    )
                    ShortPanel.Menu -> ShortsMenuContent(
                        tracks = curMeta?.captionTracks.orEmpty(),
                        qualities = ShortsPlayback.qualities(curVideo.id),
                        playingQuality = ShortsPool.playingQuality(curVideo.id),
                        audios = ShortsPlayback.audioChoices(curVideo.id),
                        playingAudio = ShortsPool.playingAudio(curVideo.id),
                        onDescription = { openSheet(ShortPanel.Description) },
                        onTranscript = { openSheet(ShortPanel.Transcript) },
                        onClean = {
                            closeSheet()
                            ShortsChrome.clean = true
                        },
                        onDownload = {
                            closeSheet()
                            DownloadPanel.open(curVideo, account)
                        },
                        onPip = { PipState.request() },
                        onNotInterested = { sendFeedback(ShortsFeedback.Kind.NOT_INTERESTED) },
                        onDontRecommend = { sendFeedback(ShortsFeedback.Kind.DONT_RECOMMEND_CHANNEL) },
                        onDismiss = { closeSheet() }
                    )
                    ShortPanel.Comments -> ShortChatContent(video = curVideo, account = account, imeOverlapPx = imeOverlapPx, onSave = onSave, onClose = { closeSheet() })
                    ShortPanel.Transcript -> TranscriptContent(
                        tracks = curMeta?.captionTracks.orEmpty(),
                        captionKey = captionKey,
                        positionMs = positionMs,
                        onSeek = { ms ->
                            curSlot?.player?.seekTo(ms)
                            userPaused = false
                        },
                        onClose = { closeSheet() }
                    )
                } }
            }
        }

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

/** Đường kính chấm tròn trên thanh tua (lúc kéo chậm; kéo nhanh thì scale lên). */
private val KNOB_SIZE = 10.dp

private val ShortShadow = TextStyle(shadow = Shadow(color = Color(0x80000000), offset = Offset(0f, 1f), blurRadius = 4f))
private val LikedRed = Color(0xFFFF3B47)

/**
 * Thanh trên cùng đen như YouTube: (Back) ... Tìm kiếm, ⋯. Video nằm dưới thanh này nên không bị đè chữ.
 * Nút ⋯ luôn hiện, mở bảng menu Short ([ShortsMenuContent]) khi đã có video; làm sạch màn hình thì ẩn hết nút nhưng giữ nguyên chỗ (thanh vẫn đen).
 */
@Composable
private fun ShortsHeader(
    onBack: (() -> Unit)?,
    onSearch: () -> Unit,
    hasVideo: Boolean
) {
    // Cửa sổ PiP chỉ còn video: không vẽ thanh trên.
    if (PipState.inPip) return
    Box(Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding().height(48.dp)) {
        if (!ShortsChrome.clean) {
            Row(Modifier.fillMaxSize().sideInsets().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) TopIcon(IconArrowLeft, onBack)
                Spacer(Modifier.weight(1f))
                if (hasVideo) TopIcon(PipIcon) { PipState.request() }
                TopIcon(YtSearch, onSearch)
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    MoreButton(width = 36.dp) { if (hasVideo) ShortsChrome.menuRequest = true }
                }
            }
        }
    }
}

@Composable
private fun Modifier.sideInsets(): Modifier = windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))

@Composable
private fun ShortSpinner(modifier: Modifier = Modifier) {
    CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = modifier.size(36.dp))
}

/** Bộ Short chưa tải được (thường do mất mạng): không báo lỗi, tự tải lại sau ít giây hoặc ngay khi có mạng. */
@Composable
private fun QuietFeedRetry(feed: PagedFeed<VideoItem>, account: Account?) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Connectivity.start(context) }
    val tick = Connectivity.tick
    var seenTick by remember { mutableIntStateOf(tick) }
    val stuck = feed.items.isEmpty() && feed.error != null && !feed.loading
    LaunchedEffect(stuck, tick) {
        if (!stuck) return@LaunchedEffect
        if (tick == seenTick) delay(FEED_RETRY_MS) else seenTick = tick
        feed.load(account, force = true)
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
    clean: Boolean,
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
    flashTick: Int,
    onTap: () -> Unit,
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
    // Đang kéo thanh thời gian (tỉ lệ 0..1): video vẫn phát, thả tay mới tua; ẩn chữ + nút, hiện "00:37 / 01:00" ngay trên thanh như YouTube.
    var scrub by remember { mutableStateOf<Float?>(null) }
    // Chấm tròn to lên khi kéo nhanh, nhỏ lại khi kéo chậm / dừng.
    var knobBoost by remember { mutableFloatStateOf(0f) }
    var lastMove by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lastMove) {
        delay(120)
        knobBoost = 0f
    }
    val knobScale by animateFloatAsState(1f + knobBoost * 1.2f, label = "knob")
    fun scrubTo(fraction: Float) {
        scrub = fraction.coerceIn(0f, 1f)
    }
    fun commitScrub() {
        val f = scrub ?: return
        scrub = null
        val player = slot?.player ?: return
        if (player.duration > 0) player.seekTo((player.duration * f).toLong())
    }
    val chrome = !clean && scrub == null

    val flash = remember { PlayFlash() }
    val firstTick = remember { flashTick }
    LaunchedEffect(flashTick) {
        if (flashTick != firstTick && current) flash.show(playing = !paused)
    }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val zoom = vertical && sheetFraction == 0f && (boxSize.height == 0 || boxSize.width.toFloat() / boxSize.height < MAX_ZOOM_ASPECT)

    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { boxSize = it }.pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }) {
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
                    it.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
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

        if (sheetFraction == 0f && chrome) Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(230.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))))
        )

        Box(videoArea.align(Alignment.TopCenter), contentAlignment = Alignment.Center) { PlayFlashDisc(flash) }
        if (sheetFraction == 0f && (error != null || !detailsReady || (current && (!ready || buffering) && !paused && slot != null))) {
            ShortSpinner(Modifier.align(Alignment.Center))
        }
        // Cột nút bên phải như Shorts của YouTube: tim, bình luận, lưu, chia sẻ (có số khi YouTube trả về).
        if (detailsReady && sheetFraction == 0f && chrome) Column(
            Modifier.align(Alignment.BottomEnd).then(inset).sideInsets().padding(end = 4.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ShortAction(if (liked) YtHeartFilled else YtHeart, likes ?: "Thích", if (liked) LikedRed else Color.White, onLike, likeEffect, LikedRed, "like|${video.id}")
            ShortAction(YtComment, commentCount ?: "Bình luận", Color.White, onComments, countKey = "comment|${video.id}")
            ShortAction(YtBookmark, "Lưu", Color.White, onSave)
            ShortAction(YtShare, "Chia sẻ", Color.White, onShare)
        }

        // Kênh + Đăng ký + tiêu đề ở góc dưới trái.
        if (detailsReady && sheetFraction == 0f && chrome) Column(
            Modifier.align(Alignment.BottomStart).then(inset).sideInsets().fillMaxWidth(0.80f).padding(start = 14.dp, bottom = 16.dp)
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

        // Thanh tiến độ sát đáy: chạm hoặc kéo ngang để tua; lúc kéo thanh chuyển đỏ, có chấm tròn và hiện thời gian ngay trên thanh.
        if (sheetFraction == 0f && current && ready) {
            val dragging = scrub != null
            val shown = scrub ?: progress
            val barHeight = if (dragging) 3.dp else 2.dp
            val accent = if (dragging) Color(0xFFFF0000) else Color.White
            Box(
                Modifier.align(Alignment.BottomStart).then(inset).fillMaxWidth().height(24.dp)
                    .pointerInput(slot) { detectTapGestures(onTap = { scrubTo(it.x / size.width); commitScrub() }) }
                    .pointerInput(slot) {
                        detectHorizontalDragGestures(
                            onDragStart = { scrubTo(it.x / size.width) },
                            onDragEnd = { commitScrub() },
                            onDragCancel = { scrub = null },
                            onHorizontalDrag = { change, dx ->
                                scrubTo(change.position.x / size.width)
                                val dt = (change.uptimeMillis - change.previousUptimeMillis).coerceAtLeast(1L)
                                knobBoost = (kotlin.math.abs(dx) / dt / 2f).coerceIn(0f, 1f)
                                lastMove = change.uptimeMillis
                            }
                        )
                    },
                contentAlignment = Alignment.BottomStart
            ) {
                Box(Modifier.fillMaxWidth().height(barHeight).background(Color(0x55FFFFFF))) {
                    Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(accent))
                }
                // Chấm nằm đúng đầu phần đã chạy (neo vào mép phải của thanh đỏ), tâm trùng tâm thanh; scale quanh tâm nên không lệch.
                if (dragging) Box(Modifier.fillMaxWidth(shown).height(KNOB_SIZE).offset(y = KNOB_SIZE / 2 - barHeight / 2)) {
                    Box(
                        Modifier.align(Alignment.CenterEnd).offset(x = KNOB_SIZE / 2).size(KNOB_SIZE)
                            .scale(knobScale).clip(CircleShape).background(accent)
                    )
                }
            }
            scrub?.let { f ->
                val total = slot?.player?.duration?.coerceAtLeast(0L) ?: 0L
                Text(
                    buildAnnotatedString {
                        append(clock((total * f).toLong(), pad = true))
                        withStyle(SpanStyle(color = Color(0xB3FFFFFF))) { append(" / ${clock(total, pad = true)}") }
                    },
                    style = ShortShadow, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomCenter).then(inset).padding(bottom = 40.dp)
                )
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

/** Số liệu của video để [WatchDescriptionExtras] dựng mục "Chi tiết video" (null = chưa có). */
internal class DescStats(val date: String?, val views: String?, val likes: String?)

/**
 * Nội dung bảng mô tả của Short (bấm vào tiêu đề): tiêu đề đầy đủ, kênh, lượt thích / lượt xem / ngày đăng và mô tả.
 * Nằm trong [DraggablePanel]: nội dung vuốt lên xuống để cuộn như bình thường, chỉ kéo ở thanh nắm trên đỉnh mới đổi chiều cao cả bảng
 * (thả tay thì tự về mốc gần nhất, xem PanelSheet). Nút X hoặc Back cũng đóng.
 */
@Composable
internal fun ColumnScope.ShortDescriptionContent(
    video: VideoItem,
    info: ShortInfo?,
    likesHint: String?,
    bottomInset: Boolean,
    onClose: () -> Unit,
    extras: @Composable ColumnScope.(DescStats) -> Unit = {}
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
    // Số liệu đầy đủ cho mục "Chi tiết video" (ngày dạng "26 thg 11, 2026", lượt xem dạng "26.056").
    val stats = DescStats(
        date = date?.let { if (it.second == "Ngày đăng") it.first else "${it.second}, ${it.first}" },
        views = meta?.viewCount?.let { java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("vi")).format(it) }
            ?: desc?.viewsText?.replace(Regex("\\s*(lượt xem|views?)", RegexOption.IGNORE_CASE), "")?.trim()?.takeIf { it.isNotEmpty() }
            ?: views,
        likes = likes
    )

    val scroll = rememberScrollState()
    Box(Modifier.weight(1f).fillMaxWidth()) {
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
            extras(stats)
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

@Composable
private fun ShortAction(
    icon: ImageVector, label: String, tint: Color, onClick: () -> Unit,
    effect: TapEffectState? = null, ringColor: Color = Color.White, countKey: Any? = null
) {
    Column(
        Modifier.plainClickable(onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, Modifier.size(30.dp).tapEffect(effect, ringColor, ring = false), tint = tint)
        Spacer(Modifier.height(3.dp))
        if (countKey != null) CountText(label, countKey, style = ShortShadow, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        else Text(label, style = ShortShadow, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
internal fun PillButton(
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

