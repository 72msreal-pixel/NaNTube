package com.nan.nantube.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.nan.nantube.data.SavedComments
import com.nan.nantube.data.Account
import com.nan.nantube.data.CommentItem
import com.nan.nantube.data.CommentSort
import com.nan.nantube.data.CommentsRepository
import com.nan.nantube.data.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Một bình luận trong cây: bình luận gốc có độ sâu 0, phản hồi 1, phản hồi của phản hồi 2... */
class CommentNode(val item: CommentItem, val depth: Int, val rootId: String) {
    var liked by mutableStateOf(item.liked)
    var disliked by mutableStateOf(item.disliked)
    var children by mutableStateOf<List<CommentNode>>(emptyList())
    var loaded by mutableStateOf(false)
    var loading by mutableStateOf(false)

    /** Token của "Phản hồi khác" (trang phản hồi kế); null = hết. */
    var moreToken by mutableStateOf<String?>(null)

    /** Còn phản hồi chưa mở (hiện "N phản hồi"). */
    val hasUnopened get() = !loaded && item.repliesToken != null
}

/** Chỉ thụt vào tối đa tới cấp này (gốc 0 → phản hồi 1 → phản hồi của phản hồi 2); sâu hơn thì xếp phẳng cùng cấp 2, không thu nhỏ thêm. */
private const val MAX_NEST = 2
private const val CACHE_MAX_AGE_MS = 15 * 60_000L

/**
 * Giữ bình luận của vài video gần nhất trong bộ nhớ: đóng bảng rồi mở lại thì hiện ngay đúng chỗ đang đọc
 * (cả các phản hồi đã mở), không tải lại.
 */
class CommentsSession {
    val nodes = mutableStateListOf<CommentNode>()
    var token by mutableStateOf<String?>(null)
    var total by mutableStateOf<String?>(null)
    var sorts by mutableStateOf<List<CommentSort>>(emptyList())
    var disabled by mutableStateOf(false)
    var loaded = false
    var loadedAt = 0L
    var scrollIndex = 0
    var scrollOffset = 0
}

object CommentsCache {
    private val map = object : LinkedHashMap<String, CommentsSession>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CommentsSession>?) = size > 6
    }

    fun get(key: String): CommentsSession {
        val old = map[key]
        if (old != null && old.loaded && System.currentTimeMillis() - old.loadedAt > CACHE_MAX_AGE_MS) map.remove(key)
        return map.getOrPut(key) { CommentsSession() }
    }
}
private val Line = Color(0xFF4A4A4A)
private val LineWidth = 0.8.dp
private val Emojis = listOf("❤️", "😂", "🎉", "😢", "😮", "😅", "😊")

private fun avatarSize(depth: Int): Dp = if (depth == 0) 30.dp else 20.dp

/**
 * Dựng cây từ danh sách phẳng: [CommentItem.level] cho biết cấp, mục cấp N nằm dưới mục cấp N-1 gần nhất phía trước.
 * Danh sách chỉ toàn một cấp (phản hồi lấy theo từng luồng) thì ra danh sách phẳng.
 */
private fun buildNodes(items: List<CommentItem>, depth: Int, rootId: String): List<CommentNode> {
    val base = items.minOfOrNull { it.level } ?: 0
    val out = ArrayList<CommentNode>()
    val stack = ArrayList<CommentNode>()
    for (item in items) {
        val level = item.level - base
        while (stack.size > level) stack.removeAt(stack.lastIndex)
        val node = CommentNode(item, minOf(depth + level, MAX_NEST), rootId)
        var parent = stack.lastOrNull()
        // Cha đã ở cấp tối đa: xếp phẳng ngang hàng với cha (dưới ông bà), không thụt thêm.
        if (parent != null && parent.depth >= MAX_NEST) {
            parent = stack.lastOrNull { it.depth < MAX_NEST }
        }
        if (parent == null) out.add(node) else {
            parent.children = parent.children + node
            parent.loaded = true
        }
        stack.add(node)
    }
    return out
}

private fun topNodes(items: List<CommentItem>) = items.map { CommentNode(it, 0, it.id) }

