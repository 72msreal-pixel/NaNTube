package com.nan.nantube.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.nan.nantube.DownloadService
import com.nan.nantube.data.Account
import com.nan.nantube.data.AudioFormat
import com.nan.nantube.data.DownloadInfo
import com.nan.nantube.data.DownloadRequest
import com.nan.nantube.data.DownloadText
import com.nan.nantube.data.VideoDownloads
import com.nan.nantube.data.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Bảng Tải xuống của video: nút "Tải xuống" ở menu ba chấm không tải thẳng nữa mà mở bảng này.
 * AppRoot vẽ [DownloadSheet] phủ lên cả thanh dưới, còn menu chỉ gọi [open].
 */
object DownloadPanel {
    var video by mutableStateOf<VideoItem?>(null)
        private set
    var account: Account? = null
        private set

    fun open(video: VideoItem, account: Account?) {
        this.account = account
        this.video = video
    }

    fun close() {
        video = null
    }
}

private val Scrim = Color(0x99000000)
private const val FLING = 1200f

/**
 * Trạng thái kéo của bảng. [lift] > 0 là bảng được kéo cao thêm (tối đa tới hết [maxPx]), < 0 là bảng đang bị kéo xuống để đóng.
 * Thả tay thì về mốc gần nhất: đóng / cao tự nhiên / mở hết cỡ.
 */
private class SheetDrag(private val scope: CoroutineScope, private val onDismiss: () -> Unit) {
    val lift = Animatable(0f)
    var peekPx = 0
    var maxPx = 0

    private val room get() = (maxPx - peekPx).coerceAtLeast(0).toFloat()

    fun pulled(): Float = if (peekPx > 0) (-lift.value / peekPx).coerceIn(0f, 1f) else 0f

    fun drag(dy: Float) {
        scope.launch { lift.snapTo((lift.value - dy).coerceIn(-peekPx.toFloat(), room)) }
    }

    fun release(velocity: Float) {
        val v = lift.value
        val target = when {
            velocity > FLING -> if (v > 0f) 0f else -peekPx.toFloat()
            velocity < -FLING -> if (v < 0f) 0f else room
            v < -peekPx * 0.35f -> -peekPx.toFloat()
            v > room / 2f -> room
            else -> 0f
        }
        scope.launch {
            lift.animateTo(target, tween(220))
            if (target < 0f) onDismiss()
        }
    }
}

/** Các trang của bảng; trang sau mở chồng lên trang trước, nút quay lại / vuốt Back lùi một trang. */
private enum class Step { Root, Video, Quality, Audio, AudioType, Clip, ClipKind }

