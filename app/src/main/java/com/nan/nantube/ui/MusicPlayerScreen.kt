package com.nan.nantube.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import kotlinx.coroutines.delay

private fun icon(name: String, filled: Boolean = false, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()

private val PlayIcon by lazy { icon("MpPlay", true, "M6 3l14 9-14 9V3z") }
private val PauseIcon by lazy { icon("MpPause", true, "M6 4h4v16H6z", "M14 4h4v16h-4z") }
private val PrevIcon by lazy { icon("MpPrev", true, "M19 20L9 12l10-8v16z", "M5 19V5") }
private val NextIcon by lazy { icon("MpNext", true, "M5 4l10 8-10 8V4z", "M19 5v14") }
private val ShuffleIcon by lazy {
    icon("MpShuffle", false, "M2 18h1.4c1.3 0 2.5-.6 3.3-1.7l6.1-8.6c.7-1.1 2-1.7 3.3-1.7H22", "M18 2l4 4-4 4",
        "M2 6h1.9c1.5 0 2.9.9 3.6 2.2", "M22 18h-5.9c-1.3 0-2.6-.7-3.3-1.8l-.5-.8", "M18 14l4 4-4 4")
}
private val RepeatIcon by lazy { icon("MpRepeat", false, "M17 2l4 4-4 4", "M3 11v-1a4 4 0 0 1 4-4h14", "M7 22l-4-4 4-4", "M21 13v1a4 4 0 0 1-4 4H3") }
private val RepeatOneIcon by lazy {
    icon("MpRepeatOne", false, "M17 2l4 4-4 4", "M3 11v-1a4 4 0 0 1 4-4h14", "M7 22l-4-4 4-4", "M21 13v1a4 4 0 0 1-4 4H3", "M11 10h1v4")
}
private const val HEART = "M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7z"
private val MpHeartIcon by lazy { icon("MpHeart", false, HEART) }
private val MpHeartFilledIcon by lazy { icon("MpHeartFilled", true, HEART) }
private val MpShareIcon by lazy { icon("MpShare", false, "M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8", "M16 6l-4-4-4 4", "M12 2v13") }

private val Accent = Color(0xFFFF4E45)

private enum class MusicPanel { Player, Transcript, Queue }
private val TranscriptIcon by lazy {
    icon("MpTranscript", false, "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M7 8h10 M7 12h10 M7 16h6")
}

/** Thích / bỏ thích như nút tim bên Short: nảy tim, khi vừa thích thêm chùm chấm confetti bung quanh icon. */
private fun likeWithEffect(effect: TapEffectState) {
    val before = MusicPlayer.liked
    MusicPlayer.toggleLike()
    if (MusicPlayer.liked != before) effect.play(burst = MusicPlayer.liked)
}

/** Góc quay của đĩa: quay đều khi đang phát, đứng yên đúng góc đó khi tạm dừng. */
@Composable
private fun rememberDiscAngle(spinning: Boolean): Animatable<Float, AnimationVector1D> {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (spinning) {
            while (true) {
                angle.animateTo(angle.value + 360f, tween(16_000, easing = LinearEasing))
                angle.snapTo(angle.value % 360f)
            }
        }
    }
    return angle
}

/**
 * Màn phát nhạc: nền và màu kiểu YouTube (tối, chữ trắng), bố cục kiểu Zing MP3 (đĩa quay ở giữa, tên bài, thanh tua,
 * hàng điều khiển). Không có phần quảng cáo / mua gói.
 */
