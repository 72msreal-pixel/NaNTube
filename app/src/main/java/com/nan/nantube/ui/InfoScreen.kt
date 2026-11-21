package com.nan.nantube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Clapperboard
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.ListVideo
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.ThumbsUp
import com.composables.icons.lucide.Video
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.nan.nantube.data.Account
import com.nan.nantube.data.VideoItem
import com.nan.nantube.data.VideoRepository

private data class LibraryItem(
    val title: String,
    val subtitle: String?,
    val icon: ImageVector,
    val colors: List<Color>,
    val hasMenu: Boolean = true
)

private val libraryItems = listOf(
    LibraryItem("Video đã thích", "Riêng tư", Lucide.ThumbsUp, listOf(Color(0xFF4A1F2E), Color(0xFF14070C))),
    LibraryItem("Xem sau", "Riêng tư", Lucide.Clock, listOf(Color(0xFF7A7A7A), Color(0xFF2E2E2E))),
    LibraryItem("Video của bạn", null, Lucide.Video, listOf(Color(0xFF3A3F2A), Color(0xFF12140D)), hasMenu = false),
    LibraryItem("Danh sách phát mới", "Riêng tư", Lucide.ListVideo, listOf(Color(0xFF5E4BA8), Color(0xFF1B1533)))
)

@Composable
fun InfoScreen(
    account: Account?,
    onAccountClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSignIn: () -> Unit
) {
    var watched by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var watchedLoading by remember { mutableStateOf(false) }
    LaunchedEffect(account?.id) {
        watched = emptyList()
        if (account == null) return@LaunchedEffect
        watchedLoading = true
        watched = try {
            VideoRepository.history(account).take(15)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        watchedLoading = false
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(onAccountClick, onSettingsClick, onSearchClick)
        LazyColumn(Modifier.fillMaxSize()) {
            item { ProfileHeader(account) }
            item {
                if (account == null) PillButton("Đăng nhập", onSignIn) else PillButton("Xem kênh") { }
            }
            item { SectionTitle("Video đã xem", withArrow = true) }
            item {
                if (watched.isEmpty()) {
                    Text(
                        when {
                            account == null -> "Đăng nhập để xem lịch sử video."
                            watchedLoading -> "Đang tải…"
                            else -> "Chưa có video nào."
                        },
                        fontSize = 14.sp,
                        color = NanColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(watched) { WatchedCard(it) }
                    }
                }
            }
            item { SectionTitle("Thư viện") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { LibraryChip("Gần đây", dropdown = true) }
                    item { LibraryChip("Đã tải xuống") }
                    item { LibraryChip("Danh sách phát") }
                    item { LibraryChip("Âm nhạc") }
                }
            }
            items(libraryItems) { LibraryRow(it) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TopBar(onAccountClick: () -> Unit, onSettingsClick: () -> Unit, onSearchClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val pill = RoundedCornerShape(50)
        Row(
            Modifier
                .clip(pill)
                .border(1.dp, NanColors.Divider, pill)
                .clickable(onClick = onAccountClick)
                .padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tài khoản", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(4.dp))
            Icon(Lucide.ChevronDown, null, Modifier.size(18.dp))
        }
        Spacer(Modifier.weight(1f))
        TopIcon(Lucide.Bell) { }
        TopIcon(Lucide.Search, onSearchClick)
        TopIcon(Lucide.Settings, onSettingsClick)
    }
}

@Composable
private fun TopIcon(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(24.dp))
    }
}

@Composable
private fun ProfileHeader(account: Account?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar((account?.name ?: "?").take(1).uppercase(), 72.dp, photoUrl = account?.photo)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(account?.name ?: "Chưa đăng nhập", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            val sub = account?.let { it.handle ?: it.email }
            if (sub != null) Text(sub, fontSize = 14.sp, color = NanColors.TextSecondary)
        }
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFF1F1F1))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionTitle(text: String, withArrow: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (withArrow) {
            Spacer(Modifier.width(6.dp))
            Icon(Lucide.ChevronRight, null, Modifier.size(22.dp))
        }
    }
}

@Composable
private fun WatchedCard(video: VideoItem) {
    val context = LocalContext.current
    Column(
        Modifier.width(162.dp).clickable {
            try {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(video.watchUrl))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(NanColors.Surface)) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (video.isShort) {
                Icon(
                    Lucide.Clapperboard,
                    null,
                    Modifier.align(Alignment.BottomEnd).padding(6.dp).size(28.dp).clip(CircleShape)
                        .background(Color(0xCC000000)).padding(5.dp)
                )
            } else if (video.duration != null) {
                Text(
                    video.duration,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Text(
            video.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        video.channel?.let {
            Text(it, fontSize = 12.sp, color = NanColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LibraryChip(text: String, dropdown: Boolean = false) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(NanColors.Surface)
            .clickable { }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (dropdown) {
            Spacer(Modifier.width(4.dp))
            Icon(Lucide.ChevronDown, null, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LibraryRow(item: LibraryItem) {
    Row(
        Modifier.fillMaxWidth().clickable { }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(164.dp)) {
            Box(
                Modifier
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(item.colors.first().copy(alpha = 0.7f))
            )
            ThumbPlaceholder(
                colors = item.colors,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                cornerIcon = item.icon
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(item.title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            if (item.subtitle != null) {
                Text(item.subtitle, fontSize = 14.sp, color = NanColors.TextSecondary)
            }
        }
        if (item.hasMenu) MoreButton()
    }
}