private fun lucide(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()

private val VideoIcon: ImageVector by lazy {
    lucide(
        "DownloadVideo",
        "m16 13 5.223 3.482a.5.5 0 0 0 .777-.416V7.87a.5.5 0 0 0-.752-.432L16 10.5",
        "M4 6h10a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z"
    )
}
private val MusicIcon: ImageVector by lazy {
    lucide("DownloadMusic", "M9 18V5l12-2v13", "M3 18a3 3 0 1 0 6 0a3 3 0 1 0-6 0", "M15 16a3 3 0 1 0 6 0a3 3 0 1 0-6 0")
}
private val ScissorsIcon: ImageVector by lazy {
    lucide(
        "DownloadClip",
        "M3 6a3 3 0 1 0 6 0a3 3 0 1 0-6 0", "M3 18a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
        "M20 4 8.12 15.88", "M14.47 14.48 20 20", "M8.12 8.12 12 12"
    )
}
private val WaveIcon: ImageVector by lazy {
    lucide("DownloadWave", "M2 10v3", "M6 6v11", "M10 3v18", "M14 8v7", "M18 5v13", "M22 10v3")
}

@Composable
fun DownloadSheet() {
    val video = DownloadPanel.video ?: return
    key(video.id) { DownloadSheetContent(video, DownloadPanel.account, onDismiss = { DownloadPanel.close() }) }
}

@Composable
private fun DownloadSheetContent(video: VideoItem, account: Account?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val stack = remember { mutableStateListOf(Step.Root) }
    val step = stack.last()
    var info by remember { mutableStateOf<DownloadInfo?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    // null = mức cao nhất tải được.
    var height by remember { mutableStateOf<Int?>(null) }
    var audioFormat by remember { mutableStateOf(AudioFormat.M4A) }
    var startText by remember { mutableStateOf("0:00") }
    var endText by remember { mutableStateOf("") }
    var clipStart by remember { mutableLongStateOf(0L) }
    var clipEnd by remember { mutableLongStateOf(0L) }
    val inClip = Step.ClipKind in stack
    val scope = rememberCoroutineScope()
    val drag = remember { SheetDrag(scope, onDismiss) }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else onDismiss()
    }
    BackHandler { back() }

    // Lấy các mức đồ họa ngay khi mở bảng, để tới trang chọn thì đã có sẵn.
    LaunchedEffect(video.id, account?.id) {
        try {
            info = VideoDownloads.info(context.applicationContext, account, video.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = e.shortMessage ?: "Không lấy được thông tin để tải video này."
        }
    }

    val lengthMs = info?.lengthMs?.takeIf { it > 0 } ?: DownloadText.parseTime(video.duration).takeIf { it > 0 } ?: -1L
    LaunchedEffect(lengthMs) {
        if (endText.isEmpty() && lengthMs > 0) endText = DownloadText.duration(lengthMs)
    }

    /** Ước dung lượng: đoạn cắt chỉ tải phần mảnh phủ đoạn đó nên tính theo tỉ lệ thời lượng. */
    fun estimate(bytes: Long): String {
        if (bytes <= 0) return ""
        val scaled = if (inClip && lengthMs > 0) bytes * (clipEnd - clipStart).coerceAtLeast(0) / lengthMs else bytes
        return "~" + DownloadText.bytes(scaled)
    }

    fun startDownload(audioOnly: Boolean) {
        val request = DownloadRequest(
            audioOnly = audioOnly,
            height = if (audioOnly) null else height,
            audioFormat = audioFormat,
            clipStartMs = if (inClip) clipStart else 0L,
            clipEndMs = if (inClip) clipEnd else 0L
        )
        DownloadService.start(context.applicationContext, account?.id, video, request)
        Toast.makeText(
            context.applicationContext,
            if (audioOnly) "Đang chuẩn bị tải âm thanh" else "Đang chuẩn bị tải video",
            Toast.LENGTH_SHORT
        ).show()
        onDismiss()
    }

    val topQuality = info?.qualities?.firstOrNull()
    val qualityLabel = height?.let { "${it}p" } ?: topQuality?.label ?: if (failed != null) "Không có" else "Đang tải…"
    val qualityBytes = (info?.qualities?.firstOrNull { it.height == (height ?: topQuality?.height) })?.bytes ?: -1L

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Scrim, alpha = 1f - drag.pulled()) }.plainClickable(onDismiss)) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)
                    .graphicsLayer { translationY = (-drag.lift.value).coerceAtLeast(0f) }
                    .layout { measurable, c ->
                        drag.maxPx = c.maxHeight
                        val lift = drag.lift.value
                        val fixed = if (lift > 0f) (drag.peekPx + lift).toInt().coerceAtMost(c.maxHeight) else null
                        val p = measurable.measure(if (fixed != null) c.copy(minHeight = fixed, maxHeight = fixed) else c)
                        if (fixed == null) drag.peekPx = p.height
                        layout(p.width, p.height) { p.place(0, 0) }
                    }
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(NanColors.Background)
                    .plainClickable { }
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                PanelHandle(drag::drag, drag::release)
                Header(
                    title = when (step) {
                        Step.Root -> "Tải xuống"
                        Step.Video -> "Tải video"
                        Step.Quality -> "Đồ họa"
                        Step.Audio -> "Tải âm thanh"
                        Step.AudioType -> "Loại âm thanh"
                        Step.Clip -> "Tải đoạn video"
                        Step.ClipKind -> "Đoạn ${DownloadText.duration(clipStart)} – ${DownloadText.duration(clipEnd)}"
                    },
                    showBack = stack.size > 1,
                    onBack = { back() }
                )
                Column(Modifier.weight(1f, fill = drag.lift.value > 0f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    when (step) {
                        Step.Root -> {
                            OptionRow(VideoIcon, "Tải video") { stack.add(Step.Video) }
                            OptionRow(MusicIcon, "Tải âm thanh") { stack.add(Step.Audio) }
                            OptionRow(ScissorsIcon, "Tải đoạn video") { stack.add(Step.Clip) }
                        }

                        Step.ClipKind -> {
                            OptionRow(VideoIcon, "Tải video") { stack.add(Step.Video) }
                            OptionRow(MusicIcon, "Tải âm thanh") { stack.add(Step.Audio) }
                        }

                        Step.Video -> {
                            OptionRow(Lucide.Settings, "Đồ họa", qualityLabel, chevron = true) { stack.add(Step.Quality) }
                            failed?.let { PanelNote(it) }
                            if (failed == null) PanelNote(
                                estimate(qualityBytes).let { if (it.isEmpty()) "Video MP4 (H.264 + AAC)" else "Video MP4 · $it" }
                            )
                        }

                        Step.Quality -> {
                            val qualities = info?.qualities
                            when {
                                qualities == null && failed == null -> Loading()
                                qualities.isNullOrEmpty() -> PanelNote(failed ?: "Video này không có mức đồ họa nào tải trực tiếp được.")
                                else -> qualities.forEach { q ->
                                    ChoiceRow(
                                        q.label, estimate(q.bytes), selected = q.height == (height ?: topQuality?.height)
                                    ) {
                                        height = q.height
                                        back()
                                    }
                                }
                            }
                        }

                        Step.Audio -> {
                            OptionRow(WaveIcon, "Loại âm thanh", audioFormat.label, chevron = true) { stack.add(Step.AudioType) }
                            failed?.let { PanelNote(it) }
                            if (failed == null) {
                                val size = if (audioFormat == AudioFormat.MP3) {
                                    // MP3 mã hóa 192 kbps nên dung lượng theo thời lượng (cả đoạn cắt).
                                    if (lengthMs > 0) "~" + DownloadText.bytes((if (inClip) clipEnd - clipStart else lengthMs) / 1000 * 24_000L) else ""
                                } else estimate(info?.audioBytes ?: -1L)
                                PanelNote(if (size.isEmpty()) audioFormat.label else "${audioFormat.label} · $size")
                            }
                        }

                        Step.AudioType -> {
                            ChoiceRow(AudioFormat.M4A.label, "Giữ nguyên chất lượng gốc", audioFormat == AudioFormat.M4A) {
                                audioFormat = AudioFormat.M4A
                                back()
                            }
                            ChoiceRow(AudioFormat.MP3.label, "192 kbps, mở được trên mọi thiết bị", audioFormat == AudioFormat.MP3) {
                                audioFormat = AudioFormat.MP3
                                back()
                            }
                        }

                        Step.Clip -> ClipPage(
                            video = video,
                            account = account,
                            lengthMs = lengthMs,
                            startText = startText,
                            endText = endText,
                            onStart = { startText = it },
                            onEnd = { endText = it }
                        )
                    }
                }
                when (step) {
                    Step.Video -> WhiteButton("Tải xuống", enabled = info != null) { startDownload(false) }
                    Step.Audio -> WhiteButton("Tải xuống", enabled = info != null) { startDownload(true) }
                    Step.Clip -> {
                        var error by remember { mutableStateOf(false) }
                        if (error) PanelNote("Thời gian không hợp lệ: kết thúc phải sau bắt đầu và nằm trong video.")
                        WhiteButton("Tiếp theo", icon = false) {
                            val from = DownloadText.parseTime(startText)
                            var to = DownloadText.parseTime(endText)
                            if (lengthMs > 0 && to > lengthMs) to = lengthMs
                            if (from < 0 || to <= from || (lengthMs > 0 && from >= lengthMs)) {
                                error = true
                            } else {
                                error = false
                                clipStart = from
                                clipEnd = to
                                stack.add(Step.ClipKind)
                            }
                        }
                    }
                    else -> Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, showBack: Boolean, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (showBack) 8.dp else 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Lucide.ArrowLeft, null, Modifier.size(24.dp), tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OptionRow(icon: ImageVector, label: String, value: String? = null, chevron: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = Color.White)
        Spacer(Modifier.width(20.dp))
        Text(label, fontSize = 17.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(value, fontSize = 15.sp, color = NanColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
        }
        if (chevron) {
            Spacer(Modifier.width(8.dp))
            Icon(Lucide.ChevronRight, null, Modifier.size(20.dp), tint = NanColors.TextSecondary)
        }
    }
}

@Composable
private fun ChoiceRow(label: String, note: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(Lucide.Check, null, Modifier.size(22.dp), tint = Color.White)
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 17.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note.isNotEmpty()) Text(note, fontSize = 13.sp, color = NanColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
    }
}