/** Nội dung bảng bình luận của Short: tiêu đề + sắp xếp, cây bình luận, ô nhập. Nằm trong khung kéo được của Short. */
@Composable
fun CommentsContent(video: VideoItem, account: Account?, imeOverlapPx: Int = 0, onSave: (VideoItem) -> Unit = {}, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val session = remember(video.id, account?.id) { CommentsCache.get(video.id + "|" + (account?.id ?: "")) }
    val nodes = session.nodes
    val listState = rememberLazyListState(session.scrollIndex, session.scrollOffset)
    DisposableEffect(session) {
        onDispose {
            session.scrollIndex = listState.firstVisibleItemIndex
            session.scrollOffset = listState.firstVisibleItemScrollOffset
        }
    }
    var token by remember(session) { mutableStateOf(session.token) }
    var total by remember(session) { mutableStateOf(session.total) }
    var sorts by remember(session) { mutableStateOf(session.sorts) }
    var sortOpen by remember { mutableStateOf(false) }
    var loading by remember(session) { mutableStateOf(!session.loaded) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var disabled by remember(session) { mutableStateOf(session.disabled) }
    var replyTo by remember { mutableStateOf<CommentNode?>(null) }
    var text by remember { mutableStateOf(TextFieldValue()) }
    var sending by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val view = androidx.compose.ui.platform.LocalView.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    var contentBottom by remember { mutableIntStateOf(0) }
    val overlapPx = if (imeBottom > 0 && contentBottom > 0) (contentBottom - (view.rootView.height - imeBottom)).coerceAtLeast(0) else 0
    // Đóng bàn phím (nút ⌄ / vuốt xuống / Back): trả ô nhập về dạng thu gọn mặc định, bỏ chế độ trả lời nếu chưa gõ gì.
    var imeWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(imeBottom > 0) {
        val open = imeBottom > 0
        if (imeWasOpen && !open) {
            focusManager.clearFocus()
            if (text.text.isBlank() || Regex("^@\\S+$").matches(text.text.trim())) {
                text = TextFieldValue()
                replyTo = null
            }
        }
        imeWasOpen = open
    }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    fun load(sortToken: String? = null) {
        loading = true
        error = null
        scope.launch {
            try {
                val page = if (sortToken != null) CommentsRepository.more(sortToken, account) else CommentsRepository.first(video.id, account)
                nodes.clear()
                nodes.addAll(topNodes(page.items))
                token = page.token
                total = page.total ?: total
                if (page.sorts.isNotEmpty()) sorts = page.sorts
                if (page.items.isEmpty()) error = "Chưa có bình luận nào."
                session.token = token
                session.total = total
                session.sorts = sorts
                session.loaded = true
                session.loadedAt = System.currentTimeMillis()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                disabled = nodes.isEmpty()
                session.disabled = disabled
                error = e.shortMessage ?: "Không tải được bình luận."
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(session) { if (!session.loaded) load() }

    fun loadMore() {
        val t = token ?: return
        if (loadingMore) return
        loadingMore = true
        scope.launch {
            try {
                val page = CommentsRepository.more(t, account)
                val seen = nodes.map { it.item.id }.toHashSet()
                nodes.addAll(topNodes(page.items.filter { seen.add(it.id) }))
                token = page.token?.takeIf { page.items.isNotEmpty() }
                session.token = token
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                loadingMore = false
            }
        }
    }
    LoadMoreEffect(listState, nodes.size) { loadMore() }

    /** Mở "N phản hồi" (lần đầu) hoặc "Phản hồi khác" (các lần sau) của một bình luận. */
    fun openReplies(node: CommentNode) {
        val t = (if (node.loaded) node.moreToken else node.item.repliesToken) ?: return
        if (node.loading) return
        node.loading = true
        scope.launch {
            try {
                val page = CommentsRepository.more(t, account)
                val fresh = buildNodes(page.items, minOf(node.depth + 1, MAX_NEST), node.rootId)
                node.children = (node.children + fresh).distinctBy { it.item.id }
                node.moreToken = page.token?.takeIf { page.items.isNotEmpty() }
                node.loaded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.shortMessage ?: "Không tải được phản hồi")
            } finally {
                node.loading = false
            }
        }
    }

    fun vote(node: CommentNode, like: Boolean) {
        val acc = account ?: return toast("Hãy đăng nhập để thích hoặc không thích bình luận")
        val item = node.item
        val on = if (like) !node.liked else !node.disliked
        val action = when {
            like && on -> item.likeAction
            like -> item.unlikeAction
            on -> item.dislikeAction
            else -> item.undislikeAction
        } ?: return toast("Không thực hiện được với bình luận này")
        val before = node.liked to node.disliked
        if (like) {
            node.liked = on
            if (on) node.disliked = false
        } else {
            node.disliked = on
            if (on) node.liked = false
        }
        scope.launch {
            try {
                CommentsRepository.vote(acc, action)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                node.liked = before.first
                node.disliked = before.second
                toast(e.shortMessage ?: "Không thực hiện được thao tác này")
            }
        }
    }

    fun startReply(node: CommentNode) {
        replyTo = node
        val mention = if (node.depth > 0) node.item.author + " " else ""
        text = TextFieldValue(mention, TextRange(mention.length))
        focus.requestFocus()
    }

    fun send() {
        val acc = account ?: return toast("Hãy đăng nhập để bình luận")
        val body = text.text.trim()
        if (body.isEmpty() || sending) return
        sending = true
        val target = replyTo
        scope.launch {
            try {
                if (target == null) {
                    val posted = CommentsRepository.post(acc, video.id, body)
                    nodes.add(0, CommentNode(posted, 0, posted.id))
                    listState.animateScrollToItem(0)
                } else {
                    val posted = CommentsRepository.reply(acc, video.id, target.rootId, body, target.depth + 1)
                    target.children = listOf(CommentNode(posted, minOf(target.depth + 1, MAX_NEST), target.rootId)) + target.children
                    target.loaded = true
                }
                text = TextFieldValue()
                replyTo = null
                focusManager.clearFocus()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.shortMessage ?: "Không đăng được bình luận")
            } finally {
                sending = false
            }
        }
    }

    // Bảng đã được nâng lên đúng bằng phần bàn phím che ([imeOverlapPx]), nên chỉ chừa đáy bằng đúng phần đó (hoặc thanh điều hướng khi không có bàn phím).
    // Tự đo: đáy nội dung đang nằm ở đâu trên màn hình so với đỉnh bàn phím, rồi chừa đúng phần bị che (đúng cả khi cửa sổ có tự co lại hay không).
    val overlapDp = with(density) { overlapPx.toDp() }
    Box(
        Modifier.fillMaxSize().onGloballyPositioned { c ->
            // Toạ độ trong cửa sổ: cùng hệ với số đo của bàn phím (tính từ đáy cửa sổ), không dùng toạ độ màn hình vì có thể lệch.
            contentBottom = (c.positionInWindow().y + c.size.height).toInt()
        }
    ) {
    Column(
        Modifier.fillMaxSize().then(
            if (imeBottom > 0) Modifier.padding(bottom = overlapDp) else Modifier.windowInsetsPadding(WindowInsets.navigationBars)
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Bình luận", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            total?.let { Text(it, fontSize = 14.sp, color = NanColors.TextSecondary, modifier = Modifier.padding(start = 10.dp)) }
            Spacer(Modifier.weight(1f))
            if (sorts.isNotEmpty()) Box {
                CommentIcon(YtSliders, 44.dp, 22.dp) { sortOpen = true }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    sorts.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.label, fontWeight = if (s.selected) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                sortOpen = false
                                if (!s.selected) load(s.token)
                            }
                        )
                    }
                }
            }
            CommentIcon(IconClose, 44.dp, 22.dp, onClose)
        }
        HorizontalDivider(color = NanColors.Divider)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> CircularProgressIndicator(
                    color = Color.White, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(32.dp)
                )
                error != null && nodes.isEmpty() -> Text(
                    error ?: "", color = NanColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
                else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(nodes, key = { it.item.id }) { node ->
                        CommentBlock(node, video, account, onSave, onVote = ::vote, onReply = ::startReply, onOpen = ::openReplies)
                    }
                    if (loadingMore) item { LoadingFooter() }
                }
            }
        }
        if (!disabled) {
            HorizontalDivider(color = NanColors.Divider)
            CommentComposer(
                account = account,
                replyTo = replyTo,
                text = text,
                sending = sending,
                focus = focus,
                onText = { text = it },
                onCancelReply = { replyTo = null; text = TextFieldValue() },
                onSend = ::send
            )
        }
    }
    }
}

