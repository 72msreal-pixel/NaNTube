package com.nan.nantube.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.nan.nantube.data.Account
import com.nan.nantube.data.NotificationItem
import com.nan.nantube.data.NotificationKind
import com.nan.nantube.data.NotificationRepository
import com.nan.nantube.data.PlaylistRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class NotifTab(val label: String) {
    All("Tất cả"), Comments("Bình luận"), Mentions("Lượt đề cập"), Own("Kênh của bạn")
}

private sealed interface Entry {
    data class Header(val text: String) : Entry
    data class Item(val n: NotificationItem) : Entry
}

private val BUCKETS = listOf("Hôm nay", "Tuần này", "Tháng này", "Cũ hơn")

private fun bucketOf(age: Double?): String = when {
    age == null -> "Cũ hơn"
    age < 24 -> "Hôm nay"
    age < 24 * 7 -> "Tuần này"
    age < 24 * 30 -> "Tháng này"
    else -> "Cũ hơn"
}

/**
 * Danh sách theo kiểu YouTube: nhóm "Quan trọng" (đang phát trực tiếp + thông báo mới chưa xem) rồi theo mốc thời gian
 * (Hôm nay, Tuần này, Tháng này). Tab khác "Tất cả" và khi đang tìm kiếm thì chỉ chia theo mốc thời gian.
 */
private fun buildEntries(items: List<NotificationItem>, tab: NotifTab, query: String): List<Entry> {
    val filtered = items
        .filter {
            when (tab) {
                NotifTab.All -> true
                NotifTab.Comments -> it.kind == NotificationKind.COMMENT
                NotifTab.Mentions -> it.kind == NotificationKind.MENTION
                NotifTab.Own -> it.kind == NotificationKind.OWN
            }
        }
        .filter { query.isBlank() || "${it.title} ${it.body}".contains(query.trim(), ignoreCase = true) }
    val important = if (tab == NotifTab.All && query.isBlank()) {
        filtered.filter { it.kind == NotificationKind.LIVE || (it.unread && (it.ageHours ?: Double.MAX_VALUE) <= 24.0) }.take(4)
    } else emptyList()
    val importantKeys = important.map { it.key }.toSet()
    val out = ArrayList<Entry>()
    if (important.isNotEmpty()) {
        out.add(Entry.Header("Quan trọng"))
        important.forEach { out.add(Entry.Item(it)) }
    }
    val rest = filtered.filter { it.key !in importantKeys }.groupBy { bucketOf(it.ageHours) }
    for (label in BUCKETS) {
        val group = rest[label] ?: continue
        out.add(Entry.Header(label))
        group.forEach { out.add(Entry.Item(it)) }
    }
    return out
}