/** Nút trắng bo tròn ở đáy bảng. */
@Composable
private fun WhiteButton(text: String, enabled: Boolean = true, icon: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) Color.White else Color(0xFF5A5A5A))
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon) {
            Icon(Lucide.Download, null, Modifier.size(22.dp), tint = Color.Black)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
    }
}

/** Trang Tải đoạn video: hình xem trước, thanh kéo hai đầu và hai ô nhập thời gian (nhập "90", "1:30" hoặc "1:01:30"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipPage(
    video: VideoItem,
    account: Account?,
    lengthMs: Long,
    startText: String,
    endText: String,
    onStart: (String) -> Unit,
    onEnd: (String) -> Unit
) {
    val from = DownloadText.parseTime(startText)
    val to = DownloadText.parseTime(endText)
    // Đoạn hợp lệ như điều kiện của nút "Tiếp theo": bắt đầu >= 0, kết thúc sau bắt đầu, bắt đầu nằm trong video (kết thúc quá dài thì cắt về hết video).
    val validClip = from >= 0 && to > from && (lengthMs <= 0 || from < lengthMs)
    val clipTo = if (lengthMs > 0 && to > lengthMs) lengthMs else to
    ClipPreview(
        video = video,
        account = account,
        startMs = from.coerceAtLeast(0L),
        endMs = clipTo.coerceAtLeast(0L),
        valid = validClip,
        label = "${if (from >= 0) DownloadText.duration(from) else "--"} – ${if (to > 0) DownloadText.duration(to) else "--"}",
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    )
    if (lengthMs > 1000) {
        val total = lengthMs / 1000f
        val s = ((if (from >= 0) from else 0L) / 1000f).coerceIn(0f, total)
        val e = ((if (to > 0) to else lengthMs) / 1000f).coerceIn(s, total)
        NanRangeSlider(
            value = s..e,
            onValueChange = { range ->
                onStart(DownloadText.duration(range.start.toLong() * 1000))
                onEnd(DownloadText.duration(range.endInclusive.toLong() * 1000))
            },
            valueRange = 0f..total,
            inactiveColor = NanColors.Divider,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeField("Bắt đầu", startText, onStart, Modifier.weight(1f))
        TimeField("Kết thúc", endText, onEnd, Modifier.weight(1f))
    }
}

@Composable
private fun TimeField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 13.sp, color = NanColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
        BasicTextField(
            value = value,
            onValueChange = { text -> onChange(text.filter { it.isDigit() || it == ':' }.take(8)) },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NanColors.Surface)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

// ---- Bảng Tải xuống của cả danh sách phát (nút tải cạnh "Phát tất cả") ------------------------------

/** Danh sách phát đang mở bảng tải: tên, số video, tài khoản, cách lấy hết video và cách vào chế độ "Chọn video muốn tải". */
class PlaylistDownloadTarget(
    val title: String,
    val countLabel: String?,
    val account: Account?,
    /** Tải nốt mọi trang của danh sách rồi trả toàn bộ video; [onCount] báo số video đã lấy được. */
    val loadAll: suspend (onCount: (Int) -> Unit) -> List<VideoItem>,
    /** Vào chế độ chọn: chạm một video trong danh sách thì mở bảng Tải xuống bình thường của video đó. */
    val onPick: () -> Unit,
    /** true = [loadAll] trả đúng các video người dùng đã chọn (từ 2 video trở lên): vào thẳng trang Tải video / Tải âm thanh. */
    val picked: Boolean = false
)