/** Một bình luận kèm khối phản hồi của nó (đệ quy): đường nối chạy từ ảnh đại diện cha xuống từng phản hồi. */
@Composable
private fun CommentBlock(
    node: CommentNode,
    video: VideoItem,
    account: Account?,
    onSave: (VideoItem) -> Unit,
    onVote: (CommentNode, Boolean) -> Unit,
    onReply: (CommentNode) -> Unit,
    onOpen: (CommentNode) -> Unit
) {
    val avatar = avatarSize(node.depth)
    val hasKids = node.children.isNotEmpty() || node.hasUnopened || node.moreToken != null
    // Tới cấp tối đa thì phản hồi sâu hơn xếp phẳng ngay dưới, cùng lề, không có đường nối / thụt thêm.
    val nest = node.depth < MAX_NEST
    Column(Modifier.fillMaxWidth().padding(start = if (node.depth == 0) 16.dp else 0.dp)) {
        CommentRow(node, video, account, onSave, avatar, joined = hasKids && nest, onVote = onVote, onReply = onReply)
        if (hasKids) Column(if (nest) Modifier.padding(start = avatar / 2) else Modifier) {
            node.children.forEachIndexed { i, child ->
                val last = i == node.children.lastIndex && !node.hasUnopened && node.moreToken == null
                Connector(last, nested = nest, curveY = 6.dp + avatarSize(child.depth) / 2) {
                    CommentBlock(child, video, account, onSave, onVote, onReply, onOpen)
                }
            }
            if (node.hasUnopened || node.moreToken != null) Connector(last = true, nested = nest, curveY = 14.dp) {
                MoreRepliesLink(node, onOpen)
            }
        }
    }
}

