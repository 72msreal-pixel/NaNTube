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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.nan.nantube.data.Account
import com.nan.nantube.data.HotSearches
import com.nan.nantube.data.SEARCH_FILTERS
import com.nan.nantube.data.SearchHistory
import com.nan.nantube.data.SearchResult
import com.nan.nantube.data.SearchSuggest
import com.nan.nantube.data.VideoItem
import com.nan.nantube.data.VideoRepository
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
            val i = arg.substringBefore('|').toIntOrNull() ?: 0
            VideoRepository.searchPage(arg.substringAfter('|'), SEARCH_FILTERS.getOrNull(i)?.params, account)
        },
        more = { account, token -> VideoRepository.searchMore(token, account) },
        keyOf = { it.key },
        emptyMessage = "Không tìm thấy kết quả nào."
    )

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
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var history by remember(ui.editing) { mutableStateOf(SearchHistory.load(context)) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    // Đề xuất khi ô trống: hiện ngay bản đã nhớ, rồi cập nhật top từ hot mới.
    var hot by remember { mutableStateOf(HotSearches.cached(context)) }
    LaunchedEffect(Unit) { hot = HotSearches.load(context) }
    var removing by remember { mutableStateOf<String?>(null) }

    fun submit(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        SearchHistory.add(context, q)
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

    // Kết quả tải theo khóa (tài khoản | bộ lọc | từ khóa): đổi chip lọc hay quay lại tab đều tự tải đúng.
    val loadKey = (account?.id ?: "") + "|" + ui.filter + "|" + ui.submitted
    LaunchedEffect(loadKey) {
        val q = ui.submitted
        if (q != null && ui.feed.loadedKey != loadKey) ui.feed.load(account, "${ui.filter}|$q")
    }

    // Gợi ý theo từ khóa đang gõ, đợi 150 ms sau phím cuối để khỏi gọi mạng từng chữ.
    val typed = ui.field.text
    LaunchedEffect(typed, ui.editing) {
        if (!ui.editing) return@LaunchedEffect
        val q = typed.trim()
        if (q.isEmpty()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(150)
        suggestions = SearchSuggest.suggestions(q)
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
            Modifier.fillMaxWidth().height(56.dp).padding(end = 8.dp),
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
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(NanColors.Surface)
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    if (typed.isEmpty()) Text("Tìm trên NaNTube", fontSize = 16.sp, color = NanColors.TextSecondary)
                    BasicTextField(
                        value = ui.field,
                        onValueChange = { ui.field = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
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
                        Modifier.size(44.dp).clip(CircleShape).clickable {
                            ui.field = TextFieldValue("")
                            ui.editing = true
                            try {
                                focusRequester.requestFocus()
                            } catch (_: Exception) {
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(IconClose, "Xóa chữ", Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            RoundIconButton(IconMic, "Tìm bằng giọng nói", background = NanColors.Surface) { startVoice() }
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
                scope.launch { ui.feed.load(account, "${ui.filter}|${ui.submitted.orEmpty()}") }
            }) {
                val listState = rememberLazyListState()
                LoadMoreEffect(listState, ui.feed.items.size) { ui.feed.loadMore(account) }
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(ui.feed.items, key = { it.key }) { r ->
                        when (r) {
                            is SearchResult.Video -> VideoCard(r.video, account, onSaveToPlaylist)
                            is SearchResult.Channel -> ChannelCard(r)
                            is SearchResult.Playlist -> PlaylistRow(r)
                        }
                    }
                    if (ui.feed.loadingMore) item { LoadingFooter() }
                }
            }
        }
    }
    }

    removing?.let { text ->
        RemoveHistoryDialog(
            text = text,
            onCancel = { removing = null },
            onConfirm = {
                SearchHistory.remove(context, text)
                history = SearchHistory.load(context)
                removing = null
            }
        )
    }
}

/** Hộp xác nhận khi giữ một từ trong Nhật ký, giống YouTube: tên từ khóa, "Xoá khỏi nhật ký tìm kiếm?", Hủy / Xóa. */
@Composable
private fun RemoveHistoryDialog(text: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = NanColors.Popup,
        shape = RoundedCornerShape(4.dp),
        title = { Text(text, fontSize = 24.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = { Text("Xoá khỏi nhật ký tìm kiếm?", fontSize = 18.sp) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Xóa", fontSize = 16.sp, fontWeight = FontWeight.Medium) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Hủy", fontSize = 16.sp, fontWeight = FontWeight.Medium) } }
    )
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, background: Color?, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(if (background != null) Modifier.background(background) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, Modifier.size(22.dp))
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
        val past = history.distinct().take(10)
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
        val all = own.map { SuggestRow.Item(it, 'h') } + fromServer.map { SuggestRow.Item(it, 's') }
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

private fun fullUrl(url: String?): String? = url?.let { if (it.startsWith("//")) "https:$it" else it }

/** Thẻ kênh trong kết quả: avatar, tên, @handle, số người đăng ký; "Xem kênh" mở trang kênh trên YouTube. */
@Composable
private fun ChannelCard(ch: SearchResult.Channel) {
    val context = LocalContext.current
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
        val pill = RoundedCornerShape(50)
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(pill)
                .border(1.dp, Color(0x66FFFFFF), pill)
                .clickable { openLink(context, ch.url) },
            contentAlignment = Alignment.Center
        ) {
            Text("Xem kênh", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
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
