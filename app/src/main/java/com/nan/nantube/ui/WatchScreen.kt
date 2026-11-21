package com.nan.nantube.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.nan.nantube.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class WatchRequest(val video: VideoItem, val queue: List<VideoItem>, val fromShorts: Boolean = false)

@Composable
internal fun WatchScreen(request: WatchRequest, account: Account?, onBack: () -> Unit, onSave: (VideoItem) -> Unit,
                         loadMoreShorts: (suspend () -> List<VideoItem>)? = null) {
    val first = request.video
    val queue = remember(request) {
        (if (request.queue.any { it.id == first.id }) request.queue.map { if (it.id == first.id) first else it }
        else listOf(first) + request.queue).filter { !first.isShort || it.isShort }.distinctBy { it.id }
    }
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize(), color = NanColors.Background, contentColor = Color.White) {
        if (first.isShort) {
            var pages by remember(request) { mutableStateOf(queue) }
            var pageError by remember(request) { mutableStateOf(false) }
            var retryPage by remember(request) { mutableIntStateOf(0) }
            val pager = rememberPagerState(initialPage = queue.indexOfFirst { it.id == first.id }.coerceAtLeast(0)) { pages.size }
            LaunchedEffect(pager.settledPage, retryPage) {
                if (loadMoreShorts != null && pager.settledPage >= pages.lastIndex - 1) {
                    pageError = false
                    try { pages = (pages + loadMoreShorts()).distinctBy { it.id } }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { pageError = true }
                }
            }
            Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
                WatchBar("Shorts", onBack)
                if (pageError) TextButton(onClick = { retryPage++ }) { Text("Thử tải thêm Shorts", color = Color.White) }
                VerticalPager(pager, modifier = Modifier.weight(1f), key = { pages[it].id }) { page ->
                    val video = pages[page]
                    if (page == pager.settledPage) {
                        key(video.id) { WatchItem(video, account, onSave, suggested = emptyList(), onOpen = {}, onEnded = {}, shorts = true) }
                    } else {
                        AsyncImage(video.thumbnail, video.title, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                WatchBar(if (first.musicTrack) "Đang phát · Music" else "NaNTube", onBack)
                key(first.id, first.offlineUri, first.musicTrack, account?.id) {
                    val open = LocalWatchOpen.current
                    val index = queue.indexOfFirst { it.id == first.id }
                    val next = queue.getOrNull(index + 1)
                    WatchItem(first, account, onSave,
                        suggested = queue.drop(index + 1),
                        onOpen = { open(it, if (queue.any { queued -> queued.id == it.id }) queue else listOf(it)) },
                        onEnded = { next?.let { open(it, queue) } },
                        onPrevious = queue.getOrNull(index - 1)?.let { previous -> { open(previous, queue) } },
                        onNext = next?.let { following -> { open(following, queue) } })
                }
            }
        }
    }
}

@Composable
private fun WatchBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, "Quay lại") }
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchItem(video: VideoItem, account: Account?, onSave: (VideoItem) -> Unit,
                      suggested: List<VideoItem>, onOpen: (VideoItem) -> Unit, onEnded: () -> Unit,
                      onPrevious: (() -> Unit)? = null, onNext: (() -> Unit)? = null, shorts: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var details by remember { mutableStateOf(WatchDetails()) }
    var loading by remember { mutableStateOf(video.offlineUri == null) }
    var failed by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var rating by remember { mutableStateOf<String?>(null) }
    var subscribed by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    BackHandler(enabled = expanded) { expanded = false }

    LaunchedEffect(refresh) {
        if (video.offlineUri != null) return@LaunchedEffect
        loading = true
        failed = false
        try {
            val info = WatchRepository.details(video, account)
            val fallback = if (info.description == null || info.channelId == null) try {
                WatchRepository.playerDetails(video, account)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { WatchDetails() } else WatchDetails()
            details = info.copy(title = info.title ?: fallback.title, channel = info.channel ?: fallback.channel,
                channelId = info.channelId ?: fallback.channelId, description = info.description ?: fallback.description)
            rating = info.rating
            subscribed = info.subscribed
        } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
        finally { loading = false }
    }

    fun message(text: String) { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    fun accountAction(action: suspend (Account) -> Unit) {
        if (account == null) { message("Đăng nhập trong tab Info để tương tác"); return }
        if (busy) return
        busy = true
        scope.launch {
            try { action(account) } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message(e.message ?: "Không thực hiện được thao tác") }
            finally { busy = false }
        }
    }
    val title = details.title ?: video.title
    val channel = details.channel ?: video.channel
    val subtitle = listOfNotNull(details.views ?: video.views, details.date).joinToString(" · ")
    val upNext = (suggested + details.suggested).distinctBy { it.id }.filterNot { it.id == video.id }
    fun share() {
        val url = if (video.musicTrack) "https://music.youtube.com/watch?v=${video.id}" else video.watchUrl
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        runCatching { context.startActivity(Intent.createChooser(send, "Chia sẻ")) }
    }
    fun rate(status: String) {
        val target = if (rating == status) "INDIFFERENT" else status
        accountAction { acc -> WatchRepository.rate(video, acc, target); rating = target }
    }

    Column(Modifier.fillMaxSize()) {
        val playerSize = when {
            expanded || shorts -> Modifier.weight(1f).fillMaxWidth()
            video.musicTrack -> Modifier.fillMaxWidth().aspectRatio(1.18f)
            else -> Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        }
        Box(playerSize) {
            WatchPlayback(video.copy(title = title, channel = channel), account, Modifier.fillMaxSize(), onEnded)
            if (shorts && video.offlineUri == null) {
                Column(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 56.dp),
                       horizontalAlignment = Alignment.End) {
                    WatchButton(if (rating == "LIKE") "✓ Thích" else "Thích", enabled = !busy) { rate("LIKE") }
                    WatchButton(if (rating == "DISLIKE") "✓ Không thích" else "Không thích", enabled = !busy) { rate("DISLIKE") }
                    WatchButton("Bình luận") { panel = "Bình luận" }
                    WatchButton("Chia sẻ") { share() }
                }
            }
        }
        if (expanded) {
            TextButton(onClick = { expanded = false }, modifier = Modifier.align(Alignment.End)) { Text("Thoát toàn màn hình", color = Color.White) }
        } else {
            val body = if (shorts) Modifier.fillMaxWidth().background(Color.Black)
            else Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            Column(body.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(title, color = Color.White, fontSize = if (shorts) 16.sp else 20.sp,
                    fontWeight = FontWeight.Bold, maxLines = if (shorts) 2 else 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { panel = "Mô tả" }.padding(vertical = 4.dp))
                if (subtitle.isNotBlank() && !shorts) Text(subtitle, color = NanColors.TextSecondary, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(channel.orEmpty(), Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val channelId = details.channelId
                    if (video.offlineUri == null && channelId != null) {
                        WatchButton(if (subscribed == true) "Đã đăng ký" else "Đăng ký", enabled = !busy, red = subscribed != true) {
                            val target = subscribed != true
                            accountAction { acc -> WatchRepository.subscribe(channelId, acc, target, details.subscribeParams); subscribed = target }
                        }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (video.offlineUri == null) {
                        if (!shorts) {
                            listOf("LIKE" to "Thích", "DISLIKE" to "Không thích").forEach { (status, label) ->
                                WatchButton(if (rating == status) "✓ $label" else label, enabled = !busy) { rate(status) }
                            }
                            WatchButton("Bình luận") { panel = "Bình luận" }
                        }
                        WatchButton("Lưu") { onSave(video.copy(title = title, channel = channel)) }
                    }
                    WatchButton("Mô tả") { panel = "Mô tả" }
                    if (!shorts || video.offlineUri != null) WatchButton("Chia sẻ") { share() }
                    if (!shorts) WatchButton("Toàn màn hình") { expanded = true }
                }
                if (onPrevious != null || onNext != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    onPrevious?.let { WatchButton("Trước", onClick = it) }
                    onNext?.let { WatchButton("Tiếp theo", onClick = it) }
                }
                if (failed) TextButton(onClick = { refresh++ }) { Text("Tải lại thông tin", color = Color.White) }
                if (!shorts && upNext.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = NanColors.Divider)
                    Text("Tiếp theo", fontWeight = FontWeight.Bold)
                    upNext.take(40).forEach { item ->
                        Row(Modifier.fillMaxWidth().clickable { onOpen(item) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(item.artwork ?: item.coverThumbnail, null, Modifier.width(116.dp).aspectRatio(16f / 9f), contentScale = ContentScale.Crop)
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(item.channel.orEmpty(), color = NanColors.TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    panel?.let { name ->
        ModalBottomSheet(onDismissRequest = { panel = null }, containerColor = NanColors.Background, contentColor = Color.White) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.74f).padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { panel = null }) { Text("Đóng", color = Color.White) }
                }
                HorizontalDivider(color = NanColors.Divider)
                when (name) {
                    "Mô tả" -> SelectionContainer {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                            Text(title, fontWeight = FontWeight.Bold)
                            if (subtitle.isNotBlank()) Text(subtitle, Modifier.padding(vertical = 8.dp), color = NanColors.TextSecondary)
                            Text(details.description ?: if (loading) "Đang tải…" else "Không có mô tả", Modifier.padding(top = 8.dp))
                        }
                    }
                    else -> if (loading) LinearProgressIndicator(color = Color.Red, modifier = Modifier.fillMaxWidth())
                    else CommentsBody(details.comments, account, details.commentsAsWeb)
                }
            }
        }
    }
}

@Composable
private fun WatchButton(text: String, enabled: Boolean = true, red: Boolean = false, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (red) Color(0xFFBC1717) else NanColors.Surface, contentColor = Color.White)) {
        Text(text, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun CommentsBody(token: String?, account: Account?, asWeb: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var thread by remember(token) { mutableStateOf<String?>(null) }
    val starting = thread ?: token
    var rows by remember(starting) { mutableStateOf(emptyList<WatchComment>()) }
    var more by remember(starting) { mutableStateOf(starting) }
    var loading by remember(starting) { mutableStateOf(false) }
    var failed by remember(starting) { mutableStateOf(false) }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun load() {
        val key = more ?: return
        if (loading) return
        loading = true
        failed = false
        job = scope.launch {
            try {
                val page = WatchRepository.comments(key, account, asWeb)
                rows = (rows + page.items).distinctBy { it.id }
                more = page.next?.takeUnless { it == key }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
            finally { loading = false }
        }
    }
    fun openThread(key: String?) { job?.cancel(); thread = key }
    LaunchedEffect(starting) { load() }
    if (thread != null) TextButton(onClick = { openThread(null) }) { Text("← Tất cả bình luận", color = Color.White) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(rows, key = { it.id }) { item ->
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(listOfNotNull(item.author, item.date).joinToString(" · "), color = NanColors.TextSecondary, fontSize = 12.sp)
                SelectionContainer { Text(item.text, modifier = Modifier.padding(top = 4.dp)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("Bình luận", item.text))
                            if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Đã sao chép bình luận", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Sao chép", color = Color.White) }
                    if (thread == null) item.replies?.let { replies -> TextButton(onClick = { openThread(replies) }) { Text("Phản hồi", color = Color.White) } }
                }
                HorizontalDivider(color = NanColors.Divider)
            }
        }
        item {
            when {
                loading -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.Red)
                failed -> TextButton(onClick = { load() }) { Text("Không tải được · Thử lại", color = Color.White) }
                more != null -> TextButton(onClick = { load() }) { Text("Tải thêm bình luận", color = Color.White) }
                rows.isEmpty() -> Text("Không có bình luận khả dụng", Modifier.padding(vertical = 16.dp), color = NanColors.TextSecondary)
            }
        }
    }
}