@Composable
fun MusicPlayerScreen() {
    val track = MusicPlayer.current ?: return
    val context = LocalContext.current
    var panel by remember { mutableStateOf(MusicPanel.Player) }
    var more by remember(track) { mutableStateOf(false) }
    var position by remember(track.id) { mutableLongStateOf(0L) }
    var duration by remember(track.id) { mutableLongStateOf(0L) }
    var dragging by remember(track.id) { mutableStateOf(false) }
    var dragValue by remember(track.id) { mutableFloatStateOf(0f) }
    val fromYouTube = track.id.length == 11 && !track.local
    BackHandler(enabled = panel != MusicPanel.Player) { panel = MusicPanel.Player }

    fun show(target: MusicPanel) {
        panel = if (panel == target) MusicPanel.Player else target
    }
    val tv = { TvScreen.show(track.asVideo()) }
    val download = { MusicPlayer.openDownload(track) }
    LaunchedEffect(track.id) {
        while (true) {
            if (!dragging) { position = MusicPlayer.position(); duration = MusicPlayer.duration() }
            delay(if (panel == MusicPanel.Transcript) 125 else 300)
        }
    }
    val angle = rememberDiscAngle(MusicPlayer.playing)
    val heartFx = rememberTapEffect()

    Box(Modifier.fillMaxSize().background(NanColors.Background).pointerInput(Unit) { detectTapGestures { } }) {
        // Nền: ảnh bìa làm mờ + phủ tối, không có ảnh thì chuyển màu đỏ sẫm → đen.
        if (track.art != null) {
            AsyncImage(
                model = track.art, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(70.dp).alpha(0.55f)
            )
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF3A1412), NanColors.Background))))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color(0xCC0F0F0F)))))

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { MusicPlayer.expanded = false }, contentAlignment = Alignment.Center) {
                    Icon(Lucide.ChevronDown, "Thu nhỏ", Modifier.size(30.dp), tint = Color.White)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PHÁT TỪ", fontSize = 11.sp, letterSpacing = 1.5.sp, color = NanColors.TextSecondary)
                    Text(MusicPlayer.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Không .clip ở đây: chùm confetti vẽ ra ngoài khung icon (giống nút tim của Short).
                Box(
                    Modifier.size(44.dp).clickable(interactionSource = null, indication = null) { likeWithEffect(heartFx) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (MusicPlayer.liked) MpHeartFilledIcon else MpHeartIcon, "Thích",
                        Modifier.size(24.dp).tapEffect(heartFx, Accent, ring = false),
                        tint = if (MusicPlayer.liked) Accent else Color.White
                    )
                }
                Box(Modifier.size(44.dp).clip(CircleShape).clickable {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "${track.title}\nhttps://youtu.be/${track.id}")
                    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, contentAlignment = Alignment.Center) {
                    Icon(MpShareIcon, "Chia sẻ", Modifier.size(22.dp), tint = Color.White)
                }
                Box {
                    Control(MoreVerticalIcon, "Tùy chọn", 24.dp, Color.White, touchSize = 44.dp) { more = true }
                    DropdownMenu(
                        expanded = more, onDismissRequest = { more = false },
                        shape = RoundedCornerShape(16.dp), containerColor = NanColors.Surface, tonalElevation = 0.dp
                    ) {
                        MusicMenuItem(TranscriptIcon, "Hiện bản chép lời") { more = false; panel = MusicPanel.Transcript }
                        MusicMenuItem(CastIcon, "Kết nối TV", fromYouTube) { more = false; tv() }
                        MusicMenuItem(YtDownload, "Tải xuống", fromYouTube) { more = false; download() }
                        MusicMenuItem(IconPlaylist, "Nhạc tiếp theo") { more = false; panel = MusicPanel.Queue }
                    }
                }
            }

            when (panel) {
                MusicPanel.Player -> BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    val discSize = minOf(maxWidth, maxHeight)
                    if (discSize > 0.dp) Disc(track.art, angle, Modifier.size(discSize))
                }
                MusicPanel.Transcript -> MusicTranscript(track, position, duration > 0L, Modifier.weight(1f)) {
                    MusicPlayer.seekTo(it)
                    position = it
                }
                MusicPanel.Queue -> MusicQueue(Modifier.weight(1f))
            }

            if (panel == MusicPanel.Player) Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    track.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
                Text(
                    MusicPlayer.error ?: track.artist ?: "Không rõ nghệ sĩ",
                    fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    color = if (MusicPlayer.error != null) Accent else NanColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            val total = duration.coerceAtLeast(1L)
            NanSlider(
                value = if (dragging) dragValue else (position.toFloat() / total).coerceIn(0f, 1f),
                onValueChange = { dragging = true; dragValue = it; position = (it * total).toLong() },
                onValueChangeFinished = { MusicPlayer.seekTo((dragValue * total).toLong()); dragging = false },
                enabled = duration > 0,
                modifier = Modifier.padding(top = 16.dp)
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(mpClock(position), fontSize = 13.sp, color = NanColors.TextSoft)
                Text(mpClock(duration), fontSize = 13.sp, color = NanColors.TextSoft)
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Control(ShuffleIcon, "Phát ngẫu nhiên", 26.dp, if (MusicPlayer.shuffle) Accent else Color.White) { MusicPlayer.toggleShuffle() }
                Control(PrevIcon, "Bài trước", 34.dp, Color.White) { MusicPlayer.prev() }
                Box(
                    Modifier.size(72.dp).border(2.dp, Color.White, CircleShape).clip(CircleShape).clickable { MusicPlayer.toggle() },
                    contentAlignment = Alignment.Center
                ) {
                    if (MusicPlayer.loading && !MusicPlayer.playing) {
                        androidx.compose.material3.CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(30.dp))
                    } else {
                        Icon(if (MusicPlayer.playing) PauseIcon else PlayIcon, if (MusicPlayer.playing) "Tạm dừng" else "Phát", Modifier.size(34.dp), tint = Color.White)
                    }
                }
                Control(NextIcon, "Bài kế", 34.dp, Color.White) { MusicPlayer.next() }
                Control(
                    if (MusicPlayer.repeat == RepeatKind.One) RepeatOneIcon else RepeatIcon, "Lặp lại", 26.dp,
                    if (MusicPlayer.repeat == RepeatKind.Off) Color(0x88FFFFFF) else Accent
                ) { MusicPlayer.cycleRepeat() }
            }

            Canvas(Modifier.fillMaxWidth().height(1.dp)) { drawRect(Color.White.copy(alpha = 0.14f)) }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp)) {
                MusicAction(TranscriptIcon, "Chép lời", Modifier.weight(1f), selected = panel == MusicPanel.Transcript) { show(MusicPanel.Transcript) }
                MusicAction(CastIcon, "Kết nối TV", Modifier.weight(1f), enabled = fromYouTube, onClick = tv)
                MusicAction(YtDownload, "Tải xuống", Modifier.weight(1f), enabled = fromYouTube, onClick = download)
                MusicAction(IconPlaylist, "Tiếp theo", Modifier.weight(1f), selected = panel == MusicPanel.Queue) { show(MusicPanel.Queue) }
            }
        }
    }
}

