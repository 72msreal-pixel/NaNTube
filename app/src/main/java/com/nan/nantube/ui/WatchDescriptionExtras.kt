package com.nan.nantube.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ThumbsUp
import com.nan.nantube.data.Account
import com.nan.nantube.data.ChannelNotify
import com.nan.nantube.data.SearchResult
import com.nan.nantube.data.ShortInfo
import com.nan.nantube.data.VideoItem

@Composable
internal fun SubscribeButton(
    channel: SearchResult.Channel?,
    subscribed: Boolean,
    account: Account?,
    effect: TapEffectState,
    onSubscribe: (SearchResult.Channel) -> Unit,
    onNotify: (SearchResult.Channel) -> Unit
) {
    if (subscribed && channel != null) {
        PillButton(
            "Đã đăng ký", Color(0x33FFFFFF), Color.White,
            leading = if (ChannelNotify.isOn(account, channel.id, channel.title)) BellRingIcon else BellOffIcon,
            trailing = Lucide.ChevronDown,
            modifier = Modifier.tapEffect(effect)
        ) { onNotify(channel) }
    } else {
        PillButton("Đăng ký", Color(0xFFF1F1F1), Color.Black, modifier = Modifier.tapEffect(effect)) {
            channel?.let(onSubscribe)
        }
    }
}

@Composable
private fun ExtraSection(title: String, subtitle: String, button: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 14.sp, color = NanColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
        Row(
            Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(50))
                .border(BorderStroke(1.dp, NanColors.Divider), RoundedCornerShape(50))
                .plainClickable(onClick).padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let { Icon(it, null, Modifier.size(20.dp), tint = Color.White); Spacer(Modifier.width(10.dp)) }
            Text(button, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp), tint = NanColors.TextSecondary)
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 16.sp, color = NanColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

/** Mục "Chi tiết video" như YouTube: Ngày / Lượt xem / Lượt thích, mỗi dòng một hàng (icon + nhãn bên trái, giá trị bên phải). */
@Composable
private fun VideoDetails(stats: DescStats) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp)) {
        Text("Chi tiết video", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        stats.date?.let { DetailRow(CalendarIcon, "Ngày", it) }
        stats.views?.let { DetailRow(PlaySquareIcon, "Lượt xem", it) }
        stats.likes?.let { DetailRow(Lucide.ThumbsUp, "Lượt thích", it) }
    }
}

@Composable
internal fun WatchDescriptionExtras(
    video: VideoItem,
    info: ShortInfo?,
    stats: DescStats,
    channel: SearchResult.Channel?,
    subscribed: Boolean,
    account: Account?,
    subEffect: TapEffectState,
    onLens: () -> Unit,
    onTranscript: () -> Unit,
    onSubscribe: (SearchResult.Channel) -> Unit,
    onNotify: (SearchResult.Channel) -> Unit
) {
    val author = info?.author ?: video.channel
    ExtraSection("Tìm kiếm trong video này", "Mua sản phẩm, dịch văn bản và nhiều tính năng khác", "Google Ống kính", LensIcon, onLens)
    ExtraSection("Bản chép lời", "Theo dõi nội dung video bằng bản chép lời.", "Hiện bản chép lời", onClick = onTranscript)
    Spacer(Modifier.height(28.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChannelAvatar(
            video.copy(channel = author, channelId = info?.channelId ?: video.channelId, avatar = info?.avatar ?: video.avatar),
            40.dp
        )
        Spacer(Modifier.width(12.dp))
        Text(
            author.orEmpty(),
            fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        SubscribeButton(channel, subscribed, account, subEffect, onSubscribe, onNotify)
    }
    VideoDetails(stats)
}
