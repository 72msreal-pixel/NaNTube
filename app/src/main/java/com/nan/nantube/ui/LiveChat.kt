package com.nan.nantube.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import com.nan.nantube.data.ChatSendException
import com.nan.nantube.data.isMemberOnlyRestriction
import com.nan.nantube.data.isSubscriberOnlyRestriction
import com.nan.nantube.data.LiveChatRepo
import com.nan.nantube.data.LoginKind
import com.nan.nantube.data.AccountStore
import com.nan.nantube.data.InnerTube
import com.nan.nantube.data.VideoItem
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_MESSAGES = 400
private val MemberGreen = Color(0xFF2BA640)
private val ModeratorBlue = Color(0xFF5E84F1)
private val NameGray = Color(0xFFAAAAAA)
private val HeartPink = Color(0xFFFF2D6F)

// Tạm tắt 3 tính năng của khung chat trực tiếp; đổi thành true để bật lại.
private const val SHOW_CHAT_XP = false      // viên thuốc "0 XP" ở đầu khung
private const val SHOW_CHAT_HEART = false   // nút thả tim nổi góc dưới phải
private const val SHOW_CHAT_POLL = false    // thẻ bình chọn của chủ kênh
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
internal fun LiveChatContent(video: VideoItem, account: Account?, channelId: String? = video.channelId, subscribed: Boolean = false, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember(video.id) { mutableStateListOf<ChatMessage>() }
    var pinned by remember(video.id) { mutableStateOf<ChatMessage?>(null) }
    var groups by remember(video.id) { mutableStateOf<List<Pair<String, List<ChatEmoji>>>>(emptyList()) }
    var restricted by remember(video.id) { mutableStateOf<String?>(null) }
    var poll by remember(video.id) { mutableStateOf<ChatPoll?>(null) }
    var status by remember(video.id) { mutableStateOf<String?>("Đang tải trò chuyện…") }
    // Đọc lại khung chat khi đổi trạng thái đăng ký kênh hoặc khi người dùng chạm dòng "chỉ dành cho người đăng ký".
    var reloadKey by remember(video.id) { mutableIntStateOf(0) }
    // Gửi thử mà YouTube vẫn từ chối vì hạn chế (vd kênh đòi đăng ký đủ lâu): thôi tự mở khóa, tin YouTube.
    var serverLocked by remember(video.id, account?.id) { mutableStateOf(false) }
    // Kênh bật "chỉ người đăng ký" và tài khoản này đã đăng ký: ô nhập hiện "Trò chuyện với tư cách người đăng ký…" như YouTube.
    var subscriberMode by remember(video.id, account?.id) { mutableStateOf(false) }

    var sendParams by remember(video.id, account?.id) { mutableStateOf<String?>(null) }
    var sending by remember(video.id) { mutableStateOf(false) }
    var voting by remember(video.id) { mutableStateOf(false) }
    val seen = remember(video.id) { HashSet<String>() }
    // Tin của mình tự dựng lúc gửi (YouTube không echo lại): bản thật về qua lượt đọc thì thay vào, không hiện đôi.
    val echoIds = remember(video.id) { HashSet<String>() }
    fun apply(batch: ChatBatch) {
        if (batch.removedIds.isNotEmpty()) messages.removeAll { it.id in batch.removedIds }
        for (m in batch.messages) if (seen.add(m.id)) {
            messages.removeAll { it.id in echoIds && it.author == m.author && it.text == m.text }
            messages.add(m)
        }
        while (messages.size > MAX_MESSAGES) messages.removeAt(0)
        batch.pinned?.let { pinned = it }
        // Khung hành động theo đúng thứ tự YouTube gửi: bảng chọn mở / cập nhật / đóng, ô nhập đổi chế độ giữa chừng.
        for (e in batch.panelEvents) when (e) {
            is ChatPanelEvent.ShowPoll -> poll = e.poll.after(poll)
            is ChatPanelEvent.ClosePanel -> poll?.let { p -> if (e.panelId == null || p.panelId == null || p.panelId == e.panelId) poll = null }
            // Tự kiểm tra thay YouTube: kênh đòi "chỉ người đăng ký" mà tài khoản này đã đăng ký kênh thì mở khóa luôn.
            // Chỉ chế độ dành riêng cho thành viên (hoặc lần gửi trước bị YouTube từ chối) mới giữ khóa.
            is ChatPanelEvent.Restricted -> {
                val unlocked = account != null && subscribed && !serverLocked && !isMemberOnlyRestriction(e.text)
                restricted = if (unlocked) null else e.text
                subscriberMode = unlocked && isSubscriberOnlyRestriction(e.text)
            }
            is ChatPanelEvent.Input -> {
                restricted = null
                subscriberMode = e.subscriberOnly
                if (e.sendParams != null) sendParams = e.sendParams
            }
        }
        if (messages.isNotEmpty()) status = null
    }

    // Tài khoản mã (YouTube TV) không chat live được: cần phiên web cùng email. Có rồi thì chat đi bằng phiên web đó.
    val needsWeb = remember(account?.id, AccountStore.accounts.size, reloadKey) {
        account != null && account.kind == LoginKind.CODE && LiveChatRepo.webTwin(account) == null
    }
    LaunchedEffect(video.id, account?.id, subscribed, reloadKey, needsWeb) {
        try {
            val start = LiveChatRepo.start(video.id, account)
            groups = start.emojiGroups
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
    val inputScroll = rememberScrollState()
    LaunchedEffect(text) { inputScroll.scrollTo(inputScroll.maxValue) }
    var field by remember { mutableStateOf(anchored("")) }
    var focused by remember { mutableStateOf(false) }
    var emojiOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
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
    // Vào chế độ hạn chế thì thanh nhập biến mất: hạ bàn phím, đóng bảng emoji.
    LaunchedEffect(restricted != null) {
        if (restricted != null) {
            emojiOpen = false
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    fun trySend() {
        val message = text.trim()
        if (message.isEmpty() || sending) return
        val me = account
        // Như meetube: mã tự dựng từ kênh + video luôn có sẵn làm dự phòng cho mã YouTube kèm trong ô nhập.
        val localParams = channelId?.let { LiveChatRepo.localSendParams(it, video.id) }
        when {
            me == null -> toast("Đăng nhập để trò chuyện")
            sendParams == null && localParams == null -> toast("Chưa biết kênh của buổi phát này nên chưa gửi được")
            else -> {
                sending = true
                scope.launch {
                    try {
                        val sent = LiveChatRepo.send(me, sendParams, localParams, message, emojiByShortcut)
                        apply(sent)
                        if (sent.messages.isEmpty()) {
                            val echo = ChatMessage(
                                id = "local-" + InnerTube.randomId(12), author = me.name, photo = me.photo,
                                runs = splitInput(message, emojiByShortcut).runs, role = ChatRole.NORMAL, verified = false, badgeUrl = null
                            )
                            echoIds += echo.id
                            messages.add(echo)
                        }
                        text = ""
                        follow = true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ChatSendException) {
                        // Chủ kênh bật chế độ chỉ người đăng ký / thành viên: hiện dòng thông báo và ẩn thanh nhập.
                        if (e.restricted) { serverLocked = true; restricted = e.message }
                        toast(e.message ?: "Không gửi được tin nhắn")
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
        val current = poll ?: return
        val params = choice.voteParams
        when {
            voting || current.voted -> Unit
            me == null -> toast("Đăng nhập để bình chọn")
            params == null -> toast("Không bình chọn được (cần đăng nhập hoặc bảng chọn đã đóng)")
            else -> {
                voting = true
                // Hiện lựa chọn ngay như YouTube (YouTube không luôn echo lại); lỗi thì trả bảng về như cũ.
                poll = current.copy(choices = current.choices.map { it.copy(selected = it.voteParams == params) })
                scope.launch {
                    try {
                        apply(LiveChatRepo.vote(me, params))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (poll?.id == current.id) poll = current
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
                Text("Trò chuyện trực tiếp", fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tin nhắn hàng đầu", fontSize = 12.sp, color = NanColors.TextSecondary)
                    if (viewers != null) {
                        Spacer(Modifier.width(14.dp))
                        Icon(ChatUserIcon, null, Modifier.size(15.dp), tint = NanColors.TextSecondary)
                        Spacer(Modifier.width(4.dp))
                        Text(viewers, fontSize = 12.sp, color = NanColors.TextSecondary)
                    }
                }
            }
            if (SHOW_CHAT_XP && account != null) {
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
            Box(Modifier.size(38.dp).clip(CircleShape).clickable { }, contentAlignment = Alignment.Center) {
                Icon(ChatSlidersIcon, "Cài đặt trò chuyện", Modifier.size(21.dp), tint = Color.White)
            }
            Box(Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(IconClose, "Đóng", Modifier.size(23.dp), tint = Color.White)
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
            if (SHOW_CHAT_HEART) Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 8.dp).size(50.dp).clip(CircleShape)
                    .background(Color(0xFF0F0F0F)).border(BorderStroke(1.dp, Color(0x40FFFFFF)), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(ChatHeartIcon, "Thả tim", Modifier.size(30.dp), tint = HeartPink) }
        }
        // Thanh ghim: bấm để mở/thu; vuốt/ném xuống thì ẩn (không kéo cao hơn vị trí mặc định); mở lại khung chat thì hiện lại.
        val pinnedMsg = pinned
        var pinnedDismissed by remember(video.id, pinnedMsg?.id) { mutableStateOf(false) }
        var pinnedExpanded by remember(video.id, pinnedMsg?.id) { mutableStateOf(false) }
        val pinnedOffset = remember { Animatable(0f) }
        LaunchedEffect(pinnedMsg?.id) { pinnedOffset.snapTo(0f) }
        if (pinnedMsg != null && !pinnedDismissed) {
            val dismissPx = with(density) { 36.dp.toPx() }
            val flyPx = with(density) { 120.dp.toPx() }
            val dragState = rememberDraggableState { d -> scope.launch { pinnedOffset.snapTo((pinnedOffset.value + d).coerceAtLeast(0f)) } }
            // Khe ghim cố định chiều cao: thanh bị kéo xuống thì chui dưới vạch kẻ (cắt cứng), không đè lên ô nhập.
            Box(Modifier.fillMaxWidth().clipToBounds()) {
            Row(
                Modifier.offset { IntOffset(0, pinnedOffset.value.roundToInt()) }
                    .fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(if (pinnedExpanded) 18.dp else 24.dp)).background(NanColors.Surface)
                    .draggable(
                        state = dragState, orientation = Orientation.Vertical,
                        onDragStopped = { v ->
                            if (pinnedOffset.value > dismissPx || v > 1800f) {
                                pinnedOffset.animateTo(pinnedOffset.value + flyPx)
                                pinnedDismissed = true
                            } else pinnedOffset.animateTo(0f)
                        }
                    )
                    .clickable { pinnedExpanded = !pinnedExpanded }
                    .animateContentSize()
                    .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = if (pinnedExpanded) Alignment.Top else Alignment.CenterVertically
            ) {
                PersonAvatar(pinnedMsg.photo, pinnedMsg.author, 24)
                Spacer(Modifier.width(8.dp))
                ChatText(pinnedMsg, Modifier.weight(1f), maxLines = if (pinnedExpanded) Int.MAX_VALUE else 1)
                Icon(ChatMoreHorizontalIcon, "Tin nhắn ghim", Modifier.size(24.dp), tint = Color.White)
            }
            }
        }
        HorizontalDivider(color = NanColors.Divider)

        // Bảng chọn của chủ kênh nằm ngay trên ô nhập như YouTube; ẩn khi đang gõ để dành chỗ cho tin nhắn.
        val activePoll = poll
        if (SHOW_CHAT_POLL && activePoll != null && imeBottom == 0 && !emojiOpen) {
            ChatPollCard(activePoll, onChoose = ::vote)
        }
        val restrictedText = restricted
        if (needsWeb && account != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(ChatInfoIcon, null, Modifier.size(24.dp), tint = NanColors.TextSecondary)
                Spacer(Modifier.width(14.dp))
                Text(
                    "Tính năng này không hoạt động với tài khoản đăng nhập bằng mã. Bạn cần đăng nhập bằng cách tự nhập Gmail.",
                    fontSize = 14.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White)
                        .clickable { WebSignInRequest.open(account.email) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) { Text("Đăng nhập", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black) }
            }
        } else if (restrictedText != null) {
            // Chủ kênh chỉ cho một nhóm người chat (vd chỉ người đăng ký).
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(ChatInfoIcon, null, Modifier.size(24.dp), tint = NanColors.TextSecondary)
                Spacer(Modifier.width(14.dp))
                Text(
                    restrictedText, fontSize = 15.sp,
                    modifier = Modifier.weight(1f).clickable { serverLocked = false; restricted = null; reloadKey++ }
                )
                // Nút donate (Super Chat) đã bỏ.
            }
        } else {
            val typing = focused || emojiOpen
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (typing) {
                    ChatAvatar(account, 30)
                    Spacer(Modifier.width(10.dp))
                }
                Box(
                    Modifier.weight(1f).height(35.dp).clip(RoundedCornerShape(50)).background(NanColors.Surface)
                        .padding(start = 16.dp, end = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val input = splitInput(text, emojiByShortcut)
                        LaunchedEffect(input.draft, field.text) {
                            if (field.text != INPUT_ANCHOR + input.draft) field = anchored(input.draft)
                        }
                        Row(
                            Modifier.weight(1f).horizontalScroll(inputScroll).plainClickable { focus.requestFocus() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            input.head.forEach { run ->
                                when (run) {
                                    is ChatRun.Text -> Text(run.text, fontSize = 14.sp, maxLines = 1, softWrap = false)
                                    is ChatRun.Emoji -> AsyncImage(run.emoji.url, run.emoji.shortcut, Modifier.padding(horizontal = 1.dp).size(20.dp))
                                }
                            }
                            BasicTextField(
                                value = field,
                                onValueChange = {
                                    field = it
                                    text = typedText(text, input, it.text, emojiByShortcut)
                                },
                                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                cursorBrush = SolidColor(Color.White),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { trySend() }),
                                modifier = Modifier
                                    .width(IntrinsicSize.Min)
                                    .widthIn(min = 8.dp)
                                    .focusRequester(focus)
                                    .onFocusChanged { focused = it.isFocused },
                                decorationBox = { inner ->
                                    if (text.isEmpty()) {
                                        Text(
                                            when {
                                                subscriberMode -> "Trò chuyện với tư cách người đăng ký…"
                                                typing -> "Trò chuyện…"
                                                else -> "Nhập tin nhắn…"
                                            },
                                            fontSize = 14.sp, color = NanColors.TextSecondary, maxLines = 1, softWrap = false)
                                    }
                                    inner()
                                }
                            )
                        }
                        Box(Modifier.size(32.dp).clip(CircleShape).clickable { toggleEmoji() }, contentAlignment = Alignment.Center) {
                            Icon(ChatSmileIcon, "Emoji", Modifier.size(22.dp), tint = Color.White)
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
                                    Box(
                                        Modifier.height(EMOJI_CELL_HEIGHT).clip(CircleShape).clickable { text += e.shortcut + " " },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(e.url, e.shortcut, Modifier.size(EMOJI_ICON_SIZE), contentScale = ContentScale.Fit)
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
                            Modifier.size(44.dp).clip(CircleShape).clickable { text = deleteLast(text, emojiByShortcut) },
                            contentAlignment = Alignment.Center
                        ) { Icon(ChatBackspaceIcon, "Xoá", Modifier.size(28.dp), tint = Color.White) }
                    }
                }
            }
        }
    }
    }
}

private val EMOJI_CELL_HEIGHT = 44.dp
private val EMOJI_ICON_SIZE = 28.dp

/** Ký tự rộng 0 đứng đầu ô gõ: bị xoá nghĩa là người dùng bấm xoá khi ô gõ đã trống, lúc đó xoá luôn emoji đứng trước. */
private const val INPUT_ANCHOR = "\u200B"

private fun anchored(draft: String) = TextFieldValue(INPUT_ANCHOR + draft, TextRange(INPUT_ANCHOR.length + draft.length))

private val EMOJI_TOKEN = Regex(":[^:\\s]+: ?")

/** Nội dung ô nhập tách làm hai: [head] là phần tới hết emoji cuối (hiện thành hình), [draft] là chữ đang gõ phía sau. */
private class InputSplit(val head: List<ChatRun>, val draft: String, val lastToken: IntRange?) {
    val runs: List<ChatRun> get() = if (draft.isEmpty()) head else head + ChatRun.Text(draft)
}

private fun splitInput(text: String, emojis: Map<String, ChatEmoji>): InputSplit {
    val head = ArrayList<ChatRun>()
    var cursor = 0
    var lastToken: IntRange? = null
    for (match in EMOJI_TOKEN.findAll(text)) {
        val emoji = emojis[match.value.trim()] ?: continue
        if (match.range.first > cursor) head.add(ChatRun.Text(text.substring(cursor, match.range.first)))
        head.add(ChatRun.Emoji(emoji))
        cursor = match.range.last + 1
        lastToken = match.range
    }
    return InputSplit(head, text.substring(cursor), lastToken)
}

/** Xoá nguyên emoji đứng cuối khi chưa gõ chữ sau nó, không thì xoá một ký tự. */
private fun deleteLast(text: String, emojis: Map<String, ChatEmoji>): String {
    val input = splitInput(text, emojis)
    val token = input.lastToken
    return if (input.draft.isEmpty() && token != null) text.removeRange(token) else text.dropLast(1)
}

/** Nhận chữ người dùng vừa gõ vào ô (đã kèm [INPUT_ANCHOR]) và trả về toàn bộ nội dung mới của tin nhắn. */
private fun typedText(text: String, input: InputSplit, typed: String, emojis: Map<String, ChatEmoji>): String = when {
    INPUT_ANCHOR in typed -> text.removeSuffix(input.draft) + typed.replace(INPUT_ANCHOR, "")
    input.draft.isEmpty() -> deleteLast(text, emojis)
    else -> text
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
            // YouTube chỉ cho xem kết quả sau khi người xem đã chọn: chưa chọn thì bản cập nhật kết quả gửi chung cũng không được lộ ra.
            val showResult = poll.voted && c.percent != null && c.ratio >= 0f
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
                    else if (c.selected) Box(Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF3EA6FF)), contentAlignment = Alignment.Center) {
                        Icon(Lucide.Check, null, Modifier.size(16.dp), tint = Color.Black)
                    } else Box(Modifier.size(24.dp).border(BorderStroke(1.5.dp, NanColors.TextSecondary), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun ChatRow(m: ChatMessage) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.Top) {
        PersonAvatar(m.photo, m.author, 24)
        Spacer(Modifier.width(8.dp))
        ChatText(m, Modifier.weight(1f))
    }
}

/**
 * Tên + dấu tích dùng chung [ChatNameTag] với VideoChat/ShortChat (chủ kênh trong viên sáng, tích xác minh giống nhau),
 * kèm huy hiệu điều hành / thành viên và nội dung có emoji chèn ngay trong dòng chữ.
 */
@Composable
private fun ChatText(m: ChatMessage, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, fontSize: androidx.compose.ui.unit.TextUnit = 13.5.sp) {
    // Luôn hiện đủ tên (không cắt).
    val author = m.author
    val emojiSize = (fontSize.value * 1.4f).sp
    val byOwner = m.role == ChatRole.OWNER
    // Cùng logic tích với bình luận: cờ VERIFIED của YouTube, hoặc tra kênh theo mã kênh (chủ kênh thường không kèm cờ).
    val verified = rememberVerified(m.id, m.verified, m.channelId)
    val nameColor = when (m.role) {
        ChatRole.MODERATOR -> ModeratorBlue
        ChatRole.MEMBER -> MemberGreen
        else -> NameGray
    }
    val tagSize = fontSize
    val tagLine = (fontSize.value * 1.07f).sp
    val density = LocalDensity.current
    val baseStyle = LocalTextStyle.current
    val measurer = rememberTextMeasurer()
    val built = remember(m.id, verified) {
        val inlines = HashMap<String, InlineTextContent>()
        // Placeholder phải biết trước bề rộng: đo tên rồi cộng phần đệm của viên sáng và tích.
        val nameStyle = TextStyle(fontSize = tagSize, lineHeight = tagLine, fontWeight = FontWeight.Medium)
        val nameWidthPx = measurer.measure(author, baseStyle.merge(nameStyle), maxLines = 1).size.width
        val extraDp = (if (byOwner) 9 else 0) + (if (verified) 16 else 0) + 3
        val width = with(density) { (nameWidthPx + (extraDp + 1).dp.toPx()).toSp() }
        val height = if (byOwner) with(density) { 17.dp.toSp() } else tagLine
        inlines["tag"] = InlineTextContent(Placeholder(width, height, PlaceholderVerticalAlign.Center)) {
            ChatNameTag(author, byOwner, verified, color = nameColor, fontSize = tagSize, lineHeight = tagLine, fontWeight = FontWeight.Medium, overflow = TextOverflow.Visible)
        }
        val line = buildAnnotatedString {
            appendInlineContent("tag", author)
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
                    is ChatRun.Text -> {
                        // Link xanh gạch chân, link dài rút gọn "…" như YouTube.
                        var last = 0
                        for (mt in UrlRegex.findAll(run.text)) {
                            append(run.text.substring(last, mt.range.first))
                            val u = mt.value
                            withStyle(SpanStyle(color = Color(0xFF3EA6FF), textDecoration = TextDecoration.Underline)) {
                                append(if (u.length > 38) u.take(37) + "…" else u)
                            }
                            last = mt.range.last + 1
                        }
                        append(run.text.substring(last))
                    }
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

private val UrlRegex = Regex("https?://\\S+")

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
private val ChatMoreHorizontalIcon by lazy { chatIcon("ChatMoreHorizontal", false, "M5 12h.01", "M12 12h.01", "M19 12h.01") }
private val ChatMoreVerticalIcon by lazy { chatIcon("ChatMoreVertical", false, "M12 5h.01", "M12 12h.01", "M12 19h.01") }
private val ChatShieldIcon by lazy {
    chatIcon("ChatShield", true, "M12 2 4 5v6c0 5 3.4 9.4 8 11 4.6-1.6 8-6 8-11V5l-8-3z")
}
