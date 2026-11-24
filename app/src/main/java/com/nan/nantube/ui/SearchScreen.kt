package com.nan.nantube.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.nan.nantube.data.Account
import com.nan.nantube.data.ChannelAvatars
import com.nan.nantube.data.ChannelSubscriptions
import com.nan.nantube.data.HotSearches
import com.nan.nantube.data.ChannelNotify
import com.nan.nantube.data.Paged
import com.nan.nantube.data.SearchFilter
import com.nan.nantube.data.SEARCH_FILTERS
import com.nan.nantube.data.SearchHistory
import com.nan.nantube.data.SearchResult
import com.nan.nantube.data.SearchSuggest
import com.nan.nantube.data.VideoItem
import com.nan.nantube.data.VideoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Trạng thái màn hình tìm kiếm, giữ ở AppRoot để đổi tab qua lại không mất kết quả.
 * [editing] = đang gõ (hiện lịch sử / gợi ý); ngược lại hiện kết quả của [submitted].
 */
class SearchUi {
    var field by mutableStateOf(TextFieldValue(""))
    var submitted by mutableStateOf<String?>(null)
    var editing by mutableStateOf(true)
    var filter by mutableStateOf(0)

    /** Màn nghe giọng nói đang mở (vẽ ở AppRoot cho phủ cả thanh dưới) và câu nói nhận được, chờ màn tìm kiếm xử lý. */
    var voiceOpen by mutableStateOf(false)
    var voiceResult by mutableStateOf<String?>(null)

    val feed = PagedFeed<SearchResult>(
        first = { account, arg ->
            val chosen = SEARCH_FILTERS.getOrElse(arg.substringBefore('|').toIntOrNull() ?: 0) { SEARCH_FILTERS[0] }
            VideoRepository.searchPage(arg.substringAfter('|'), chosen.params, account).keeping(chosen)
        },
        more = { account, token -> VideoRepository.searchMore(token, account).keeping(SEARCH_FILTERS[filter]) },
        keyOf = { it.key },
        emptyMessage = "Không tìm thấy kết quả nào.",
        prepare = { list ->
            val videos = list.filterIsInstance<SearchResult.Video>().map { it.video }
            ChannelAvatars.prefetch(
                videos.map { ChannelAvatars.Request(it.channelId, it.channel, it.panelId, it.panelParams) },
                videos.map { it.avatar } + list.filterIsInstance<SearchResult.Channel>().map { it.avatar }
            )
        }
    )

    private fun Paged<SearchResult>.keeping(filter: SearchFilter) = Paged(items.filter(filter.keep), token)

    /** Mở trang tìm kiếm mới (bấm biểu tượng kính lúp): ô trống, sẵn sàng gõ. */
    fun startNew() {
        field = TextFieldValue("")
        submitted = null
        editing = true
        filter = 0
    }
}

