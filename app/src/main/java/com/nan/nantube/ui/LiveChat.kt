package com.nan.nantube.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nan.nantube.data.Account
import com.nan.nantube.data.ChatBatch
import com.nan.nantube.data.ChatEmoji
import com.nan.nantube.data.ChatMessage
import com.nan.nantube.data.ChatPanelEvent
import com.nan.nantube.data.ChatPoll
import com.nan.nantube.data.ChatPollChoice
import com.nan.nantube.data.ChatRole
import com.nan.nantube.data.ChatRun
import com.nan.nantube.data.LiveChatRepo
import com.nan.nantube.data.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_MESSAGES = 400
private val MemberGreen = Color(0xFF2BA640)
private val ModeratorBlue = Color(0xFF5E84F1)
private val NameGray = Color(0xFFAAAAAA)
private val HeartPink = Color(0xFFFF2D6F)
private val FALLBACK_EMOJI = listOf(
    "😀", "😂", "😍", "🥰", "😎", "🤔", "😭", "😡", "👍", "👏", "🙏", "🔥", "❤️", "💯", "🎉", "😮", "😴", "🤣", "👀", "💀", "✨"
)

/**
 * Ô "Trò chuyện trực tiếp" thay cho thẻ Bình luận khi xem buổi phát trực tiếp: thanh nhập kiểu YouTube
 * ("Trò chuyện công khai bằng tên …"), chạm để mở khung chat.
 */