@Composable
private fun MusicMenuItem(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 14.sp) },
        leadingIcon = { Icon(icon, null, Modifier.size(22.dp)) },
        enabled = enabled, onClick = onClick
    )
}

@Composable
private fun MusicAction(
    icon: ImageVector, label: String, modifier: Modifier = Modifier,
    selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit
) {
    val tint = when {
        !enabled -> NanColors.TextSecondary.copy(alpha = 0.5f)
        selected -> Color.White
        else -> NanColors.TextSoft
    }
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 7.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Text(label, fontSize = 11.sp, color = tint, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Control(icon: ImageVector, desc: String, size: Dp, tint: Color, touchSize: Dp = 52.dp, onClick: () -> Unit) {
    Box(Modifier.size(touchSize).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, Modifier.size(size), tint = tint)
    }
}

private fun mpClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * Đĩa nhạc quay. Có ảnh: ảnh bìa tròn trên viền đĩa đen, có lỗ trục nhỏ ở giữa. Không có ảnh: đĩa than đen có rãnh,
 * nhãn đỏ chia cung với nốt nhạc ở giữa (khác hẳn đĩa CD nhiều màu của Zing), thêm vệt sáng để thấy rõ đĩa đang quay.
 */
@Composable
private fun Disc(art: String?, angle: Animatable<Float, AnimationVector1D>, modifier: Modifier) {
    Box(modifier.graphicsLayer { rotationZ = angle.value }.clip(CircleShape), contentAlignment = Alignment.Center) {
        if (art != null) {
            Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A)))
            AsyncImage(
                model = art, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().padding(10.dp).clip(CircleShape)
            )
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                drawCircle(Color(0xFF0A0A0A), radius = size.minDimension * 0.045f, center = c)
                drawCircle(Color(0x55FFFFFF), radius = size.minDimension * 0.045f, center = c, style = Stroke(width = 2f))
                drawCircle(Color(0x66FFFFFF), radius = size.minDimension / 2 - 2f, center = c, style = Stroke(width = 3f))
            }
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                val c = center
                drawCircle(Color(0xFF121212), radius = r, center = c)
                var g = r * 0.42f
                while (g < r - 6f) {
                    drawCircle(Color(0x14FFFFFF), radius = g, center = c, style = Stroke(width = 1.2f))
                    g += r * 0.035f
                }
                // Vệt sáng trên vinyl: hai cung đối xứng cho thấy chuyển động quay.
                val arcBox = Size((r - 14f) * 2, (r - 14f) * 2)
                val arcTop = Offset(c.x - arcBox.width / 2, c.y - arcBox.height / 2)
                drawArc(Color(0x22FFFFFF), 200f, 40f, false, arcTop, arcBox, style = Stroke(width = r * 0.22f))
                drawArc(Color(0x22FFFFFF), 20f, 40f, false, arcTop, arcBox, style = Stroke(width = r * 0.22f))
                // Nhãn đỏ chia cung.
                val label = r * 0.40f
                drawCircle(
                    Brush.sweepGradient(
                        listOf(Accent, Color(0xFFB3261E), Accent, Color(0xFFB3261E), Accent), c
                    ),
                    radius = label, center = c
                )
                drawCircle(Color(0x33FFFFFF), radius = label, center = c, style = Stroke(width = 2f))
                drawCircle(Color(0xFF121212), radius = r * 0.035f, center = c)
            }
            Icon(Lucide.Music, null, Modifier.fillMaxSize(0.17f), tint = Color.White)
        }
    }
}


