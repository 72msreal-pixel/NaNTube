package com.nan.nantube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nan.nantube.data.Account
import com.nan.nantube.data.NotificationItem
import com.nan.nantube.data.NotificationRepository
import kotlinx.coroutines.CancellationException

/** Màn Thông báo (nút chuông): danh sách thông báo của tài khoản, chạm để mở video / liên kết trên YouTube. */
@Composable
fun NotificationsScreen(account: Account?, onBack: () -> Unit, onSignIn: () -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var loading by remember { mutableStateOf(account != null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(account?.id, reload) {
        if (account == null) return@LaunchedEffect
        loading = true
        error = null
        try {
            items = NotificationRepository.load(account)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Không tải được thông báo."
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(NanColors.Background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Icon(IconArrowLeft, "Quay lại", Modifier.size(24.dp)) }
            Text("Thông báo", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (account == null) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text("Đăng nhập để xem thông báo của bạn.", color = NanColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFF1F1F1)).clickable(onClick = onSignIn)
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) { Text("Đăng nhập", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            }
        } else {
            StatusBody(loading, items.isNotEmpty(), error, onRetry = { reload++ }) {
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Chưa có thông báo nào.", color = NanColors.TextSecondary, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(items, key = { it.key }) { n -> NotificationRow(n) { n.url?.let { openLink(context, it) } } }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: NotificationItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (n.unread) Color(0x22FFFFFF) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (n.unread) NanColors.Blue else Color.Transparent))
        Spacer(Modifier.width(8.dp))
        Avatar(n.message.take(1).uppercase(), 48.dp, photoUrl = n.avatar)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(n.message, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            n.time?.let { Text(it, fontSize = 12.sp, color = NanColors.TextSecondary, modifier = Modifier.padding(top = 2.dp)) }
        }
        n.thumbnail?.let {
            Spacer(Modifier.width(12.dp))
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(NanColors.Surface)
            )
        }
    }
}
