package com.nan.nantube.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Pin
import com.nan.nantube.data.SavedComments
import com.nan.nantube.data.Account
import com.nan.nantube.data.CommentItem
import com.nan.nantube.data.CommentSort
import com.nan.nantube.data.CommentsRepository
import com.nan.nantube.data.VideoItem
import com.nan.nantube.data.OwnerRef
import com.nan.nantube.data.VideoOwner
import com.nan.nantube.data.pinnedHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Một bình luận trong cây: bình luận gốc có độ sâu 0, phản hồi 1, phản hồi của phản hồi 2... */
class ShortChatNode(initial: CommentItem, val depth: Int, val rootId: String) {
    var item by mutableStateOf(initial)
    var likeCount by mutableStateOf(if (item.liked) item.likesLiked ?: item.likes else item.likes)
    var writing by mutableStateOf(false)
    var liked by mutableStateOf(item.liked)
    var disliked by mutableStateOf(item.disliked)
    var children by mutableStateOf<List<ShortChatNode>>(emptyList())
    var loaded by mutableStateOf(false)
    var loading by mutableStateOf(false)

    /** Đang mở danh sách phản hồi (false = đã bấm "Ẩn phản hồi", dữ liệu vẫn giữ để mở lại không cần tải). */
    var expanded by mutableStateOf(true)

    /** Token của "Phản hồi khác" (trang phản hồi kế); null = hết. */
    var moreToken by mutableStateOf<String?>(null)

    /** Còn phản hồi chưa mở (hiện "N phản hồi"). */
    val hasUnopened get() = !loaded && item.repliesToken != null
}

/** Chỉ 2 tầng: bình luận gốc 0 và phản hồi 1 (mọi phản hồi xếp phẳng một hàng, không đường nối, không lồng thêm). */
private const val MAX_NEST = 1
private const val CACHE_MAX_AGE_MS = 15 * 60_000L

/**
 * Giữ bình luận của vài video gần nhất trong bộ nhớ: đóng bảng rồi mở lại thì hiện ngay đúng chỗ đang đọc
 * (cả các phản hồi đã mở), không tải lại.
 */
class ShortChatSession {
    val nodes = mutableStateListOf<ShortChatNode>()
    var token by mutableStateOf<String?>(null)
    var fromTv = false
    var total by mutableStateOf<String?>(null)
    var sorts by mutableStateOf<List<CommentSort>>(emptyList())
    var sortIndex = 0
    val feeds = HashMap<Int, ShortChatSortFeed>()
    var disabled by mutableStateOf(false)
    var loaded = false
    var loadedAt = 0L
    var scrollIndex = 0
    var scrollOffset = 0
    /** Mã kênh (UC…) của chủ video, lấy từ trang video rồi nhớ lại cho cả "Nổi bật nhất" và "Mới nhất". */
    var ownerId by mutableStateOf<String?>(null)
    var ownerHandle by mutableStateOf<String?>(null)
}

/** Bình luận đã tải của một kiểu sắp xếp (như MeeTube giữ riêng Top / Newest): đổi qua lại không phải tải lại. */
class ShortChatSortFeed(val nodes: List<ShortChatNode>, val token: String?, val scrollIndex: Int, val scrollOffset: Int)

object ShortChatCache {
    private val map = object : LinkedHashMap<String, ShortChatSession>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ShortChatSession>?) = size > 6
    }

    fun get(key: String): ShortChatSession {
        val old = map[key]
        if (old != null && old.loaded && System.currentTimeMillis() - old.loadedAt > CACHE_MAX_AGE_MS) map.remove(key)
        return map.getOrPut(key) { ShortChatSession() }
    }
}
private val Line = Color(0xFF4A4A4A)
private val LineWidth = 0.8.dp
private val Emojis = listOf("❤️", "😂", "🎉", "😢", "😮", "😅", "😊")

private fun avatarSize(depth: Int): Dp = when (depth) { 0 -> 30.dp; 1 -> 24.dp; 2 -> 20.dp; else -> 18.dp }

