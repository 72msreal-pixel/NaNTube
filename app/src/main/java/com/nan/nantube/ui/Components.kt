package com.nan.nantube.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Play
import com.nan.nantube.data.ChannelAvatars
import com.nan.nantube.data.VideoItem
import kotlinx.coroutines.delay

fun Modifier.plainClickable(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

@Composable
fun MoreButton(modifier: Modifier = Modifier, width: Dp = 36.dp, onClick: () -> Unit = {}) {
    Box(
        modifier.size(width = width, height = 36.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(width = 16.dp, height = 4.dp)) {
            val r = size.height / 2f
            for (x in listOf(r, size.width / 2f, size.width - r)) {
                drawCircle(Color.White, r, Offset(x, r))
            }
        }
    }
}

@Composable
fun Avatar(letter: String, size: Dp, modifier: Modifier = Modifier, photoUrl: String? = null) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFFFF4D4D), Color(0xFF8A0F1B)))),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
        if (!photoUrl.isNullOrBlank()) {
            // Ảnh tải xong sẽ phủ lên chữ cái; lỗi mạng thì chữ cái vẫn còn đó.
            AsyncImage(
                model = if (photoUrl.startsWith("//")) "https:$photoUrl" else photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Avatar của kênh đăng video: dùng ảnh YouTube kèm sẵn ([directUrl]) nếu có, không thì nhớ trong máy,
 * không nữa thì tra qua [ChannelAvatars] (đợi 150 ms để thẻ nào chỉ lướt qua khi cuộn nhanh thì khỏi gọi mạng).
 * Trong lúc chờ vẫn hiện chữ cái đầu tên kênh. Dùng chung cho mọi nơi có thẻ video.
 */
@Composable
fun ChannelAvatar(
    name: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    directUrl: String? = null,
    channelId: String? = null,
    panelId: String? = null,
    panelParams: String? = null,
    /** Chữ hiện khi chưa có ảnh và [name] trống (vd video không nêu tên kênh). */
    fallbackLetter: String? = null
) {
    var url by remember(directUrl, channelId, name) {
        mutableStateOf(directUrl?.takeIf { it.isNotBlank() } ?: ChannelAvatars.cached(channelId, name))
    }
    LaunchedEffect(directUrl, channelId, name, panelId, panelParams) {
        if (url == null) {
            delay(150)
            url = ChannelAvatars.resolve(ChannelAvatars.Request(channelId, name, panelId, panelParams))
        }
    }
    Avatar((name ?: fallbackLetter ?: "?").trim().take(1).uppercase(), size, modifier, photoUrl = url)
}

/** Avatar kênh của một video (thẻ trang chủ, kết quả tìm kiếm…). */
@Composable
fun ChannelAvatar(video: VideoItem, size: Dp, modifier: Modifier = Modifier) =
    ChannelAvatar(
        name = video.channel,
        size = size,
        modifier = modifier,
        directUrl = video.avatar,
        channelId = video.channelId,
        panelId = video.panelId,
        panelParams = video.panelParams,
        fallbackLetter = video.title
    )

@Composable
fun ThumbPlaceholder(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    duration: String? = null,
    cornerIcon: ImageVector? = null,
    progress: Float = 0f,
    /** Ảnh bìa (video đầu tiên của danh sách); chưa có hoặc lỗi thì vẫn thấy nền màu bên dưới. */
    imageUrl: String? = null
) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(colors))) {
        if (imageUrl.isNullOrBlank()) {
            Icon(
                Lucide.Play,
                null,
                Modifier.align(Alignment.Center).size(26.dp),
                tint = Color.White.copy(alpha = 0.55f)
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (cornerIcon != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(cornerIcon, null, Modifier.size(16.dp))
            }
        } else if (duration != null) {
            Text(
                duration,
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
        if (progress > 0f) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(Color.Red)
            )
        }
    }
}

/** Thanh đỏ mỏng dưới ảnh bìa thể hiện phần đã xem (0..1); không vẽ gì nếu chưa xem. */
@Composable
fun BoxScope.WatchProgress(progress: Float) {
    if (progress <= 0f) return
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth(progress.coerceAtMost(1f))
            .height(3.dp)
            .background(Color.Red)
    )
}

/** Dấu Shorts ở góc ảnh bìa (logo Shorts trong vòng tròn tối), thay cho thời lượng ở video ngắn. */
@Composable
fun BoxScope.ShortsBadge() {
    Icon(
        ShortsFilled,
        null,
        Modifier.align(Alignment.BottomEnd).padding(6.dp).size(28.dp).clip(CircleShape)
            .background(Color(0xCC000000)).padding(5.dp)
    )
}

/**
 * Nhãn thời lượng ở góc ảnh bìa. Video nhạc có thêm ký hiệu ♪ đứng trước giờ, như YouTube.
 * Không vẽ gì nếu không có thời lượng và cũng không phải nhạc.
 */
@Composable
fun BoxScope.DurationBadge(duration: String?, music: Boolean) {
    if (duration == null && !music) return
    Row(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (music) {
            Icon(Lucide.Music, null, Modifier.size(12.dp), tint = Color.White)
            if (duration != null) Spacer(Modifier.width(3.dp))
        }
        if (duration != null) {
            Text(duration, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }
}