/**
 * Thanh nhỏ khi màn phát nhạc đã thu nhỏ, bố cục như Zing: thẻ đen bo góc có viền xám mỏng, ảnh bìa nằm trong ô vuông bo góc
 * có viền mỏng và cao hơn thẻ một chút, đĩa nhô ra sau ảnh 1/4 đường kính (quay khi đang phát), vạch tiến độ ở đáy thẻ bắt đầu
 * từ mép ảnh. Nút tim / phát-dừng / bài kế đứng sát nhau. Chạm để mở rộng; vuốt ngang: thẻ trượt theo ngón tay rồi trượt hẳn
 * ra ngoài và tắt nhạc.
 */
@Composable
fun MiniMusicBar() {
    val track = MusicPlayer.current ?: return
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(track.id) {
        while (true) {
            val d = MusicPlayer.duration()
            progress = if (d > 0) (MusicPlayer.position().toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }
    val angle = rememberDiscAngle(MusicPlayer.playing)
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val heartFx = rememberTapEffect()
    val press = remember { MutableInteractionSource() }

    val barHeight = 48.dp
    val artSize = 54.dp
    val disc = 44.dp
    val peek = 16.dp // đĩa nhô ra khỏi mép phải ảnh (như Zing MP3)
    val cardShape = RoundedCornerShape(14.dp)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .graphicsLayer { alpha = (1f - kotlin.math.abs(offsetX.value) / widthPx * 0.8f).coerceIn(0f, 1f) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (kotlin.math.abs(offsetX.value) > widthPx * 0.3f) {
                                offsetX.animateTo(kotlin.math.sign(offsetX.value) * widthPx * 1.1f, tween(180))
                                MusicPlayer.close()
                            } else offsetX.animateTo(0f, tween(160))
                        }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f, tween(160)) } },
                    onHorizontalDrag = { change, amount -> change.consume(); scope.launch { offsetX.snapTo(offsetX.value + amount) } }
                )
            }
            // Không vẽ ripple ở đây (sẽ thành hình chữ nhật cả khung); hiệu ứng nhấn vẽ riêng ở lớp bo góc cuối cùng.
            .clickable(interactionSource = press, indication = null) { MusicPlayer.expanded = true },
        contentAlignment = Alignment.CenterStart
    ) {
        // Thẻ đen có viền xám mỏng; vạch tiến độ nằm ở đáy, bắt đầu từ mép phải của ảnh.
        Box(
            Modifier.fillMaxWidth().height(barHeight).clip(cardShape).background(Color.Black)
                .border(1.dp, Color(0xFF3A3A3A), cardShape)
        ) {
            Box(Modifier.align(Alignment.BottomStart).padding(start = artSize).fillMaxWidth().height(2.5.dp)) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Accent))
            }
        }
        Row(Modifier.fillMaxWidth().height(artSize), verticalAlignment = Alignment.CenterVertically) {
            // Đĩa nằm dưới ảnh, chỉ nhô ra 1/4 đường kính.
            Box(Modifier.size(artSize), contentAlignment = Alignment.CenterStart) {
                Canvas(Modifier.offset(x = artSize - disc + peek).requiredSize(disc).graphicsLayer { rotationZ = angle.value }) {
                    val r = size.minDimension / 2
                    drawCircle(Color(0xFF111111), radius = r, center = center)
                    drawCircle(Color(0x33FFFFFF), radius = r - 1f, center = center, style = Stroke(width = 1.2f))
                    drawCircle(Color(0x22FFFFFF), radius = r * 0.82f, center = center, style = Stroke(width = 1.2f))
                    drawCircle(Color(0x18FFFFFF), radius = r * 0.62f, center = center, style = Stroke(width = 1.2f))
                    drawCircle(Accent, radius = r * 0.34f, center = center)
                    drawCircle(Color(0xFF111111), radius = r * 0.06f, center = center)
                }
                Box(
                    Modifier.size(artSize).clip(RoundedCornerShape(12.dp)).background(NanColors.Surface)
                        .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (track.art != null) AsyncImage(track.art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(Lucide.Music, null, Modifier.size(22.dp), tint = Color.White)
                }
            }
            Column(Modifier.weight(1f).padding(start = 8.dp + peek, end = 2.dp)) {
                Text(track.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    MusicPlayer.error ?: track.artist ?: "Không rõ nghệ sĩ", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (MusicPlayer.error != null) Accent else NanColors.TextSecondary
                )
            }
            // Nút tim không clip để chùm confetti bung ra ngoài như bên Short.
            Box(
                Modifier.size(34.dp).clickable(interactionSource = null, indication = null) { likeWithEffect(heartFx) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (MusicPlayer.liked) MpHeartFilledIcon else MpHeartIcon, "Thích",
                    Modifier.size(20.dp).tapEffect(heartFx, Accent, ring = false),
                    tint = if (MusicPlayer.liked) Accent else Color.White
                )
            }
            MiniButton(if (MusicPlayer.playing) PauseIcon else PlayIcon, "Phát / tạm dừng", Color.White) { MusicPlayer.toggle() }
            MiniButton(NextIcon, "Bài kế", Color.White) { MusicPlayer.next() }
            Box(Modifier.size(4.dp))
        }
        // Lớp nhấn / giữ: ánh sáng bo góc theo đúng hình thẻ, nằm trên cùng và không chặn thao tác chạm.
        Box(Modifier.fillMaxWidth().height(barHeight).clip(cardShape).indication(press, ripple(color = Color.White)))
    }
}

/** Nút nhỏ của thanh mini: vùng chạm 34dp, đứng sát nhau. */
@Composable
private fun MiniButton(icon: ImageVector, desc: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, Modifier.size(20.dp), tint = tint)
    }
}