/** Phản hồi xếp phẳng theo thứ tự YouTube trả (đã gồm cả phản hồi-của-phản-hồi), bỏ qua bình luận đã có. */
private fun buildNodes(items: List<CommentItem>, depth: Int, rootId: String, opened: ShortChatNode? = null): List<ShortChatNode> {
    val seen = HashSet<String>()
    val earlier = ArrayList<ShortChatNode>()
    fun collect(n: ShortChatNode) { for (c in n.children) { seen.add(c.item.id); earlier.add(c); collect(c) } }
    if (opened != null) collect(opened)
    val out = ArrayList<ShortChatNode>()
    for (item in items) {
        if (!seen.add(item.id)) continue
        val node = ShortChatNode(item, minOf(depth, MAX_NEST), rootId)
        out.add(node)
        earlier.add(node)
    }
    return out
}

private fun topNodes(items: List<CommentItem>) = items.map { ShortChatNode(it, 0, it.id) }

/** Khung chat của Short (ShortChat, giữ nguyên kiểu bản 238): tiêu đề + sắp xếp, cây bình luận, ô nhập. Nằm trong khung kéo được của Short. */
@Composable
fun ShortChatContent(video: VideoItem, account: Account?, imeOverlapPx: Int = 0, onSave: (VideoItem) -> Unit = {}, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val session = remember(video.id, account?.id) { ShortChatCache.get(video.id + "|" + (account?.id ?: "")) }
    val nodes = session.nodes
    // Nhãn ghim chỉ chủ video mới có: handle trong nhãn cũng là handle chủ video (vẫn so từng ký tự với người viết).
    val creator = OwnerRef(
        session.ownerId,
        (listOfNotNull(session.ownerHandle) + nodes.mapNotNull { pinnedHandle(it.item.pinned) }).toSet()
    )
    // Mã kênh chủ video lấy từ trang video (như check nhãn ghim nhưng chính xác): bình luận có cùng mã kênh là của chủ video.
    LaunchedEffect(video.id) {
        if (session.ownerId != null && session.ownerHandle != null) return@LaunchedEffect
        val info = try {
            com.nan.nantube.data.ShortsPlayback.info(context.applicationContext, account, video).await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val found = VideoOwner.find(video.id, account)
        session.ownerId = (info?.channelId ?: found?.id ?: video.channelId)?.takeIf { it.startsWith("UC") }
        // Không đọc được handle từ trang video thì thử tên kênh của thẻ, chỉ khi nó có dạng handle (không dấu cách).
        session.ownerHandle = found?.handle
            ?: info?.author?.trim()?.trimStart('@')?.takeIf { it.isNotEmpty() && it.none(Char::isWhitespace) }?.let { "@$it" }
        android.util.Log.d("NanCreator", "video=${video.id} ownerId=${session.ownerId} ownerHandle=${session.ownerHandle}")
    }
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
    var sortIndex by remember(session) { mutableStateOf(session.sortIndex) }
    var sortOpen by remember { mutableStateOf(false) }
    var loading by remember(session) { mutableStateOf(!session.loaded) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var disabled by remember(session) { mutableStateOf(session.disabled) }
    var replyTo by remember(session) { mutableStateOf<ShortChatNode?>(null) }
    var text by remember(session) { mutableStateOf(TextFieldValue()) }
    var sending by remember(session) { mutableStateOf(false) }
    var editTarget by remember(session) { mutableStateOf<ShortChatNode?>(null) }
    var editText by remember(session) { mutableStateOf(TextFieldValue()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(editTarget, disabled) {
        if (editTarget != null && !disabled) {
            focus.requestFocus()
            keyboard?.show()
        }
    }
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
            if (editTarget == null && (text.text.isBlank() || Regex("^@\\S+$").matches(text.text.trim()))) {
                text = TextFieldValue()
                replyTo = null
            }
        }
        imeWasOpen = open
    }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    fun load(sortToken: String? = null, fallbackIndex: Int = sortIndex) {
        loading = true
        error = null
        scope.launch {
            try {
                val page = if (sortToken != null) CommentsRepository.more(sortToken, account, session.fromTv, videoId = video.id) else CommentsRepository.first(video.id, account)
                nodes.clear()
                nodes.addAll(topNodes(page.items))
                token = page.token
                total = page.total ?: total
                // Danh sách kiểu sắp xếp chỉ lấy một lần từ lượt tải đầu; trang của từng kiểu không có danh sách đáng tin.
                if (sortToken == null && page.sorts.isNotEmpty()) sorts = page.sorts
                if (sortToken != null) listState.scrollToItem(0)
                if (page.items.isEmpty()) error = "Chưa có bình luận nào."
                session.token = token
                session.fromTv = page.fromTv
                session.total = total
                session.sorts = sorts
                session.sortIndex = sortIndex
                session.loaded = true
                session.loadedAt = System.currentTimeMillis()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (sortToken != null) {
                    // Không tải được kiểu mới: quay về kiểu cũ cùng danh sách đang có.
                    sortIndex = fallbackIndex
                    session.sortIndex = fallbackIndex
                    session.feeds[fallbackIndex]?.let { f ->
                        nodes.clear()
                        nodes.addAll(f.nodes)
                        token = f.token
                        session.token = f.token
                    }
                    toast(e.shortMessage ?: "Không tải được bình luận.")
                } else {
                    disabled = nodes.isEmpty()
                    session.disabled = disabled
                    error = e.shortMessage ?: "Không tải được bình luận."
                }
            } finally {
                loading = false
            }
        }
    }

    fun pickSort(index: Int) {
        val target = sorts.getOrNull(index) ?: return
        if (index == sortIndex || loading) return
        val from = sortIndex
        session.feeds[from] = ShortChatSortFeed(nodes.toList(), token, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        sortIndex = index
        session.sortIndex = index
        val kept = session.feeds[index]
        if (kept == null) {
            load(target.token, from)
            return
        }
        nodes.clear()
        nodes.addAll(kept.nodes)
        token = kept.token
        session.token = kept.token
        error = null
        scope.launch { listState.scrollToItem(kept.scrollIndex, kept.scrollOffset) }
    }
    LaunchedEffect(session) { if (!session.loaded) load() }

    fun loadMore() {
        val t = token ?: return
        if (loadingMore) return
        loadingMore = true
        scope.launch {
            try {
                val page = CommentsRepository.more(t, account, session.fromTv, videoId = video.id)
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
    fun openReplies(node: ShortChatNode) {
        if (!node.expanded) { node.expanded = true; return }
        val t = (if (node.loaded) node.moreToken else node.item.repliesToken) ?: return
        if (node.loading) return
        node.loading = true
        scope.launch {
            try {
                val page = CommentsRepository.more(t, account, node.item.fromTv, videoId = video.id, rootId = node.rootId)
                val fresh = buildNodes(page.items.filter { it.id != node.item.id && it.id != node.rootId }, minOf(node.depth + 1, MAX_NEST), node.rootId, node)
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

    fun vote(node: ShortChatNode, like: Boolean, effect: TapEffectState) {
        val acc = account ?: return toast("Hãy đăng nhập để thích hoặc không thích bình luận")
        if (node.writing) return
        node.writing = true
        // Phản hồi ngay khi chạm; chỉ like (khi bật) mới bung confetti, dislike chỉ nảy nút.
        effect.play(burst = like && !node.liked)
        // Đổi giao diện ngay (như MeeTube): icon đặc + số tăng/giảm; YouTube từ chối thì trả về như cũ.
        val wasLiked = node.liked
        val wasDisliked = node.disliked
        val wasCount = node.likeCount
        val turnOn = if (like) !wasLiked else !wasDisliked
        val nowLiked = like && turnOn
        val nowDisliked = !like && turnOn
        val item = node.item
        node.liked = nowLiked
        node.disliked = nowDisliked
        node.likeCount = when {
            nowLiked && item.likesLiked != null -> item.likesLiked.takeUnless { it == "0" }
            !nowLiked && item.likesLiked != null -> item.likes
            nowLiked == wasLiked -> wasCount
            wasCount == null || wasCount.toIntOrNull() != null ->
                ((wasCount?.toIntOrNull() ?: 0) + if (nowLiked) 1 else -1).takeIf { it > 0 }?.toString()
            else -> wasCount
        }
        scope.launch {
            try {
                val result = CommentsRepository.vote(acc, video.id, node.item, node.rootId, wasLiked, wasDisliked, wasCount, like)
                node.liked = result.liked
                node.disliked = result.disliked
                node.likeCount = result.count
            } catch (e: CancellationException) {
                session.loadedAt = 0L // Lần mở sau tải lại nếu màn hình đóng trong lúc request đang đi.
                throw e
            } catch (e: Exception) {
                node.liked = wasLiked
                node.disliked = wasDisliked
                node.likeCount = wasCount
                toast(e.shortMessage ?: "Không thực hiện được thao tác này")
            } finally {
                node.writing = false
            }
        }
    }

    fun startEdit(node: ShortChatNode) {
        if (disabled || sending || node.writing || !node.item.isOwnedBy(account)) return
        if (editTarget === node) {
            focus.requestFocus()
            keyboard?.show()
            return
        }
        val original = node.item.text
        editText = TextFieldValue(original, TextRange(original.length))
        editTarget = node
    }

    fun finishEdit() {
        editTarget = null
        editText = TextFieldValue()
        focusManager.clearFocus()
    }

    fun saveEdit() {
        val acc = account ?: return
        val node = editTarget ?: return
        val body = editText.text.trim()
        if (sending || node.writing || body.isEmpty() || body == node.item.text.trim() || !node.item.isOwnedBy(acc)) return
        sending = true
        node.writing = true
        scope.launch {
            try {
                val updated = CommentsRepository.edit(acc, video.id, node.item, node.rootId, body)
                node.item = node.item.copy(text = updated)
                if (editTarget === node) finishEdit()
            } catch (e: CancellationException) {
                session.loadedAt = 0L
                throw e
            } catch (e: Exception) {
                toast(e.shortMessage ?: "Không chỉnh sửa được bình luận")
            } finally {
                node.writing = false
                sending = false
            }
        }
    }

    fun containsNode(parent: ShortChatNode, target: ShortChatNode): Boolean =
        parent === target || parent.children.any { containsNode(it, target) }

    fun removeChild(parent: ShortChatNode, node: ShortChatNode): Boolean {
        if (node in parent.children) {
            parent.children = parent.children - node
            return true
        }
        return parent.children.any { removeChild(it, node) }
    }

    fun delete(node: ShortChatNode) {
        val acc = account ?: return
        if (!node.item.isOwnedBy(acc) || node.writing) return
        node.writing = true
        scope.launch {
            try {
                CommentsRepository.delete(acc, video.id, node.item, node.rootId)
                if (!nodes.remove(node)) nodes.any { removeChild(it, node) }
                if (replyTo?.item?.id == node.item.id || (node.depth == 0 && replyTo?.rootId == node.rootId)) {
                    replyTo = null
                    text = TextFieldValue()
                }
                if (editTarget?.let { containsNode(node, it) || (node.depth == 0 && it.rootId == node.rootId) } == true) {
                    finishEdit()
                }
                toast("Đã xóa bình luận")
            } catch (e: CancellationException) {
                session.loadedAt = 0L
                throw e
            } catch (e: Exception) {
                toast(e.shortMessage ?: "Không xóa được bình luận")
            } finally {
                node.writing = false
            }
        }
    }

    fun startReply(node: ShortChatNode) {
        if (editTarget != null) {
            if (sending) return
            finishEdit()
        }
        replyTo = node
        val mention = if (node.depth > 0) node.item.author + " " else ""
        text = TextFieldValue(mention, TextRange(mention.length))
        focus.requestFocus()
    }

    fun send() {
        val acc = account ?: return toast("Hãy đăng nhập để bình luận")
        val body = text.text.trim()
        if (body.isEmpty() || sending || editTarget != null) return
        sending = true
        val target = replyTo
        scope.launch {
            try {
                if (target == null) {
                    val posted = CommentsRepository.post(acc, video.id, body)
                    nodes.add(0, ShortChatNode(posted, 0, posted.id))
                    listState.animateScrollToItem(0)
                } else {
                    val posted = CommentsRepository.reply(acc, video.id, target.rootId, body, target.depth + 1)
                    target.children = listOf(ShortChatNode(posted, minOf(target.depth + 1, MAX_NEST), target.rootId)) + target.children
                    target.loaded = true
                    target.expanded = true
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
            Text("Bình luận", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            total?.let { Text(it, fontSize = 14.sp, color = NanColors.TextSecondary, modifier = Modifier.padding(start = 10.dp)) }
            Spacer(Modifier.weight(1f))
            Box {
                CommentIcon(YtSliders, 44.dp, 22.dp) { sortOpen = true }
                DropdownMenu(
                    expanded = sortOpen, onDismissRequest = { sortOpen = false },
                    shape = RoundedCornerShape(16.dp), containerColor = NanColors.Surface, tonalElevation = 0.dp
                ) {
                    // Như MeeTube: mục 0 = Nổi bật nhất (Top), mục 1 = Mới nhất (Newest). Chưa tải xong thì hiện sẵn nhưng chưa bấm được.
                    for (i in 0..1) {
                        SortItem(if (i == 1) "Mới nhất" else "Nổi bật nhất",
                            if (i == 1) "Có thể gồm bình luận rác" else "Được thích nhiều nhất lên đầu", i == sortIndex) {
                            sortOpen = false
                            pickSort(i)
                        }
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
                        CommentBlock(node, video, account, creator, onSave, onVote = ::vote, onReply = ::startReply, onOpen = ::openReplies,
                            onEdit = ::startEdit, onDelete = ::delete)
                    }
                    if (loadingMore) item { LoadingFooter() }
                }
            }
        }
        if (!disabled) {
            HorizontalDivider(color = NanColors.Divider)
            val editing = editTarget
            CommentComposer(
                account = account,
                replyTo = if (editing == null) replyTo else null,
                editTarget = editing,
                text = if (editing == null) text else editText,
                sending = sending,
                focus = focus,
                onText = { if (editTarget == null) text = it else if (!sending) editText = it },
                onCancelReply = { replyTo = null; text = TextFieldValue() },
                onCancelEdit = { if (!sending) finishEdit() },
                onSaveEdit = ::saveEdit,
                onSend = ::send
            )
        }
    }
    }
}

/** Một bình luận kèm khối phản hồi của nó (đệ quy): đường nối chạy từ ảnh đại diện cha xuống từng phản hồi. */
@Composable
private fun CommentBlock(
    node: ShortChatNode,
    video: VideoItem,
    account: Account?,
    creator: OwnerRef,
    onSave: (VideoItem) -> Unit,
    onVote: (ShortChatNode, Boolean, TapEffectState) -> Unit,
    onReply: (ShortChatNode) -> Unit,
    onOpen: (ShortChatNode) -> Unit,
    onEdit: (ShortChatNode) -> Unit,
    onDelete: (ShortChatNode) -> Unit
) {
    val avatar = avatarSize(node.depth)
    val shown = if (node.expanded) node.children else emptyList()
    val collapsed = !node.expanded && node.children.isNotEmpty()
    val links = buildList {
        if (node.depth > 0) Unit // phản hồi không có nút "Xem phản hồi": mọi phản hồi đã nằm sẵn trong danh sách của bình luận gốc
        else if (collapsed) add(ShortChatReplyLink.Show)
        else {
            if (node.hasUnopened) add(ShortChatReplyLink.Open)
            else if (node.moreToken != null) add(ShortChatReplyLink.More)
            if (node.loaded && node.children.isNotEmpty()) add(ShortChatReplyLink.Hide)
        }
    }
    val hasKids = shown.isNotEmpty() || links.isNotEmpty()
    // Tới cấp tối đa thì phản hồi sâu hơn xếp phẳng ngay dưới, cùng lề, không có đường nối / thụt thêm.
    val nest = node.depth < MAX_NEST
    Column(Modifier.fillMaxWidth().padding(start = if (node.depth == 0) 16.dp else 0.dp)) {
        CommentRow(node, video, account, creator, onSave, avatar, joined = hasKids && nest, onVote = onVote, onReply = onReply, onEdit = onEdit, onDelete = onDelete)
        if (hasKids) Column(if (nest) Modifier.padding(start = avatar / 2) else Modifier) {
            shown.forEachIndexed { i, child ->
                val last = i == shown.lastIndex && links.isEmpty()
                Connector(last, nested = nest, curveY = 6.dp + avatarSize(child.depth) / 2) {
                    CommentBlock(child, video, account, creator, onSave, onVote, onReply, onOpen, onEdit, onDelete)
                }
            }
            links.forEachIndexed { i, link ->
                Connector(last = i == links.lastIndex, nested = nest, curveY = 14.dp) {
                    MoreRepliesLink(node, link, if (nest) 0.dp else avatar + 12.dp, onOpen)
                }
            }
        }
    }
}

private enum class ShortChatReplyLink { Show, Open, More, Hide }

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
                // Lấn ra 1px ở hai đầu để các đoạn liền nhau không hở khe do làm tròn pixel.
                val curve = Path().apply {
                    moveTo(0f, -1f)
                    lineTo(0f, y - r)
                    quadraticTo(0f, y, r, y)
                    lineTo(indent.toPx() - 4.dp.toPx(), y)
                }
                drawPath(curve, Line, style = stroke)
                // Chưa phải phản hồi cuối: đường dọc đi liền suốt chiều cao (kể cả đoạn đường cong uốn ra) tới phản hồi kế.
                if (!last) drawLine(Line, Offset(0f, -1f), Offset(0f, size.height + 1f), stroke.width)
            }
        )
    ) {
        Box(Modifier.padding(start = indent)) { content() }
    }
}

@Composable
private fun MoreRepliesLink(node: ShortChatNode, link: ShortChatReplyLink, startPad: Dp, onOpen: (ShortChatNode) -> Unit) {
    val label = when (link) {
        ShortChatReplyLink.Hide -> "Ẩn phản hồi"
        ShortChatReplyLink.More -> "Phản hồi khác"
        else -> node.item.replies?.let { "$it phản hồi" } ?: "Xem phản hồi"
    }
    Row(
        Modifier.fillMaxWidth()
            .plainClickable { if (link == ShortChatReplyLink.Hide) node.expanded = false else onOpen(node) }
            .padding(start = startPad, top = 4.dp, bottom = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        if (node.loading && link != ShortChatReplyLink.Hide) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        else Icon(if (link == ShortChatReplyLink.Hide) Lucide.ChevronUp else Lucide.ChevronDown, null, Modifier.size(18.dp))
    }
}

@Composable
private fun CommentRow(
    node: ShortChatNode,
    video: VideoItem,
    account: Account?,
    creator: OwnerRef,
    onSave: (VideoItem) -> Unit,
    avatar: Dp,
    joined: Boolean,
    onVote: (ShortChatNode, Boolean, TapEffectState) -> Unit,
    onReply: (ShortChatNode) -> Unit,
    onEdit: (ShortChatNode) -> Unit,
    onDelete: (ShortChatNode) -> Unit
) {
    val c = node.item
    val context = LocalContext.current
    val copy = rememberChatCopier()
    var menu by remember { mutableStateOf(false) }
    val count = node.likeCount
    val likeEffect = rememberTapEffect()
    val dislikeEffect = rememberTapEffect()
    Row(
        Modifier.fillMaxWidth()
            .copyOnLongPress(c.text) { copy(c.text, "Đã sao chép bình luận") }
            .drawBehind {
                // Đường dọc bắt đầu ngay dưới ảnh đại diện, nối liền với đường cong của các phản hồi bên dưới.
                if (joined) {
                    val x = avatar.toPx() / 2
                    drawLine(Line, Offset(x, 6.dp.toPx() + avatar.toPx() + 3.dp.toPx()), Offset(x, size.height + 1f), LineWidth.toPx())
                }
            }
            .padding(top = 6.dp, bottom = 6.dp, end = 12.dp)
    ) {
        Avatar(c.author.trimStart('@').take(1).uppercase(), avatar, photoUrl = c.avatar)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            c.pinned?.let { label ->
                Row(Modifier.padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Pin, null, Modifier.size(13.dp), tint = NanColors.TextSecondary)
                    Spacer(Modifier.width(4.dp))
                    Text(label, fontSize = ChatMetaSize, lineHeight = ChatMetaLine, color = NanColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // Chủ video: tên nằm trong viên sáng (YouTube báo isCreator, hoặc trùng handle học từ nhãn ghim).
            val byCreator = c.creator || creator.matches(c)
            val verified = rememberVerified(c, byCreator, creator.id)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChatNameTag(c.author, byCreator, verified, Modifier.weight(1f, fill = false))
                c.published?.let { Text(" • $it", fontSize = ChatMetaSize, lineHeight = ChatMetaLine, color = NanColors.TextSecondary, maxLines = 1) }
            }
            Text(mentioned(c.text), fontSize = 14.sp, lineHeight = ChatBodyLine, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                VoteButton(if (node.liked) YtThumbUpFilled else YtThumbUp, count, !node.writing, likeEffect) {
                    onVote(node, true, likeEffect)
                }
                Spacer(Modifier.width(28.dp))
                VoteButton(if (node.disliked) YtThumbDownFilled else YtThumbDown, null, !node.writing, dislikeEffect) {
                    onVote(node, false, dislikeEffect)
                }
                Spacer(Modifier.width(28.dp))
                VoteButton(YtComment, null) { onReply(node) }
                (c.heartedBy ?: com.nan.nantube.data.CommentsRepository.heartOf(c.id))?.let { HeartBadge(it) }
            }
        }
        Box {
            Box(Modifier.size(width = 36.dp, height = 30.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { menu = true }
                .semantics { contentDescription = "Tùy chọn bình luận" },
                contentAlignment = Alignment.TopCenter) {
                Canvas(Modifier.padding(top = 6.dp).size(16.dp, 5.dp)) {
                    val spacing = 5.dp.toPx()
                    for (dot in -1..1) {
                        drawCircle(Color.White, 1.45.dp.toPx(), Offset(center.x + dot * spacing, center.y))
                    }
                }
            }
            DropdownMenu(
                expanded = menu, onDismissRequest = { menu = false },
                shape = RoundedCornerShape(20.dp), containerColor = NanColors.Surface, tonalElevation = 0.dp
            ) {
                MenuItem(YtComment, "Phản hồi") { menu = false; onReply(node) }
                if (c.isOwnedBy(account)) MenuItem(Lucide.Pencil, "Chỉnh sửa") { menu = false; onEdit(node) }
                MenuItem(ShareIcon, "Chia sẻ") { menu = false; copy("https://www.youtube.com/watch?v=${video.id}&lc=${c.id}", "Đã sao chép liên kết bình luận") }
                if (c.isOwnedBy(account)) MenuItem(TrashIcon, "Xóa") { menu = false; onDelete(node) }
                else MenuItem(Lucide.Copy, "Sao chép") { menu = false; copy(c.author, "Đã sao chép tên") }
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
private fun SortItem(title: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().widthIn(min = 190.dp).plainClickable(onClick).padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = Color.White, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Text(hint, fontSize = 11.sp, color = NanColors.TextSecondary, modifier = Modifier)
        }
        Box(Modifier.padding(start = 12.dp).size(16.dp)) {
            if (selected) Icon(Lucide.Check, null, Modifier.fillMaxSize(), tint = Color.White)
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
private fun VoteButton(
    icon: ImageVector,
    count: String?,
    enabled: Boolean = true,
    effect: TapEffectState? = null,
    countKey: Any? = null,
    onClick: () -> Unit
) {
    val click = if (effect == null) {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        // Bỏ ripple hình chữ nhật của cả hàng; hiệu ứng chỉ vẽ quanh icon.
        Modifier.clickable(
            interactionSource = null, indication = null,
            enabled = enabled, role = Role.Button, onClick = onClick
        )
    }
    Row(click.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp).tapEffect(effect, ring = false), tint = Color.White)
        count?.let { CountText(it, countKey, fontSize = 13.sp, color = NanColors.TextSecondary, modifier = Modifier.padding(start = 6.dp)) }
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

/** Ô nhập: thu gọn là một viên "Bình luận..."; chạm vào thì hiện ảnh tài khoản, nút gửi và hàng emoji nhanh. */
@Composable
private fun CommentComposer(
    account: Account?,
    replyTo: ShortChatNode?,
    editTarget: ShortChatNode?,
    text: TextFieldValue,
    sending: Boolean,
    focus: FocusRequester,
    onText: (TextFieldValue) -> Unit,
    onCancelReply: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onSend: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val expanded = focused || replyTo != null || editTarget != null
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        if (editTarget != null || replyTo != null) Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (editTarget != null) "Đang chỉnh sửa bình luận" else "Đang trả lời ${replyTo?.item?.author}",
                fontSize = 12.sp, color = NanColors.TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            CommentIcon(IconClose, 28.dp, 16.dp, if (editTarget != null) onCancelEdit else onCancelReply)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (expanded) {
                Avatar((account?.name ?: "?").take(1).uppercase(), 32.dp, photoUrl = account?.photo)
                Spacer(Modifier.width(8.dp))
            }
            BasicTextField(
                value = text,
                onValueChange = onText,
                readOnly = editTarget != null && sending,
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
            if (editTarget != null) {
                Spacer(Modifier.width(10.dp))
                TextButton(
                    onClick = onSaveEdit,
                    enabled = !sending && !editTarget.writing && text.text.isNotBlank() &&
                        text.text.trim() != editTarget.item.text.trim()
                ) {
                    Text(if (sending) "Đang lưu…" else "Lưu")
                }
            } else if (expanded) {
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