/** Khung của một phản hồi: vẽ đường cong từ đường dọc của cha vào ảnh đại diện; chưa phải phản hồi cuối thì đường dọc đi tiếp xuống. */
@Composable
private fun Connector(last: Boolean, nested: Boolean, curveY: Dp, content: @Composable () -> Unit) {
    val indent = if (nested) 22.dp else 0.dp
    Box(
        Modifier.fillMaxWidth().then(
            if (!nested) Modifier else Modifier.drawBehind {
                val r = 10.dp.toPx()
                val y = curveY.toPx()
                val stroke = Stroke(LineWidth.toPx())
                val curve = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(0f, y - r)
                    quadraticTo(0f, y, r, y)
                    lineTo(indent.toPx() - 4.dp.toPx(), y)
                }
                drawPath(curve, Line, style = stroke)
                if (!last) drawLine(Line, Offset(0f, y), Offset(0f, size.height), stroke.width)
            }
        )
    ) {
        Box(Modifier.padding(start = indent)) { content() }
    }
}

@Composable
private fun MoreRepliesLink(node: CommentNode, onOpen: (CommentNode) -> Unit) {
    val label = if (node.hasUnopened) node.item.replies?.let { "$it phản hồi" } ?: "Xem phản hồi" else "Phản hồi khác"
    Row(
        Modifier.fillMaxWidth().plainClickable { onOpen(node) }.padding(top = 4.dp, bottom = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        if (node.loading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        else Icon(Lucide.ChevronDown, null, Modifier.size(18.dp))
    }
}

@Composable
private fun CommentRow(
    node: CommentNode,
    video: VideoItem,
    account: Account?,
    onSave: (VideoItem) -> Unit,
    avatar: Dp,
    joined: Boolean,
    onVote: (CommentNode, Boolean) -> Unit,
    onReply: (CommentNode) -> Unit
) {
    val c = node.item
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    fun copy(value: String, done: String) {
        menu = false
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
    }
    val count = (if (node.liked) c.likesLiked ?: c.likes else c.likes)
    Row(
        Modifier.fillMaxWidth()
            .drawBehind {
                // Đường dọc bắt đầu ngay dưới ảnh đại diện, nối liền với đường cong của các phản hồi bên dưới.
                if (joined) {
                    val x = avatar.toPx() / 2
                    drawLine(Line, Offset(x, 6.dp.toPx() + avatar.toPx() + 3.dp.toPx()), Offset(x, size.height), LineWidth.toPx())
                }
            }
            .padding(top = 6.dp, bottom = 6.dp)
    ) {
        Avatar(c.author.trimStart('@').take(1).uppercase(), avatar, photoUrl = c.avatar)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(c.author, c.published).joinToString(" • "),
                fontSize = 12.sp, color = NanColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(mentioned(c.text), fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                VoteButton(if (node.liked) YtThumbUpFilled else YtThumbUp, count) { onVote(node, true) }
                Spacer(Modifier.width(28.dp))
                VoteButton(if (node.disliked) YtThumbDownFilled else YtThumbDown, null) { onVote(node, false) }
                Spacer(Modifier.width(28.dp))
                VoteButton(YtComment, null) { onReply(node) }
                c.heartedBy?.let { HeartBadge(it) }
            }
        }
        Box {
            CommentIcon(MoreHorizontalIcon, 32.dp, 22.dp) { menu = true }
            DropdownMenu(
                expanded = menu, onDismissRequest = { menu = false },
                shape = RoundedCornerShape(20.dp), containerColor = NanColors.Surface, tonalElevation = 0.dp
            ) {
                MenuItem(YtComment, "Phản hồi") { menu = false; onReply(node) }
                MenuItem(ShareIcon, "Chia sẻ") { copy("https://www.youtube.com/watch?v=${video.id}&lc=${c.id}", "Đã sao chép liên kết bình luận") }
                MenuItem(Lucide.Copy, "Sao chép") { copy(c.author, "Đã sao chép tên") }
                MenuItem(if (SavedComments.contains(account, c.id)) BookmarkFilled else BookmarkOutline, if (SavedComments.contains(account, c.id)) "Bỏ lưu" else "Lưu") {
                    menu = false
                    // Lưu: nhớ bình luận rồi mở bảng "Lưu vào…" để thêm video vào danh sách phát; mở lại video sẽ tự hiện bình luận.
                    if (SavedComments.toggle(account, video.id, c)) onSave(video)
                    else Toast.makeText(context, "Đã bỏ lưu bình luận", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().widthIn(min = 200.dp).plainClickable(onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = Color.White)
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 16.sp, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun VoteButton(icon: ImageVector, count: String?, onClick: () -> Unit) {
    Row(Modifier.plainClickable(onClick).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = Color.White)
        count?.let { Text(it, fontSize = 13.sp, color = NanColors.TextSecondary, modifier = Modifier.padding(start = 6.dp)) }
    }
}

/** Ảnh kênh chủ video kèm trái tim đỏ nhỏ: bình luận được chủ kênh thả tim. */
@Composable
private fun HeartBadge(photoUrl: String) {
    Box(Modifier.padding(start = 28.dp)) {
        Avatar("", 20.dp, photoUrl = photoUrl)
        Icon(
            YtHeartFilled, null, tint = LikedHeart,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 3.dp).size(11.dp)
        )
    }
}

private val LikedHeart = Color(0xFFFF2D3B)

@Composable
private fun CommentIcon(icon: ImageVector, box: Dp, size: Dp, onClick: () -> Unit) {
    Box(Modifier.size(box).clip(CircleShape).plainClickable(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size), tint = Color.White)
    }
}

/** Tô xanh @tên được nhắc tới, như YouTube. */
private fun mentioned(text: String) = buildAnnotatedString {
    var last = 0
    Regex("@[^\\s@]+").findAll(text).forEach { m ->
        append(text.substring(last, m.range.first))
        withStyle(SpanStyle(color = NanColors.Blue)) { append(m.value) }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

/** Ô nhập: thu gọn là một viên "Bình luận..."; chạm vào thì hiện ảnh tài khoản, nút gửi và hàng emoji nhanh. */
@Composable
private fun CommentComposer(
    account: Account?,
    replyTo: CommentNode?,
    text: TextFieldValue,
    sending: Boolean,
    focus: FocusRequester,
    onText: (TextFieldValue) -> Unit,
    onCancelReply: () -> Unit,
    onSend: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val expanded = focused || replyTo != null
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        if (replyTo != null) Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Đang trả lời ${replyTo.item.author}", fontSize = 12.sp, color = NanColors.TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            CommentIcon(IconClose, 28.dp, 16.dp, onCancelReply)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (expanded) {
                Avatar((account?.name ?: "?").take(1).uppercase(), 32.dp, photoUrl = account?.photo)
                Spacer(Modifier.width(8.dp))
            }
            BasicTextField(
                value = text,
                onValueChange = onText,
                modifier = Modifier.weight(1f).focusRequester(focus).onFocusChanged { focused = it.isFocused },
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(NanColors.Blue),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorationBox = { field ->
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(24.dp)).background(NanColors.Surface)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (text.text.isEmpty()) {
                            Text(if (replyTo != null) "Thêm câu trả lời..." else "Bình luận...", fontSize = 14.sp, color = NanColors.TextSecondary)
                        }
                        field()
                    }
                }
            )
            if (expanded) {
                Spacer(Modifier.width(10.dp))
                val ready = text.text.isNotBlank() && !sending
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(if (ready) Color(0xFFFF0033) else NanColors.Surface)
                        .plainClickable { if (ready) onSend() },
                    contentAlignment = Alignment.Center
                ) {
                    if (sending) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    else Icon(YtSend, null, Modifier.size(22.dp), tint = if (ready) Color.White else NanColors.TextSecondary)
                }
            }
        }
        if (expanded) Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Emojis.forEach { e ->
                Box(Modifier.plainClickable {
                    val at = text.selection.end
                    onText(TextFieldValue(text.text.substring(0, at) + e + text.text.substring(at), TextRange(at + e.length)))
                }.padding(4.dp)) { Text(e, fontSize = 22.sp) }
            }
        }
    }
}