@Composable
fun SearchScreen(ui: SearchUi, account: Account?, onBack: () -> Unit, onSaveToPlaylist: (VideoItem) -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    // Khách: nhật ký lưu trong máy. Đã đăng nhập: nhật ký của chính tài khoản đó trên YouTube.
    var localHistory by remember(ui.editing) { mutableStateOf(SearchHistory.load(context)) }
    var accountHistory by remember(account?.id) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(account?.id, ui.editing) {
        if (account != null && ui.editing) {
            accountHistory = SearchSuggest.accountHistory(account).take(SearchSuggest.MAX_SUGGESTIONS)
        }
    }
    val history = if (account != null) accountHistory else localHistory
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    // Đề xuất khi ô trống: hiện ngay bản đã nhớ, rồi cập nhật top từ hot mới.
    var hot by remember { mutableStateOf(if (account == null) HotSearches.cached(context) else emptyList()) }
    LaunchedEffect(account?.id) { hot = if (account == null) HotSearches.load(context) else emptyList() }
    var removing by remember { mutableStateOf<String?>(null) }

    fun submit(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        // Khách thì nhớ trong máy; tài khoản thì YouTube tự ghi khi tìm bằng token của tài khoản.
        if (account == null) SearchHistory.add(context, q)
        ui.field = TextFieldValue(q, TextRange(q.length))
        if (ui.submitted != q) ui.filter = 0
        ui.submitted = q
        ui.editing = false
        focus.clearFocus()
    }

    fun fill(text: String) {
        ui.field = TextFieldValue(text, TextRange(text.length))
        ui.editing = true
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val said = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!said.isNullOrBlank()) submit(said)
        }
    }

    fun startVoice() {
        // Có bộ nhận giọng nói trên máy: mở màn nghe riêng kiểu YouTube; không thì dùng hộp thoại của hệ thống.
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            focus.clearFocus()
            ui.voiceOpen = true // màn nghe phủ cả màn hình, vẽ ở AppRoot
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói để tìm kiếm")
        try {
            voice.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Máy chưa hỗ trợ tìm kiếm bằng giọng nói", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(ui.voiceResult) {
        ui.voiceResult?.let {
            ui.voiceResult = null
            submit(it)
        }
    }

    // Đổi chip lọc, từ khóa hay quay lại tab đều tự hiện đúng kết quả (đã tải rồi thì lấy lại ngay).
    LaunchedEffect(account?.id, ui.filter, ui.submitted) {
        ui.submitted?.let { ui.feed.load(account, "${ui.filter}|$it") }
    }

    // Gợi ý theo từ khóa đang gõ, đợi 150 ms sau phím cuối để khỏi gọi mạng từng chữ.
    val typed = ui.field.text
    LaunchedEffect(typed, ui.editing, account?.id) {
        if (!ui.editing) return@LaunchedEffect
        val q = typed.trim()
        if (q.isEmpty()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(150)
        suggestions = SearchSuggest.suggestions(q, account)
    }

    LaunchedEffect(ui.editing) {
        if (ui.editing) {
            delay(60)
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
                // Ô chưa gắn vào cây giao diện: người dùng chạm vào ô là được.
            }
        }
    }

    BackHandler(enabled = ui.editing && ui.submitted != null) {
        ui.editing = false
        focus.clearFocus()
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(IconArrowLeft, "Quay lại", background = null) {
                if (ui.editing && ui.submitted != null) {
                    ui.editing = false
                    focus.clearFocus()
                } else {
                    onBack()
                }
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(NanColors.Surface)
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    if (typed.isEmpty()) Text("Tìm trên NaNTube", fontSize = 15.sp, color = NanColors.TextSecondary)
                    BasicTextField(
                        value = ui.field,
                        onValueChange = { ui.field = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit(ui.field.text) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (it.isFocused && !ui.editing) ui.editing = true }
                    )
                }
                if (typed.isNotEmpty()) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).clickable {
                            ui.field = TextFieldValue("")
                            ui.editing = true
                            try {
                                focusRequester.requestFocus()
                            } catch (_: Exception) {
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(IconClose, "Xóa chữ", Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            RoundIconButton(IconMic, "Tìm bằng giọng nói", background = NanColors.Surface, size = 36.dp, iconSize = 20.dp) { startVoice() }
        }

        if (ui.editing || ui.submitted == null) {
            SuggestionList(
                query = typed,
                history = history,
                hot = hot,
                suggestions = suggestions,
                onPick = { submit(it) },
                onFill = { fill(it) },
                onAskRemoveHistory = { removing = it }
            )
        } else {
            ChipRow(SEARCH_FILTERS.map { it.label }, ui.filter) { ui.filter = it }
            PagedBody(ui.feed, onRetry = {
                ui.feed.load(account, "${ui.filter}|${ui.submitted.orEmpty()}", force = true)
            }) {
                LoadMoreEffect(ui.feed.listState, ui.feed.items.size) { ui.feed.loadMore(account) }
                YtPullRefresh(ui.feed.loading, onRefresh = {
                    ui.feed.load(account, "${ui.filter}|${ui.submitted.orEmpty()}", force = true, keep = true)
                }) {
                    LazyColumn(Modifier.fillMaxSize(), state = ui.feed.listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                        if (SEARCH_FILTERS[ui.filter].grid) {
                            shortRows(ui.feed.items.filterIsInstance<SearchResult.Video>().map { it.video }, account, onSaveToPlaylist)
                        } else {
                            val rows = groupShorts(ui.feed.items)
                            items(rows, key = { it.key }) { row ->
                                when (row) {
                                    is ResultRow.Shorts -> Row(
                                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        row.videos.forEach { ShortCard(it, account, onSaveToPlaylist, Modifier.weight(1f)) }
                                        if (row.videos.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                    is ResultRow.Single -> when (val r = row.result) {
                                        is SearchResult.Video -> VideoCard(r.video, account, onSaveToPlaylist)
                                        is SearchResult.Channel -> ChannelCard(r, account)
                                        is SearchResult.Playlist -> PlaylistRow(r)
                                    }
                                }
                            }
                        }
                        if (ui.feed.loadingMore) item { LoadingFooter() }
                    }
                }
            }
        }
    }
    }

    removing?.let { text ->
        if (account != null) {
            ConfirmDialog(
                title = "Nhật ký tìm kiếm",
                message = "Xóa toàn bộ nhật ký tìm kiếm của tài khoản này trên YouTube?",
                confirmLabel = "Xóa tất cả",
                onCancel = { removing = null },
                onConfirm = {
                    removing = null
                    scope.launch {
                        try {
                            SearchSuggest.clearAccountHistory(account)
                            accountHistory = emptyList()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Toast.makeText(context, e.shortMessage ?: "Không xóa được nhật ký", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        } else {
            ConfirmDialog(
                title = text,
                message = "Xoá khỏi nhật ký tìm kiếm?",
                confirmLabel = "Xóa",
                onCancel = { removing = null },
                onConfirm = {
                    SearchHistory.remove(context, text)
                    localHistory = SearchHistory.load(context)
                    removing = null
                }
            )
        }
    }
}

/** Hộp xác nhận kiểu YouTube: tiêu đề lớn, một dòng hỏi, Hủy / nút xác nhận. */
@Composable
private fun ConfirmDialog(title: String, message: String, confirmLabel: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = NanColors.Popup,
        shape = RoundedCornerShape(4.dp),
        title = { Text(title, fontSize = 24.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = { Text(message, fontSize = 18.sp) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, fontSize = 16.sp, fontWeight = FontWeight.Medium) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Hủy", fontSize = 16.sp, fontWeight = FontWeight.Medium) } }
    )
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    description: String,
    background: Color?,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (background != null) Modifier.background(background) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, Modifier.size(iconSize))
    }
}

private sealed interface SuggestRow {
    val key: String

    class Header(val title: String) : SuggestRow {
        override val key = "hd:$title"
    }

    /** [kind]: h = nhật ký (giữ để xóa), t = đề xuất hot, s = gợi ý của máy chủ khi đang gõ. */
    class Item(val text: String, val kind: Char) : SuggestRow {
        override val key = "$kind:$text"
    }
}

/**
 * Ô trống: mục "Nhật ký" (từ đã tìm, giữ để xóa) rồi mục "Đề xuất" (10 từ hot). Đang gõ: gợi ý (kèm vài mục nhật ký khớp).
 * Chạm = tìm ngay, mũi tên = chép vào ô.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestionList(
    query: String,
    history: List<String>,
    hot: List<String>,
    suggestions: List<String>,
    onPick: (String) -> Unit,
    onFill: (String) -> Unit,
    onAskRemoveHistory: (String) -> Unit
) {
    val q = query.trim()
    val rows: List<SuggestRow> = if (q.isEmpty()) {
        val past = history.distinct().take(SearchSuggest.MAX_SUGGESTIONS)
        val trending = hot.filter { h -> past.none { it.equals(h, ignoreCase = true) } }.take(HotSearches.COUNT)
        buildList {
            if (past.isNotEmpty()) {
                add(SuggestRow.Header("Nhật ký"))
                past.forEach { add(SuggestRow.Item(it, 'h')) }
            }
            if (trending.isNotEmpty()) {
                add(SuggestRow.Header("Đề xuất"))
                trending.forEach { add(SuggestRow.Item(it, 't')) }
            }
        }
    } else {
        val own = history.distinct().filter { it.contains(q, ignoreCase = true) && !it.equals(q, ignoreCase = true) }.take(3)
        val fromServer = suggestions.filter { s -> own.none { it.equals(s, ignoreCase = true) } }
        val all = (own.map { SuggestRow.Item(it, 'h') } + fromServer.map { SuggestRow.Item(it, 's') })
            .take(SearchSuggest.MAX_SUGGESTIONS)
        // Chưa có gợi ý (đang tải / mất mạng): vẫn cho tìm đúng chữ đang gõ.
        all.ifEmpty { listOf(SuggestRow.Item(q, 's')) }
    }

    LazyColumn(Modifier.fillMaxSize().imePadding()) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is SuggestRow.Header -> Text(
                    row.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NanColors.TextSecondary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)
                )
                is SuggestRow.Item -> SuggestItem(row, q, onPick, onFill, onAskRemoveHistory)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestItem(
    row: SuggestRow.Item,
    q: String,
    onPick: (String) -> Unit,
    onFill: (String) -> Unit,
    onAskRemoveHistory: (String) -> Unit
) {
    val text = row.text
    val isHistory = row.kind == 'h'
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .combinedClickable(
                onClick = { onPick(text) },
                onLongClick = if (isHistory) ({ onAskRemoveHistory(text) }) else null
            )
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isHistory) IconHistory else Lucide.Search,
            null,
            Modifier.size(22.dp),
            tint = NanColors.TextSoft
        )
        Spacer(Modifier.width(16.dp))
        val styled = buildAnnotatedString {
            if (q.isNotEmpty() && text.startsWith(q, ignoreCase = true)) {
                append(text.substring(0, q.length))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(q.length)) }
            } else {
                append(text)
            }
        }
        Text(styled, Modifier.weight(1f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(
            Modifier.size(52.dp).clickable { onFill(text) },
            contentAlignment = Alignment.Center
        ) {
            Icon(IconFillQuery, "Chép vào ô tìm kiếm", Modifier.size(22.dp), tint = NanColors.TextSoft)
        }
    }
}

/** Một hàng trong kết quả hỗn hợp: một mục thường, hoặc cặp Short đứng cạnh nhau (2 cột như YouTube). */
private sealed interface ResultRow {
    val key: String

    class Single(val result: SearchResult) : ResultRow {
        override val key get() = result.key
    }

    class Shorts(val videos: List<VideoItem>) : ResultRow {
        override val key get() = "s:" + videos.first().id
    }
}

/** Gom các Short liền nhau (giữ nguyên thứ tự YouTube trả về) thành từng cặp; mục khác giữ nguyên. */
private fun groupShorts(items: List<SearchResult>): List<ResultRow> {
    val out = ArrayList<ResultRow>()
    val run = ArrayList<VideoItem>()
    fun flush() {
        run.chunked(2).forEach { out.add(ResultRow.Shorts(it)) }
        run.clear()
    }
    for (r in items) {
        if (r is SearchResult.Video && r.video.isShort) {
            run.add(r.video)
        } else {
            flush()
            out.add(ResultRow.Single(r))
        }
    }
    flush()
    return out
}

private fun normName(s: String?) = s.orEmpty().trim().removePrefix("@").lowercase()

/** Thẻ kênh này có phải chính kênh của tài khoản đang dùng không (so @handle, không có handle thì so tên kênh). */
private fun isOwnChannel(account: Account?, ch: SearchResult.Channel): Boolean {
    if (account == null) return false
    val h1 = normName(account.handle)
    val h2 = normName(ch.handle)
    if (h1.isNotEmpty() && h2.isNotEmpty()) return h1 == h2
    val n1 = normName(account.name)
    return n1.isNotEmpty() && (n1 == normName(ch.title) || (h1.isNotEmpty() && h1 == normName(ch.title)))
}

private fun fullUrl(url: String?): String? = url?.let { if (it.startsWith("//")) "https:$it" else it }

/**
 * Thẻ kênh trong kết quả: avatar, tên, @handle, số người đăng ký. Có nút Đăng ký / Đã đăng ký (bấm Đã đăng ký để hủy)
 * khi kết quả kèm nút đăng ký; "Xem kênh" mở trang kênh trên YouTube.
 */
@Composable
private fun ChannelCard(ch: SearchResult.Channel, account: Account?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var showNotify by remember { mutableStateOf(false) }
    val subscribed = ChannelSubscriptions.isSubscribed(account, ch)
    val notifyOn = ChannelNotify.isOn(account, ch.id, ch.title)

    fun setSubscribed(subscribe: Boolean) {
        if (account == null) {
            Toast.makeText(context, "Hãy đăng nhập để đăng ký kênh", Toast.LENGTH_SHORT).show()
            return
        }
        busy = true
        scope.launch {
            try {
                ChannelSubscriptions.set(account, ch, subscribe)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, e.shortMessage ?: "Không đổi được trạng thái đăng ký", Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(NanColors.Surface)
            .clickable { openLink(context, ch.url) }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelAvatar(ch.title, 68.dp, directUrl = ch.avatar, channelId = ch.id)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(ch.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ch.handle?.let { Text(it, fontSize = 14.sp, color = NanColors.TextSecondary, maxLines = 1) }
                ch.info?.let { Text(it, fontSize = 14.sp, color = NanColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ch.subscribed != null && !isOwnChannel(account, ch)) {
                CardButton(
                    Modifier.weight(1f),
                    if (subscribed) "Đã đăng ký" else "Đăng ký",
                    filled = if (subscribed) Color(0x33FFFFFF) else Color(0xFFF1F1F1),
                    textColor = if (subscribed) Color.White else Color.Black,
                    enabled = !busy,
                    leading = if (subscribed) (if (notifyOn) BellRingIcon else BellOffIcon) else null,
                    trailing = if (subscribed) Lucide.ChevronDown else null
                ) { if (subscribed) showNotify = true else setSubscribed(true) }
            }
            CardButton(Modifier.weight(1f), "Xem kênh", outlined = true) { openLink(context, ch.url) }
        }
    }

    if (showNotify) {
        NotificationSheet(
            ch = ch,
            account = account,
            onDismiss = { showNotify = false },
            onUnsubscribe = { showNotify = false; setSubscribed(false) }
        )
    }
}

// ---- Bảng "Thông báo" khi bấm nút Đã đăng ký ---------------------------------------------------
// Chỉ Bật / Tắt, lưu trên máy (xem ChannelNotify); NewUploadsWorker dựa vào đây để bắn thông báo video mới.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationSheet(ch: SearchResult.Channel, account: Account?, onDismiss: () -> Unit, onUnsubscribe: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val on = ChannelNotify.isOn(account, ch.id, ch.title)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NanColors.Popup,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text("Thông báo", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                "Nhận thông báo khi kênh này tải video mới lên.",
                fontSize = 15.sp,
                color = NanColors.TextSecondary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp)
            )
            SheetOptionRow(BellRingIcon, "Nhận thông báo", selected = on, enabled = true) {
                ChannelNotify.set(account, ch.id, ch.title, true)
                onDismiss()
            }
            SheetOptionRow(BellOffIcon, "Không nhận thông báo", selected = !on, enabled = true) {
                ChannelNotify.set(account, ch.id, ch.title, false)
                onDismiss()
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(NanColors.Divider))
            SheetOptionRow(UserXIcon, "Hủy đăng ký", selected = false, enabled = true, onClick = onUnsubscribe)
        }
    }
}

@Composable
private fun SheetOptionRow(icon: ImageVector, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(26.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, fontSize = 18.sp, modifier = Modifier.weight(1f))
        if (selected) Icon(Lucide.Check, "Đang chọn", Modifier.size(26.dp))
    }
}

/** Nút tròn 40dp trong thẻ kênh: [filled] nền đặc, [outlined] chỉ viền. */
@Composable
private fun CardButton(
    modifier: Modifier,
    label: String,
    filled: Color? = null,
    textColor: Color = Color.White,
    outlined: Boolean = false,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    onClick: () -> Unit
) {
    val pill = RoundedCornerShape(50)
    Row(
        modifier
            .height(40.dp)
            .clip(pill)
            .then(if (filled != null) Modifier.background(filled) else Modifier)
            .then(if (outlined) Modifier.border(1.dp, Color(0x66FFFFFF), pill) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.let { Icon(it, null, Modifier.size(20.dp), tint = textColor); Spacer(Modifier.width(6.dp)) }
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
        trailing?.let { Spacer(Modifier.width(4.dp)); Icon(it, null, Modifier.size(18.dp), tint = textColor) }
    }
}

/** Danh sách phát trong kết quả: ảnh bìa + số video bên trái, tên và kênh bên phải. */
@Composable
private fun PlaylistRow(pl: SearchResult.Playlist) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { openLink(context, pl.url) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(
            Modifier
                .width(168.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(NanColors.Surface)
        ) {
            fullUrl(pl.thumbnail)?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            pl.count?.let {
                Text(
                    it,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(pl.title, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            pl.channel?.let {
                Text(it, fontSize = 12.sp, color = NanColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            Text("Danh sách phát", fontSize = 12.sp, color = NanColors.TextSecondary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