/** Màn Thông báo (nút chuông), dựng giống màn Thông báo của app YouTube. */
@Composable
fun NotificationsScreen(account: Account?, onBack: () -> Unit, onSignIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var fromInbox by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(account != null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(NotifTab.All) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var moreOpen by remember { mutableStateOf(false) }
    val latestItems = rememberUpdatedState(items)

    LaunchedEffect(account?.id, reload) {
        if (account == null) return@LaunchedEffect
        loading = true
        error = null
        try {
            // Không báo lỗi: lỗi mạng thì cứ thử lại liên tục (vòng xoay), như bên Short.
            var attempt = 0
            while (true) {
                try {
                    val result = NotificationRepository.load(account, force = reload > 0)
                    items = result.items
                    fromInbox = result.fromInbox
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    attempt++
                    kotlinx.coroutines.delay((800L * attempt).coerceAtMost(4_000L))
                }
            }
        } finally {
            loading = false
        }
    }

    // Rời màn hình thì coi như đã xem: lần sau chấm xanh không còn ở các thông báo này.
    DisposableEffect(account?.id) {
        onDispose { NotificationRepository.markSeen(account, latestItems.value) }
    }

    BackHandler(enabled = searchOpen) {
        searchOpen = false
        query = ""
    }

    fun refreshFromCache() {
        scope.launch {
            try {
                items = NotificationRepository.load(account).items
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    Column(Modifier.overlayScreen()) {
        // ---- Thanh trên: Quay lại · Thông báo · Tìm kiếm · ⋮ ----
        Row(Modifier.fillMaxWidth().height(56.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable {
                    if (searchOpen) {
                        searchOpen = false
                        query = ""
                    } else onBack()
                },
                contentAlignment = Alignment.Center
            ) { Icon(IconArrowLeft, "Quay lại", Modifier.size(24.dp)) }
            if (searchOpen) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) Text("Tìm kiếm thông báo", color = NanColors.TextSecondary, fontSize = 18.sp)
                            inner()
                        }
                    }
                )
                if (query.isNotEmpty()) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).clickable { query = "" },
                        contentAlignment = Alignment.Center
                    ) { Icon(IconClose, "Xoá", Modifier.size(24.dp)) }
                }
            } else {
                Text("Thông báo", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.weight(1f))
                if (account != null) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).clickable { searchOpen = true },
                        contentAlignment = Alignment.Center
                    ) { Icon(Lucide.Search, "Tìm kiếm", Modifier.size(24.dp)) }
                    Box {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).clickable { moreOpen = true },
                            contentAlignment = Alignment.Center
                        ) { Icon(MoreVerticalIcon, "Thêm", Modifier.size(24.dp)) }
                        DropdownMenu(
                            expanded = moreOpen,
                            onDismissRequest = { moreOpen = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = NanColors.Surface,
                            tonalElevation = 0.dp
                        ) {
                            PlainMenuRow("Làm mới") {
                                moreOpen = false
                                reload++
                            }
                            if (NotificationRepository.hasLocalFilters(account)) {
                                PlainMenuRow("Hiện lại thông báo đã ẩn") {
                                    moreOpen = false
                                    NotificationRepository.restoreHidden(account)
                                    reload++
                                }
                            }
                        }
                    }
                }
            }
        }

        if (account == null) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Đăng nhập để xem thông báo của bạn.", color = NanColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFF1F1F1)).clickable(onClick = onSignIn)
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) { Text("Đăng nhập", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            }
        } else {
            // ---- Chip: Tất cả · Bình luận · Lượt đề cập · Kênh của bạn ----
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NotifTab.values().forEach { t ->
                    val selected = t == tab
                    Box(
                        Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Color(0xFFF1F1F1) else NanColors.Surface)
                            .clickable { tab = t }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(t.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (selected) Color.Black else Color.White)
                    }
                }
            }

            StatusBody(loading, items.isNotEmpty(), error, onRetry = { reload++ }) {
                val entries = remember(items, tab, query) { buildEntries(items, tab, query) }
                if (entries.isEmpty()) {
                    EmptyState(tab, searching = query.isNotBlank(), fromInbox = fromInbox)
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(entries, key = { e -> if (e is Entry.Header) "h:${e.text}" else (e as Entry.Item).n.key }) { e ->
                            when (e) {
                                is Entry.Header -> Text(
                                    e.text,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = NanColors.TextSecondary,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
                                )
                                is Entry.Item -> NotificationRow(
                                    n = e.n,
                                    onOpen = { e.n.url?.let { openLink(context, it) } },
                                    onWatchLater = {
                                        e.n.videoId?.let { id ->
                                            scope.launch {
                                                try {
                                                    PlaylistRepository.addToWatchLater(account, id)
                                                    toast("Đã lưu vào Xem sau")
                                                } catch (ex: CancellationException) {
                                                    throw ex
                                                } catch (ex: Exception) {
                                                    toast(ex.shortMessage ?: "Không lưu được video")
                                                }
                                            }
                                        }
                                    },
                                    onHide = {
                                        scope.launch {
                                            NotificationRepository.hide(account, e.n)
                                            refreshFromCache()
                                        }
                                    },
                                    onMute = {
                                        NotificationRepository.mute(account, e.n)
                                        toast("Đã tắt thông báo từ ${e.n.channel}")
                                        refreshFromCache()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(tab: NotifTab, searching: Boolean, fromInbox: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (tab == NotifTab.Mentions) {
            Text("@", fontSize = 120.sp, color = Color(0xFF666666), fontWeight = FontWeight.Light)
        } else {
            Icon(Lucide.Bell, null, Modifier.size(88.dp), tint = Color(0xFF666666))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            when {
                searching -> "Không có thông báo nào khớp"
                tab == NotifTab.Mentions -> "Chưa có lời đề cập nào"
                else -> "Chưa có thông báo nào"
            },
            fontSize = 18.sp,
            color = NanColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        if (!searching && tab != NotifTab.All && !fromInbox) {
            Spacer(Modifier.height(10.dp))
            Text(
                "YouTube không cho phiên đăng nhập này đọc hộp thư thông báo nên mục này chưa có dữ liệu; tab Tất cả đang hiện video mới của các kênh bạn đăng ký.",
                fontSize = 13.sp,
                color = Color(0xFF777777),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PlainMenuRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 16.sp,
        color = Color.White,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)
    )
}

@Composable
private fun MenuItemRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = Color.White)
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 16.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Một thông báo: chấm xanh (chưa xem) · avatar · [tên đậm / nội dung / thời gian] · ảnh bìa · nút ⋯. */
@Composable
private fun NotificationRow(
    n: NotificationItem,
    onOpen: () -> Unit,
    onWatchLater: () -> Unit,
    onHide: () -> Unit,
    onMute: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.width(16.dp).height(40.dp), contentAlignment = Alignment.Center) {
            if (n.unread) Box(Modifier.size(6.dp).clip(CircleShape).background(NanColors.Blue))
        }
        ChannelAvatar(
            name = n.channel,
            size = 40.dp,
            directUrl = n.avatar,
            channelId = n.channelId,
            videoId = n.videoId,
            fallbackLetter = n.title.ifBlank { n.body }
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (n.title.isNotBlank()) {
                Text(n.title, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (n.body.isNotBlank()) {
                Text(n.body, fontSize = 15.sp, lineHeight = 20.sp, maxLines = if (n.title.isBlank()) 4 else 3, overflow = TextOverflow.Ellipsis)
            }
            n.time?.let { Text(it, fontSize = 13.sp, color = NanColors.TextSecondary, modifier = Modifier.padding(top = 2.dp)) }
        }
        n.thumbnail?.let {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.width(100.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A4856)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    // Short là ảnh dọc: giữ nguyên tỉ lệ, hai bên để nền xám xanh như YouTube.
                    contentScale = if (n.isShort) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Box {
            MoreButton(width = 36.dp) { menuOpen = true }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = NanColors.Surface,
                tonalElevation = 0.dp
            ) {
                if (n.videoId != null) {
                    MenuItemRow(Lucide.Clock, "Lưu vào danh sách Xem sau") {
                        menuOpen = false
                        onWatchLater()
                    }
                }
                MenuItemRow(EyeOffIcon, "Ẩn thông báo này") {
                    menuOpen = false
                    onHide()
                }
                if (!n.channel.isNullOrBlank()) {
                    MenuItemRow(BellOffIcon, "Tắt tất cả thông báo từ ${n.channel}") {
                        menuOpen = false
                        onMute()
                    }
                }
            }
        }
    }
}