object PlaylistDownloadPanel {
    var target by mutableStateOf<PlaylistDownloadTarget?>(null)
        private set

    fun open(target: PlaylistDownloadTarget) {
        this.target = target
    }

    fun close() {
        target = null
    }
}

private enum class ListStep { Root, All, AllVideo, AllAudio, Quality, AudioType }

/** Mức đồ họa chọn sẵn khi tải cả danh sách (không hỏi từng video); null = cao nhất tải được. */
private val ListQualities = listOf<Int?>(null, 1080, 720, 480, 360)

@Composable
fun PlaylistDownloadSheet() {
    val target = PlaylistDownloadPanel.target ?: return
    key(target) { PlaylistDownloadContent(target, onDismiss = { PlaylistDownloadPanel.close() }) }
}

@Composable
private fun PlaylistDownloadContent(target: PlaylistDownloadTarget, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val stack = remember { mutableStateListOf(if (target.picked) ListStep.All else ListStep.Root) }
    val step = stack.last()
    var height by remember { mutableStateOf<Int?>(null) }
    var audioFormat by remember { mutableStateOf(AudioFormat.M4A) }
    var preparing by remember { mutableStateOf(false) }
    var found by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val drag = remember { SheetDrag(scope, onDismiss) }

    fun back() {
        if (preparing || stack.size <= 1) onDismiss() else stack.removeAt(stack.lastIndex)
    }
    BackHandler { back() }

    /** Lấy hết các trang của danh sách, bỏ video đã tải, rồi giao cho dịch vụ tải một lượt. */
    fun startAll(audio: Boolean) {
        if (preparing) return
        preparing = true
        val app = context.applicationContext
        scope.launch {
            try {
                val videos = target.loadAll { found = it }
                val have = runCatching { VideoDownloads.page(app, target.account).items }.getOrDefault(emptyList())
                    .filter { it.music == audio }.mapTo(HashSet()) { it.id }
                val todo = videos.filter { it.id !in have }
                if (todo.isEmpty()) {
                    Toast.makeText(app, "Các video trong danh sách này đã được tải hết", Toast.LENGTH_SHORT).show()
                } else {
                    DownloadService.startBatch(
                        app, target.account?.id, todo,
                        DownloadRequest(audioOnly = audio, height = if (audio) null else height, audioFormat = audioFormat)
                    )
                    val skipped = videos.size - todo.size
                    Toast.makeText(
                        app,
                        "Đang chuẩn bị tải ${todo.size} " + (if (audio) "âm thanh" else "video") +
                            if (skipped > 0) " (bỏ qua $skipped đã tải)" else "",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                preparing = false
                Toast.makeText(app, e.shortMessage ?: "Không bắt đầu được việc tải danh sách", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val count = target.countLabel?.let { " · $it" }.orEmpty()

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Scrim, alpha = 1f - drag.pulled()) }.plainClickable(onDismiss)) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)
                    .graphicsLayer { translationY = (-drag.lift.value).coerceAtLeast(0f) }
                    .layout { measurable, c ->
                        drag.maxPx = c.maxHeight
                        val lift = drag.lift.value
                        val fixed = if (lift > 0f) (drag.peekPx + lift).toInt().coerceAtMost(c.maxHeight) else null
                        val p = measurable.measure(if (fixed != null) c.copy(minHeight = fixed, maxHeight = fixed) else c)
                        if (fixed == null) drag.peekPx = p.height
                        layout(p.width, p.height) { p.place(0, 0) }
                    }
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(NanColors.Background)
                    .plainClickable { }
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                PanelHandle(drag::drag, drag::release)
                Header(
                    title = when (step) {
                        ListStep.Root -> "Tải xuống"
                        ListStep.All -> if (target.picked) "Tải ${target.countLabel ?: "video đã chọn"}" else "Tải cả danh sách phát"
                        ListStep.AllVideo -> "Tải video"
                        ListStep.AllAudio -> "Tải âm thanh"
                        ListStep.Quality -> "Đồ họa"
                        ListStep.AudioType -> "Loại âm thanh"
                    },
                    showBack = stack.size > 1 && !preparing,
                    onBack = { back() }
                )
                Column(Modifier.weight(1f, fill = drag.lift.value > 0f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    if (preparing) {
                        Loading()
                        PanelNote("Đang lấy danh sách… $found video")
                    } else when (step) {
                        ListStep.Root -> {
                            ListOptionRow(Lucide.Check, "Chọn video muốn tải", "Chọn 1 video để tải cả đoạn video, chọn nhiều video để tải video hoặc âm thanh") {
                                onDismiss()
                                target.onPick()
                            }
                            ListOptionRow(Lucide.Download, "Tải cả danh sách phát", "Chỉ tải video hoặc âm thanh" + count) { stack.add(ListStep.All) }
                        }

                        ListStep.All -> {
                            OptionRow(VideoIcon, "Tải video") { stack.add(ListStep.AllVideo) }
                            OptionRow(MusicIcon, "Tải âm thanh") { stack.add(ListStep.AllAudio) }
                            PanelNote("Video đã tải rồi sẽ được bỏ qua.")
                        }

                        ListStep.AllVideo -> {
                            OptionRow(Lucide.Settings, "Đồ họa", height?.let { "${it}p" } ?: "Cao nhất", chevron = true) { stack.add(ListStep.Quality) }
                            PanelNote("Video MP4" + count)
                        }

                        ListStep.Quality -> {
                            ListQualities.forEach { q ->
                                ChoiceRow(q?.let { "${it}p" } ?: "Cao nhất", if (q == null) "Mức cao nhất mỗi video có (tối đa 1080p)" else "Video không có mức này sẽ lấy mức gần nhất thấp hơn", q == height) {
                                    height = q
                                    back()
                                }
                            }
                        }

                        ListStep.AllAudio -> {
                            OptionRow(WaveIcon, "Loại âm thanh", audioFormat.label, chevron = true) { stack.add(ListStep.AudioType) }
                            PanelNote(audioFormat.label + count)
                        }

                        ListStep.AudioType -> {
                            ChoiceRow(AudioFormat.M4A.label, "Giữ nguyên chất lượng gốc", audioFormat == AudioFormat.M4A) {
                                audioFormat = AudioFormat.M4A
                                back()
                            }
                            ChoiceRow(AudioFormat.MP3.label, "192 kbps, mở được trên mọi thiết bị", audioFormat == AudioFormat.MP3) {
                                audioFormat = AudioFormat.MP3
                                back()
                            }
                        }
                    }
                }
                when {
                    preparing -> Spacer(Modifier.height(12.dp))
                    step == ListStep.AllVideo -> WhiteButton("Tải xuống") { startAll(false) }
                    step == ListStep.AllAudio -> WhiteButton("Tải xuống") { startAll(true) }
                    else -> Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

/** Dòng chọn có biểu tượng, tên và một dòng mô tả bên dưới. */
@Composable
private fun ListOptionRow(icon: ImageVector, label: String, note: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = Color.White)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 17.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(note, fontSize = 13.sp, color = NanColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
