package com.nan.nantube.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.border
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
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.nan.nantube.data.ShortsSeen
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

/**
 * Avatar tròn. Có [photoUrl] thì chỉ hiện nền xám trơn cho tới khi ảnh tải xong rồi hiện dần ảnh lên (không còn chữ cái
 * nhấp nháy trước ảnh); [pending] = đang tra ảnh nên cũng chỉ hiện nền xám. Chữ cái chỉ hiện khi chắc chắn không có ảnh
 * (không có đường dẫn, hoặc tải ảnh lỗi).
 */
@Composable
fun Avatar(letter: String, size: Dp, modifier: Modifier = Modifier, photoUrl: String? = null, pending: Boolean = false) {
    var failed by remember(photoUrl) { mutableStateOf(false) }
    val hasPhoto = !photoUrl.isNullOrBlank() && !failed
    val base = modifier.size(size).clip(CircleShape)
    Box(
        if (hasPhoto || pending) base.background(NanColors.Surface)
        else base.background(Brush.linearGradient(listOf(Color(0xFFFF4D4D), Color(0xFF8A0F1B)))),
        contentAlignment = Alignment.Center
    ) {
        if (!hasPhoto && !pending) {
            Text(letter, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
        }
        if (hasPhoto) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(if (photoUrl!!.startsWith("//")) "https:$photoUrl" else photoUrl)
                    .crossfade(150)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
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
    fallbackLetter: String? = null,
    /** Video chứa thẻ: để tra kênh đăng khi thẻ không nêu mã kênh (kênh nhạc, cộng tác nhiều nghệ sĩ). */
    videoId: String? = null,
    /** Ảnh dùng khi hoàn toàn không tra được avatar (vd ảnh bìa của video nhạc / tuyển tập). */
    lastResortUrl: String? = null
) {
    var url by remember(directUrl, channelId, name) {
        mutableStateOf(directUrl?.takeIf { it.isNotBlank() } ?: ChannelAvatars.cached(channelId, name))
    }
    // Chưa biết có ảnh hay không thì chỉ hiện nền xám; tra xong (kể cả không có) mới quyết định chữ cái hay ảnh.
    var resolved by remember(directUrl, channelId, name) { mutableStateOf(url != null) }
    LaunchedEffect(directUrl, channelId, name, panelId, panelParams) {
        if (url == null) {
            delay(150)
            url = ChannelAvatars.resolve(ChannelAvatars.Request(channelId, name, panelId, panelParams, videoId))
                ?: lastResortUrl
        }
        resolved = true
    }
    Avatar((name ?: fallbackLetter ?: "?").trim().take(1).uppercase(), size, modifier, photoUrl = url, pending = !resolved)
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
        fallbackLetter = video.title,
        videoId = video.id,
        // Video nhạc / tuyển tập nhiều nghệ sĩ mà không tra được kênh: dùng ảnh bìa tròn thay cho chữ cái.
        lastResortUrl = if (video.isMusic || video.channel?.contains(", ") == true) video.coverThumbnail else null
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

/**
 * Phần đã xem để vẽ thanh đỏ: YouTube báo thì theo YouTube; Short thì YouTube không có thanh này nên dùng bản nhớ trong máy
 * ([ShortsSeen], Short đã lên hình ở bất cứ đâu trong app) → đầy thanh, giống đánh dấu "đã xem" của meetube.
 */
fun VideoItem.watchedProgress(): Float =
    if (progress > 0f) progress else if (isShort && ShortsSeen.contains(id)) 1f else 0f

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
fun BoxScope.DurationBadge(duration: String?, music: Boolean, live: Boolean = false) {
    val isLive = live
    if (isLive) {
        // Video đang phát trực tiếp: nhãn đỏ có biểu tượng phát sóng + "TRỰC TIẾP" như YouTube.
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE62117))
                .padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiveIcon(14.dp, Color.White)
            Spacer(Modifier.width(4.dp))
            Text("TRỰC TIẾP", fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        return
    }
    if (duration == null && !music) return
    Row(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (music) {
            Icon(Lucide.Music, null, Modifier.size(16.dp), tint = Color.White)
            if (duration != null) Spacer(Modifier.width(5.dp))
        }
        if (duration != null) {
            Text(duration, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }
}

/** Biểu tượng phát sóng ((•)) vẽ tay: chấm giữa và hai cặp cung hai bên, như biểu tượng live của YouTube. */
@Composable
fun LiveIcon(size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val c = center
        val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
        drawCircle(tint, radius = w * 0.13f, center = c)
        for (r in listOf(w * 0.29f, w * 0.46f)) {
            val box = Size(r * 2, r * 2)
            val topLeft = Offset(c.x - r, c.y - r)
            drawArc(tint, -45f, 90f, false, topLeft, box, style = stroke)
            drawArc(tint, 135f, 90f, false, topLeft, box, style = stroke)
        }
    }
}

/** Video có đang phát trực tiếp không: chỉ theo cờ [VideoItem.live] do bộ phân tích đặt từ nhãn LIVE của YouTube (như meetube). */
val VideoItem.isLiveNow: Boolean
    get() = live

/**
 * Avatar kênh của video: đang live thì có vòng đỏ quanh avatar và chấm phát sóng đỏ ở đáy, để nhận ra livestream.
 * Kích thước ngoài giữ nguyên [size] nên không xô lệch bố cục.
 */
@Composable
fun LiveAwareAvatar(video: VideoItem, size: Dp) {
    if (!video.isLiveNow) {
        ChannelAvatar(video, size)
        return
    }
    val red = Color(0xFFE62117)
    Box(Modifier.size(size)) {
        Box(
            Modifier.fillMaxSize().border(2.dp, red, CircleShape).padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            ChannelAvatar(video, size - 10.dp)
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 0.dp)
                .clip(RoundedCornerShape(50))
                .background(red)
                .border(1.5.dp, Color(0xFF0F0F0F), RoundedCornerShape(50))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            LiveIcon(9.dp, Color.White)
        }
    }
}

private val connectFail = Regex("^(Failed to connect) to .*")

/** Lỗi mạng hay kèm host + IP ("Failed to connect to www.youtube.com/1.2.3.4:443"), người dùng chỉ cần phần đầu. */
val Throwable.shortMessage: String?
    get() = message?.replace(connectFail, "$1")