@Composable
internal fun LiveChatCard(account: Account?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NanColors.Popup)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = 14.dp)
    ) {
        Text("Trò chuyện trực tiếp", fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(top = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            ChatAvatar(account, 24)
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(NanColors.Surface)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(chatHint(account), fontSize = 12.sp, lineHeight = 15.sp, color = NanColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Khung chat trực tiếp như YouTube: tiêu đề + "Tin nhắn hàng đầu" + số người xem, tin nhắn thật (có emoji của YouTube / chủ kênh),
 * tin nhắn ghim, nút cuộn xuống, ô nhập kèm bộ chọn emoji, bảng chọn. Đọc / gửi / bình chọn theo tài khoản đang chọn (chưa đăng nhập thì chỉ đọc).
 */
@Composable
internal fun LiveChatContent(video: VideoItem, account: Account?, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember(video.id) { mutableStateListOf<ChatMessage>() }
    var pinned by remember(video.id) { mutableStateOf<ChatMessage?>(null) }
    var groups by remember(video.id) { mutableStateOf<List<Pair<String, List<ChatEmoji>>>>(emptyList()) }
    var restricted by remember(video.id) { mutableStateOf<String?>(null) }
    var poll by remember(video.id) { mutableStateOf<ChatPoll?>(null) }
    var status by remember(video.id) { mutableStateOf<String?>("Đang tải trò chuyện…") }

    var sendParams by remember(video.id, account?.id) { mutableStateOf<String?>(null) }
    var sending by remember(video.id) { mutableStateOf(false) }
    var voting by remember(video.id) { mutableStateOf(false) }
    val seen = remember(video.id) { HashSet<String>() }
    fun apply(batch: ChatBatch) {
        if (batch.removedIds.isNotEmpty()) messages.removeAll { it.id in batch.removedIds }
        for (m in batch.messages) if (seen.add(m.id)) messages.add(m)
        while (messages.size > MAX_MESSAGES) messages.removeAt(0)
        batch.pinned?.let { pinned = it }
        // Khung hành động theo đúng thứ tự YouTube gửi: bảng chọn mở / cập nhật / đóng, ô nhập đổi chế độ giữa chừng.
        for (e in batch.panelEvents) when (e) {
            is ChatPanelEvent.ShowPoll -> poll = e.poll
            is ChatPanelEvent.ClosePanel -> poll?.let { p -> if (e.panelId == null || p.panelId == null || p.panelId == e.panelId) poll = null }
            is ChatPanelEvent.Restricted -> {
                restricted = e.text
                if (e.sendParams != null) sendParams = e.sendParams
            }
        }
        if (messages.isNotEmpty()) status = null
    }

    LaunchedEffect(video.id, account?.id) {
        try {
            val start = LiveChatRepo.start(video.id, account)
            groups = start.emojiGroups
            restricted = start.restricted
            apply(start.batch)
            // Đã đọc theo tài khoản thì các lượt sau cũng theo tài khoản; bị từ chối (hết phiên) thì rơi về ẩn danh.
            var authed = start.authed
            if (messages.isEmpty()) status = "Chưa có tin nhắn nào."
            var next = start.batch.continuation
            var wait = start.batch.waitMs
            var failures = 0
            while (next != null) {
                delay(wait)
                try {
                    val batch = try {
                        LiveChatRepo.poll(next, account, authed)
                    } catch (e: com.nan.nantube.data.AuthRejected) {
                        authed = false
                        LiveChatRepo.poll(next, null, false)
                    }
                    apply(batch)
                    next = batch.continuation
                    wait = batch.waitMs
                    failures = 0
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    if (++failures >= 4) break
                    wait = 4_000L
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (messages.isEmpty()) status = e.message ?: "Không tải được trò chuyện."
        }
    }

    val listState = rememberLazyListState()
    val atBottom by remember { derivedStateOf { val info = listState.layoutInfo; (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 2 } }
    // Đang bám đáy (tin mới tự cuộn) cho tới khi người dùng tự kéo lên.
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to atBottom }.collect { (scrolling, bottom) ->
            if (scrolling) follow = bottom else if (bottom) follow = true
        }
    }
    LaunchedEffect(messages.size) {
        if (follow && messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    var text by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var emojiOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    // Cửa sổ mặc định (adjustUnspecified) tự "pan" cả màn hình lên khi bàn phím che ô nhập, làm cả khung chat bay lên
    // và lệch với số đo bên dưới. Trong lúc mở chat live thì đặt adjustResize để hệ thống không pan nữa (trả lại như cũ khi đóng).
    DisposableEffect(view) {
        val window = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>().firstOrNull()?.window
        val old = window?.attributes?.softInputMode
        window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { if (window != null && old != null) window.setSoftInputMode(old) }
    }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    // Như VideoChat/ShortChat: khung giữ nguyên chỗ, tự đo phần bàn phím che đáy khung rồi chỉ chừa đúng phần đó,
    // nên chỉ thanh nhập nhích lên trên bàn phím còn danh sách tin nhắn co lại (không cả khung bay lên).
    var contentBottom by remember { mutableIntStateOf(0) }
    val overlapPx = if (imeBottom > 0 && contentBottom > 0) (contentBottom - (view.rootView.height - imeBottom)).coerceAtLeast(0) else 0
    val overlapDp = with(density) { overlapPx.toDp() }
    var imeWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(imeBottom > 0) {
        val open = imeBottom > 0
        if (open) emojiOpen = false
        // Đóng bàn phím (nút ⌄ / vuốt xuống / Back): trả ô nhập về dạng thu gọn, trừ khi đang chuyển sang bảng emoji.
        else if (imeWasOpen && !emojiOpen) focusManager.clearFocus()
        imeWasOpen = open
    }
    // Danh sách vừa co lại do bàn phím mở thì giữ bám đáy để tin mới nhất không bị che.
    LaunchedEffect(overlapPx > 0) {
        if (follow && messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }
    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    // Mã gõ emoji (":ten:") → emoji, để gửi đúng đoạn emoji thay vì chữ.
    val emojiByShortcut = remember(groups) { groups.flatMap { it.second }.associateBy { it.shortcut } }
    fun trySend() {
        val message = text.trim()
        if (message.isEmpty() || sending) return
        val me = account
        val params = sendParams
        when {
            me == null -> toast("Đăng nhập để trò chuyện")
            params == null -> toast("Tài khoản này không thể trò chuyện ở buổi phát này")
            else -> {
                sending = true
                scope.launch {
                    try {
                        apply(LiveChatRepo.send(me, params, message, emojiByShortcut))
                        text = ""
                        follow = true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        toast(e.message ?: "Không gửi được tin nhắn")
                    } finally {
                        sending = false
                    }
                }
            }
        }
    }
    fun vote(choice: ChatPollChoice) {
        val me = account
        val params = choice.voteParams
        when {
            voting -> Unit
            me == null -> toast("Đăng nhập để bình chọn")
            params == null -> toast("Không bình chọn được (cần đăng nhập hoặc bảng chọn đã đóng)")
            else -> {
                voting = true
                scope.launch {
                    try {
                        apply(LiveChatRepo.vote(me, params))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        toast(e.message ?: "Không bình chọn được")
                    } finally {
                        voting = false
                    }
                }
            }
        }
    }
    fun toggleEmoji() {
        if (emojiOpen) {
            emojiOpen = false
            focus.requestFocus()
            keyboard?.show()
        } else {
            emojiOpen = true
            focus.requestFocus()
            keyboard?.hide()
        }
    }

    val viewers = remember(video.views) { video.views?.let { Regex("^[\\d.,]+(?:\\s(?:N|K|Tr|M))?").find(it)?.value } }

    Box(
        Modifier.fillMaxSize().onGloballyPositioned { c ->
            // Toạ độ trong cửa sổ: cùng hệ với số đo của bàn phím (tính từ đáy cửa sổ).
            contentBottom = (c.positionInWindow().y + c.size.height).toInt()
        }
    ) {
    Column(
        Modifier.fillMaxSize().then(
            if (imeBottom > 0) Modifier.padding(bottom = overlapDp) else Modifier.windowInsetsPadding(WindowInsets.navigationBars)
        )
    ) {
        // Đầu khung: tiêu đề + "Tin nhắn hàng đầu · số người", điểm kinh nghiệm, bộ lọc, đóng.
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Trò chuyện trực tiếp", fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tin nhắn hàng đầu", fontSize = 13.sp, color = NanColors.TextSecondary)
                    if (viewers != null) {
                        Spacer(Modifier.width(14.dp))
                        Icon(ChatUserIcon, null, Modifier.size(16.dp), tint = NanColors.TextSecondary)
                        Spacer(Modifier.width(4.dp))
                        Text(viewers, fontSize = 13.sp, color = NanColors.TextSecondary)
                    }
                }
            }
            if (account != null) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(NanColors.Surface).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(ChatCrownIcon, null, Modifier.size(18.dp), tint = Color.White)
                    Spacer(Modifier.width(5.dp))
                    Text("0 XP", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.width(6.dp))
            }
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { }, contentAlignment = Alignment.Center) {
                Icon(ChatSlidersIcon, "Cài đặt trò chuyện", Modifier.size(24.dp), tint = Color.White)
            }
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(IconClose, "Đóng", Modifier.size(26.dp), tint = Color.White)
            }
        }
        HorizontalDivider(color = NanColors.Divider)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                Text(
                    status ?: "", color = NanColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(vertical = 4.dp)) {
                    itemsIndexed(messages, key = { _, m -> m.id }) { _, m -> ChatRow(m) }
                }
            }
            if (!follow && messages.isNotEmpty()) {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).size(38.dp).clip(CircleShape).background(Color(0xFFF1F1F1))
                        .clickable {
                            follow = true
                            scope.launch { if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex) }
                        },
                    contentAlignment = Alignment.Center
                ) { Icon(ChatArrowDownIcon, "Xuống cuối", Modifier.size(22.dp), tint = Color(0xFF0F0F0F)) }
            }
            // Nút thả tim (làm màu như YouTube, chưa gửi cảm xúc thật).
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 8.dp).size(50.dp).clip(CircleShape)
                    .background(Color(0xFF0F0F0F)).border(BorderStroke(1.dp, Color(0x40FFFFFF)), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(ChatHeartIcon, "Thả tim", Modifier.size(30.dp), tint = HeartPink) }
        }

        pinned?.let { p ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).clip(RoundedCornerShape(50)).background(NanColors.Surface)
                    .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PersonAvatar(p.photo, p.author, 24)
                Spacer(Modifier.width(10.dp))
                ChatText(p, Modifier.weight(1f), maxLines = 1, fontSize = 14.sp)
                Icon(WatchMore, "Tin nhắn ghim", Modifier.size(24.dp), tint = Color.White)
            }
        }
        HorizontalDivider(color = NanColors.Divider)

        // Bảng chọn của chủ kênh nằm ngay trên ô nhập như YouTube; ẩn khi đang gõ để dành chỗ cho tin nhắn.
        val activePoll = poll
        if (activePoll != null && imeBottom == 0 && !emojiOpen) {
            ChatPollCard(activePoll, onChoose = ::vote)
        }
        val restrictedText = restricted
        if (restrictedText != null) {
            // Chủ kênh chỉ cho một nhóm người chat (vd chỉ người đăng ký).
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(ChatInfoIcon, null, Modifier.size(24.dp), tint = NanColors.TextSecondary)
                Spacer(Modifier.width(14.dp))
                Text(restrictedText, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Box(Modifier.size(width = 52.dp, height = 40.dp).clip(RoundedCornerShape(14.dp)).background(NanColors.Surface), contentAlignment = Alignment.Center) {
                    Icon(ChatBanknoteIcon, null, Modifier.size(24.dp), tint = Color.White)
                }
            }
        } else {
            val typing = focused || emojiOpen
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (typing) {
                    ChatAvatar(account, 30)
                    Spacer(Modifier.width(10.dp))
                }
                Box(
                    Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(50)).background(NanColors.Surface)
                        .padding(start = 16.dp, end = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            BasicTextField(
                                value = text,
                                onValueChange = { text = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                                cursorBrush = SolidColor(Color.White),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { trySend() }),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused },
                                decorationBox = { inner ->
                                    if (text.isEmpty()) Text(if (typing) "Trò chuyện…" else "Nhập tin nhắn…", fontSize = 15.sp, color = NanColors.TextSecondary)
                                    inner()
                                }
                            )
                        }
                        Box(Modifier.size(36.dp).clip(CircleShape).clickable { toggleEmoji() }, contentAlignment = Alignment.Center) {
                            Icon(ChatSmileIcon, "Emoji", Modifier.size(26.dp), tint = Color.White)
                        }
                    }
                }
                if (typing) {
                    Box(Modifier.padding(start = 6.dp).size(42.dp).clip(CircleShape).clickable { trySend() }, contentAlignment = Alignment.Center) {
                        Icon(ChatSendIcon, "Gửi", Modifier.size(26.dp), tint = if (text.isBlank() || sending) NanColors.TextSecondary else Color.White)
                    }
                }
            }
            if (emojiOpen) {
                HorizontalDivider(color = NanColors.Divider)
                Column(Modifier.fillMaxWidth().height(290.dp)) {
                    LazyVerticalGrid(GridCells.Fixed(7), Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
                        if (groups.isEmpty()) {
                            items(FALLBACK_EMOJI) { e ->
                                Box(Modifier.aspectRatio(1f).clip(CircleShape).clickable { text += e }, contentAlignment = Alignment.Center) {
                                    Text(e, fontSize = 26.sp)
                                }
                            }
                        } else {
                            groups.forEach { (title, list) ->
                                if (title.isNotBlank()) item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(title, fontSize = 13.sp, color = NanColors.TextSecondary, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp))
                                }
                                items(list) { e ->
                                    Box(Modifier.aspectRatio(1f).clip(CircleShape).clickable { text += e.shortcut + " " }.padding(8.dp)) {
                                        AsyncImage(e.url, e.shortcut, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = NanColors.Divider)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).clickable { toggleEmoji() }, contentAlignment = Alignment.Center) {
                            Icon(ChatKeyboardIcon, "Bàn phím", Modifier.size(28.dp), tint = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).clickable {
                                // Xoá nguyên mã emoji (":ten:") đứng cuối, không có thì xoá một ký tự.
                                val tail = Regex(":[^:\\s]+: ?$").find(text)
                                text = if (tail != null) text.removeRange(tail.range) else text.dropLast(1)
                            },
                            contentAlignment = Alignment.Center
                        ) { Icon(ChatBackspaceIcon, "Xoá", Modifier.size(28.dp), tint = Color.White) }
                    }
                }
            }
        }
    }
    }
}

