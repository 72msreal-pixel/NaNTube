@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nan.nantube.ui

import android.content.Intent
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
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
import com.nan.nantube.data.ShortInfo
import com.nan.nantube.data.ShortsMedia
import com.nan.nantube.data.ShortsPlayback
import com.nan.nantube.data.ShortsSeen
import com.nan.nantube.data.VideoItem
import com.nan.nantube.data.VideoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

    fun open(list: List<VideoItem>) {
        items = list
        serial++
    }

    fun close() {
        items = null
    }
}

/** Vị trí đang xem của tab Short, giữ ở AppRoot để đổi tab rồi quay lại không mất chỗ. */
class ShortsTabState {
    var page by mutableIntStateOf(0)

    /** Tăng lên là tab Short tải lại danh sách mới (nút làm mới, hoặc quay lại app sau một lúc lâu). */
    var epoch by mutableIntStateOf(0)
    var loadedEpoch = 0

    fun refresh() {
        page = 0
        epoch++
    }
}

fun shortsFeed() = PagedFeed(
    first = { account, _ -> VideoRepository.shortsPage(account) },
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
        if (force) state.page = 0
        feed.load(account, force = force)
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        ShortsHeader(
            onBack = null,
            onRefresh = { state.refresh() },
            refreshing = feed.loading,
            onSearch = onSearch,
            video = feed.items.getOrNull(state.page),
            account = account,
            onSave = onSaveToPlaylist
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PagedBody(feed, onRetry = { feed.load(account, force = true) }) {
                ShortsPager(
                    items = feed.items,
                    startPage = state.page,
                    account = account,
                    active = ShortsViewer.items == null,
                    bottomInset = false,
                    onPage = { state.page = it },
                    onNearEnd = { feed.loadMore(account) },
                    onSave = onSaveToPlaylist
                )
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
        var page by remember { mutableIntStateOf(0) }
        val scope = rememberCoroutineScope()

        fun append(items: List<VideoItem>) {
            val seen = list.map { it.id }.toHashSet()
            list = list + items.filter { seen.add(it.id) }
        }

        LaunchedEffect(Unit) {
            if (initial.size >= 5) return@LaunchedEffect
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
                onRefresh = null,
                refreshing = false,
                onSearch = onSearch,
                video = list.getOrNull(page),
                account = account,
                onSave = onSave
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ShortsPager(
                    items = list,
                    startPage = 0,
                    account = account,
                    active = true,
                    bottomInset = true,
                    onPage = { page = it },
                    onNearEnd = {
                        val t = token
                        if (t != null && !busy) {
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

/** Trạng thái của lượt phát hiện tại, giữ ngoài Compose để trình nghe lỗi của ExoPlayer đọc được giá trị mới nhất. */
private class PlayRun {
    var videoId: String? = null
    var source: String? = null
    var lastRetry = 0
    var autoTries = 0
    var resumeMs = 0L

    /** Client đã bị từ chối cho từng video: lần lấy link kế xếp chúng cuối (như recovery của meetube). */
    val failed = HashMap<String, MutableSet<String>>()
}

/** Quá thời gian này mà chưa lên hình đầu tiên thì coi là link kẹt và đổi client. */
private const val FIRST_FRAME_TIMEOUT_MS = 9_000L

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

    val player = remember {
        ExoPlayer.Builder(context)
            .setLoadControl(ShortsMedia.loadControl())
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                    true
                )
            }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    val playRun = remember { PlayRun() }
    var userPaused by remember { mutableStateOf(false) }
    var readyId by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var vertical by remember { mutableStateOf(true) }
    var commentsFor by remember { mutableStateOf<VideoItem?>(null) }
    var notifyFor by remember { mutableStateOf<SearchResult.Channel?>(null) }
    var subBusy by remember { mutableStateOf(false) }
    val liked = remember { mutableStateMapOf<String, Boolean>() }
    // Kênh + trạng thái đăng ký thật của từng Short (hỏi song song với việc lấy link, không chờ video tải xong).
    val infos = remember(account?.id) { mutableStateMapOf<String, ShortInfo>() }
    val pausedNow by rememberUpdatedState(userPaused)

    /**
     * Link bị từ chối / hết hạn / kẹt / ExoPlayer báo lỗi giữa chừng: đổi sang client khác lấy link mới rồi phát tiếp từ chỗ đang xem.
     * Quá [MAX_AUTO_RECOVERY] lần mới hiện lỗi cho người dùng bấm "Thử lại".
     */
    fun recover() {
        val id = playRun.videoId ?: return
        if (playRun.autoTries >= MAX_AUTO_RECOVERY) {
            error = "Không phát được video này."
            return
        }
        playRun.autoTries++
        playRun.resumeMs = player.currentPosition.coerceAtLeast(0L)
        playRun.source?.let { playRun.failed.getOrPut(id) { mutableSetOf() }.add(it) }
        ShortsPlayback.forget(id)
        retry++
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                recover()
            }

            override fun onRenderedFirstFrame() {
                readyId = playRun.videoId
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) vertical = size.width.toFloat() / size.height < 0.8f
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
            else if (event == Lifecycle.Event.ON_RESUME && !pausedNow && player.mediaItemCount > 0) player.play()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Đổi trang (hoặc vào lại tab / đổi client sau lỗi) thì phát video của trang đó: lấy link → dựng DASH → ExoPlayer phát từng đoạn.
    LaunchedEffect(page, active, retry) {
        val list = latest
        val video = list.getOrNull(page)
        if (!active || video == null) {
            player.pause()
            return@LaunchedEffect
        }
        onPage(page)
        if (page >= list.size - 4) onNearEnd()
        // Chạy lại vì lỗi (tự đổi client hoặc bấm Thử lại) thì giữ số lần thử và chỗ đang xem; còn lại là lượt phát mới.
        val isRetry = retry != playRun.lastRetry
        playRun.lastRetry = retry
        if (!isRetry) {
            playRun.autoTries = 0
            playRun.resumeMs = 0L
            userPaused = false
        }
        playRun.videoId = video.id
        error = null
        readyId = null
        progress = 0f
        player.stop()
        player.clearMediaItems()
        try {
            val plan = ShortsPlayback.plan(context, account, video, playRun.failed[video.id].orEmpty()).await()
            playRun.source = plan.source
            val source = withContext(Dispatchers.IO) { ShortsMedia.buildSource(context, plan) }
            player.setMediaSource(source, playRun.resumeMs)
            player.prepare()
            player.playWhenReady = !userPaused
            val frame = withTimeoutOrNull(FIRST_FRAME_TIMEOUT_MS) { snapshotFlow { readyId }.first { it == video.id } }
            if (frame == null) {
                recover()
                return@LaunchedEffect
            }
            // Đã lên hình: giờ mới tải trước video kế tiếp để không tranh băng thông với video đang xem.
            list.getOrNull(page + 1)?.let { ShortsPlayback.warm(context, account, it, bytes = true) }
            list.getOrNull(page + 2)?.let { ShortsPlayback.warm(context, account, it, bytes = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Không phát được video này."
        }
    }

    LaunchedEffect(page, items.size, account?.id) {
        val video = latest.getOrNull(page) ?: return@LaunchedEffect
        if (infos.containsKey(video.id)) return@LaunchedEffect
        try {
            infos[video.id] = ShortsPlayback.info(context, account, video).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Không biết kênh thì chỉ ẩn nút Đăng ký của video này.
        }
    }

    // Short đã lên hình là đã xem: lần mở app sau không đề xuất lại.
    LaunchedEffect(readyId) { ShortsSeen.add(readyId) }

    LaunchedEffect(readyId) {
        while (readyId != null) {
            val duration = player.duration
            progress = if (duration > 0) (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
            delay(100)
        }
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun like(video: VideoItem) {
        if (account == null) return toast("Hãy đăng nhập để thích video")
        val now = liked[video.id] != true
        liked[video.id] = now
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (items.isNotEmpty()) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { index -> items.getOrNull(index)?.id ?: index }
            ) { index ->
                val video = items[index]
                val current = index == page
                val info = infos[video.id]
                val channel = info?.toChannel()
                ShortPage(
                    video = video,
                    current = current,
                    player = player,
                    ready = current && readyId == video.id,
                    buffering = current && buffering,
                    error = if (current) error else null,
                    paused = userPaused,
                    vertical = vertical,
                    progress = progress,
                    bottomInset = bottomInset,
                    liked = liked[video.id] == true,
                    account = account,
                    info = info,
                    channel = channel,
                    subscribed = channel != null && ChannelSubscriptions.isSubscribed(account, channel),
                    onTap = {
                        userPaused = !userPaused
                        player.playWhenReady = !userPaused
                    },
                    onRetry = {
                        playRun.autoTries = 0
                        playRun.resumeMs = 0L
                        playRun.failed.remove(video.id)
                        ShortsPlayback.forget(video.id)
                        retry++
                    },
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
    onRefresh: (() -> Unit)?,
    refreshing: Boolean,
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
        if (onRefresh != null) RefreshButton(refreshing, onRefresh)
        TopIcon(YtSearch, onSearch)
        video?.let { VideoMoreButton(it, account, onSave) }
    }
}

@Composable
private fun RefreshButton(spinning: Boolean, onClick: () -> Unit) {
    val turn = rememberInfiniteTransition(label = "refresh")
    val angle by turn.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "angle"
    )
    Box(Modifier.size(44.dp).clip(CircleShape).plainClickable(onClick), contentAlignment = Alignment.Center) {
        Icon(YtRefresh, "Làm mới", Modifier.size(24.dp).graphicsLayer { rotationZ = if (spinning) angle else 0f }, tint = Color.White)
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
    player: ExoPlayer,
    ready: Boolean,
    buffering: Boolean,
    error: String?,
    paused: Boolean,
    vertical: Boolean,
    progress: Float,
    bottomInset: Boolean,
    liked: Boolean,
    account: Account?,
    info: ShortInfo?,
    channel: SearchResult.Channel?,
    subscribed: Boolean,
    onTap: () -> Unit,
    onRetry: () -> Unit,
    onLike: () -> Unit,
    onComments: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onSubscribe: () -> Unit
) {
    val context = LocalContext.current
    val meta = ShortsPlayback.meta(video.id)
    val inset = if (bottomInset) Modifier.navigationBarsPadding() else Modifier
    val author = info?.author ?: meta?.author ?: video.channel
    val channelId = info?.channelId ?: meta?.channelId ?: video.channelId
    val title = info?.title ?: meta?.title ?: video.title
    // Nút Đăng ký luôn có: hiện ngay khi đang tải thông tin kênh, chỉ ẩn khi đã hỏi xong mà vẫn không biết kênh.
    val showSubscribe = info == null || channel != null

    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }) {
        AsyncImage(
            model = video.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        if (current) {
            val holder = remember { arrayOfNulls<PlayerView>(1) }
            DisposableEffect(Unit) { onDispose { holder[0]?.player = null } }
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.shorts_player_view, null) as PlayerView).also {
                        holder[0] = it
                        it.player = player
                    }
                },
                update = {
                    it.resizeMode = if (vertical) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(180.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))))
        )

        if (current && ready && paused) {
            Icon(Lucide.Play, null, Modifier.align(Alignment.Center).size(64.dp), tint = Color(0xCCFFFFFF))
        }
        if (current && (!ready || buffering) && !paused && error == null) {
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
        Column(
            Modifier.align(Alignment.BottomEnd).then(inset).padding(end = 4.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ShortAction(if (liked) YtHeartFilled else YtHeart, info?.likes ?: "Thích", if (liked) LikedRed else Color.White, onLike)
            ShortAction(YtComment, info?.comments ?: "Bình luận", Color.White, onComments)
            ShortAction(YtBookmark, "Lưu", Color.White, onSave)
            ShortAction(YtShare, "Chia sẻ", Color.White, onShare)
        }

        // Kênh + Đăng ký + tiêu đề ở góc dưới trái (avatar / tên / nút nhỏ gọn như YouTube).
        Column(
            Modifier.align(Alignment.BottomStart).then(inset).fillMaxWidth(0.78f).padding(start = 12.dp, bottom = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelAvatar(
                    name = author,
                    size = 28.dp,
                    directUrl = info?.avatar ?: video.avatar,
                    channelId = channelId,
                    videoId = video.id,
                    fallbackLetter = title
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    author ?: "",
                    style = ShortShadow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (showSubscribe) {
                    Spacer(Modifier.width(8.dp))
                    if (subscribed && channel != null) {
                        // Đã đăng ký: chuông + mũi tên, bấm mở bảng Thông báo / Hủy đăng ký.
                        PillButton(
                            "Đã đăng ký", Color(0x33FFFFFF), Color.White,
                            leading = if (ChannelNotify.isOn(account, channel.id, channel.title)) BellRingIcon else BellOffIcon,
                            trailing = Lucide.ChevronDown,
                            onClick = onSubscribe
                        )
                    } else {
                        PillButton("Đăng ký", Color(0xFFF1F1F1), Color.Black, onClick = onSubscribe)
                    }
                }
            }
            if (title.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(title, style = ShortShadow, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        // Thanh tiến độ mảnh sát đáy.
        if (current && ready) {
            Box(Modifier.align(Alignment.BottomStart).then(inset).fillMaxWidth().height(2.dp).background(Color(0x55FFFFFF))) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color.White))
            }
        }
    }
}

@Composable
private fun ShortAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier.plainClickable(onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, Modifier.size(30.dp), tint = tint)
        Spacer(Modifier.height(3.dp))
        Text(label, style = ShortShadow, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun PillButton(
    text: String,
    background: Color,
    textColor: Color,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(background).plainClickable(onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.let { Icon(it, null, Modifier.size(14.dp), tint = textColor); Spacer(Modifier.width(5.dp)) }
        Text(text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        trailing?.let { Spacer(Modifier.width(2.dp)); Icon(it, null, Modifier.size(14.dp), tint = textColor) }
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