/** Thẻ bảng chọn như YouTube: người đăng + "x phút trước • N lượt bình chọn", câu hỏi, các lựa chọn (có kết quả thì hiện thanh % tô nền). */
@Composable
private fun ChatPollCard(poll: ChatPoll, onChoose: (ChatPollChoice) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp)).background(NanColors.Popup)
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(poll.avatar, poll.meta ?: "?", 36)
            Spacer(Modifier.width(10.dp))
            Text(poll.meta ?: "", fontSize = 14.sp, color = NanColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(ChatMoreVerticalIcon, "Thêm", Modifier.size(24.dp), tint = Color.White)
        }
        Text(poll.question, fontSize = 17.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 14.dp, bottom = 12.dp))
        poll.choices.forEach { c ->
            val showResult = c.percent != null && c.ratio >= 0f
            Box(
                Modifier.padding(bottom = 8.dp).fillMaxWidth().height(46.dp).clip(RoundedCornerShape(50))
                    .background(NanColors.Surface).clickable { onChoose(c) }
            ) {
                if (showResult) Box(
                    Modifier.fillMaxHeight().fillMaxWidth(c.ratio.coerceIn(0.02f, 1f))
                        .background(if (c.selected) Color(0xFF3EA6FF).copy(alpha = 0.45f) else Color(0x33FFFFFF))
                )
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (showResult) Text(c.percent ?: "", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    else Box(Modifier.size(24.dp).border(BorderStroke(1.5.dp, NanColors.TextSecondary), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun ChatRow(m: ChatMessage) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.Top) {
        PersonAvatar(m.photo, m.author, 26)
        Spacer(Modifier.width(10.dp))
        ChatText(m, Modifier.weight(1f))
    }
}

/**
 * Tên + dấu tích dùng chung [ChatNameTag] với VideoChat/ShortChat (chủ kênh trong viên sáng, tích xác minh giống nhau),
 * kèm huy hiệu điều hành / thành viên và nội dung có emoji chèn ngay trong dòng chữ.
 */
@Composable
private fun ChatText(m: ChatMessage, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, fontSize: androidx.compose.ui.unit.TextUnit = 15.sp) {
    val emojiSize = (fontSize.value * 1.4f).sp
    val byOwner = m.role == ChatRole.OWNER
    // Cùng logic tích với bình luận: cờ VERIFIED của YouTube, hoặc tra kênh theo mã kênh (chủ kênh thường không kèm cờ).
    val verified = rememberVerified(m.id, m.verified, m.channelId)
    val nameColor = when (m.role) {
        ChatRole.MODERATOR -> ModeratorBlue
        ChatRole.MEMBER -> MemberGreen
        else -> NameGray
    }
    val tagSize = (fontSize.value * 0.87f).sp
    val tagLine = (fontSize.value * 1.07f).sp
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val built = remember(m.id, verified) {
        val inlines = HashMap<String, InlineTextContent>()
        // Placeholder phải biết trước bề rộng: đo tên rồi cộng phần đệm của viên sáng và tích.
        val nameStyle = TextStyle(fontSize = tagSize, lineHeight = tagLine, fontWeight = FontWeight.Medium)
        val nameWidthPx = measurer.measure(m.author, nameStyle, maxLines = 1).size.width
        val extraDp = (if (byOwner) 9 else 0) + (if (verified) 16 else 0)
        val width = with(density) { (nameWidthPx + (extraDp + 1).dp.toPx()).toSp() }
        val height = if (byOwner) with(density) { 17.dp.toSp() } else tagLine
        inlines["tag"] = InlineTextContent(Placeholder(width, height, PlaceholderVerticalAlign.Center)) {
            ChatNameTag(m.author, byOwner, verified, color = nameColor, fontSize = tagSize, lineHeight = tagLine, fontWeight = FontWeight.Medium)
        }
        val line = buildAnnotatedString {
            appendInlineContent("tag", m.author)
            if (m.role == ChatRole.MODERATOR) {
                append(" ")
                inlines["mod"] = badge { Icon(ChatShieldIcon, null, Modifier.fillMaxSize(), tint = ModeratorBlue) }
                appendInlineContent("mod", "🛡")
            }
            m.badgeUrl?.let { url ->
                append(" ")
                inlines["member"] = badge { AsyncImage(url, null, Modifier.fillMaxSize()) }
                appendInlineContent("member", "★")
            }
            append("  ")
            m.amount?.let { withStyle(SpanStyle(color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)) { append("$it  ") } }
            m.runs.forEachIndexed { i, run ->
                when (run) {
                    is ChatRun.Text -> append(run.text)
                    is ChatRun.Emoji -> {
                        inlines["e$i"] = InlineTextContent(Placeholder(emojiSize, emojiSize, PlaceholderVerticalAlign.TextCenter)) {
                            AsyncImage(run.emoji.url, run.emoji.shortcut, Modifier.fillMaxSize())
                        }
                        appendInlineContent("e$i", run.emoji.shortcut)
                    }
                }
            }
        }
        line to inlines
    }
    Text(built.first, modifier, fontSize = fontSize, lineHeight = (fontSize.value * 1.3f).sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis, inlineContent = built.second)
}

private fun badge(content: @Composable () -> Unit) =
    InlineTextContent(Placeholder(15.sp, 15.sp, PlaceholderVerticalAlign.TextCenter)) { content() }

/** Ảnh đại diện người chat; chưa có ảnh thì chữ cái đầu trên nền màu theo tên. */
@Composable
private fun PersonAvatar(url: String?, name: String, sizeDp: Int) {
    val palette = listOf(0xFFD84315, 0xFF2E7D32, 0xFF1565C0, 0xFF6A1B9A, 0xFFAD1457, 0xFF00838F)
    val tint = Color(palette[(name.hashCode() and 0x7fffffff) % palette.size])
    Box(Modifier.size(sizeDp.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
        Text(name.trimStart('@').take(1).uppercase(), fontSize = (sizeDp / 2).sp, fontWeight = FontWeight.Medium, color = Color.White)
        url?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

private fun chatHint(account: Account?): String =
    if (account == null) "Đăng nhập để trò chuyện…" else "Trò chuyện công khai bằng tên ${account.name}…"

@Composable
private fun ChatAvatar(account: Account?, sizeDp: Int) {
    Box(
        Modifier.size(sizeDp.dp).clip(CircleShape).background(Color(0xFF8E2A5B)),
        contentAlignment = Alignment.Center
    ) {
        Text((account?.name ?: "?").trimStart('@').take(1).uppercase(), fontSize = (sizeDp / 2).sp, fontWeight = FontWeight.Medium)
        account?.photo?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

// Icon vẽ tay theo nét Lucide (viewBox 24), khỏi phụ thuộc tên icon của thư viện.
private fun chatIcon(name: String, filled: Boolean, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { d ->
            if (filled) addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
            else addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()

private val ChatCrownIcon by lazy { chatIcon("ChatCrown", false, "m2 4 3 12h14l3-12-6 7-4-7-4 7-6-7z", "M5 20h14") }
private val ChatSlidersIcon by lazy {
    chatIcon("ChatSliders", false, "M21 4h-7", "M10 4H3", "M21 12h-9", "M8 12H3", "M21 20h-5", "M12 20H3", "M14 2v4", "M8 10v4", "M16 18v4")
}
private val ChatUserIcon by lazy { chatIcon("ChatUser", true, "M12 12a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9z", "M3 21v-1.5C3 16.5 7 15 12 15s9 1.5 9 4.5V21z") }
private val ChatSmileIcon by lazy {
    chatIcon("ChatSmile", false, "M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0", "M8 14s1.5 2 4 2 4-2 4-2", "M9 9h.01", "M15 9h.01")
}
private val ChatSendIcon by lazy {
    chatIcon("ChatSend", false, "M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z", "m21.854 2.147-10.94 10.939")
}
private val ChatArrowDownIcon by lazy { chatIcon("ChatArrowDown", false, "M12 5v14", "m19 12-7 7-7-7") }
private val ChatHeartIcon by lazy {
    chatIcon("ChatHeart", true, "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z")
}
private val ChatInfoIcon by lazy { chatIcon("ChatInfo", false, "M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0", "M12 16v-4", "M12 8h.01") }
private val ChatBanknoteIcon by lazy {
    chatIcon("ChatBanknote", false, "M4 6h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z", "M14 12a2 2 0 1 1-4 0 2 2 0 0 1 4 0", "M6 12h.01", "M18 12h.01")
}
private val ChatKeyboardIcon by lazy {
    chatIcon(
        "ChatKeyboard", false, "M2 6a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2z",
        "M6 8h.01", "M10 8h.01", "M14 8h.01", "M18 8h.01", "M8 12h.01", "M12 12h.01", "M16 12h.01", "M7 16h10"
    )
}
private val ChatBackspaceIcon by lazy {
    chatIcon("ChatBackspace", false, "M10 5a2 2 0 0 0-1.344.519l-6.328 5.74a1 1 0 0 0 0 1.481l6.328 5.741A2 2 0 0 0 10 19h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2z", "m12 9 6 6", "m18 9-6 6")
}
private val ChatMoreVerticalIcon by lazy { chatIcon("ChatMoreVertical", false, "M12 5h.01", "M12 12h.01", "M12 19h.01") }
private val ChatShieldIcon by lazy {
    chatIcon("ChatShield", true, "M12 2 4 5v6c0 5 3.4 9.4 8 11 4.6-1.6 8-6 8-11V5l-8-3z")
}
