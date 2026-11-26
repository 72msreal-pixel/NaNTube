package com.nan.nantube.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nan.nantube.data.CaptionCue
import com.nan.nantube.data.CaptionTrack
import com.nan.nantube.data.ShortsCaptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val HANDS_OFF_MS = 4_000L

/**
 * Chiều cao bảng kéo được, tính bằng tỉ lệ vùng chứa: kéo ở thanh nắm thì đi theo ngón tay, thả tay thì tự về mốc gần nhất
 * (đóng / mở mặc định [peek] / hết cỡ [full]). [open] mở tới [peek] hoặc mốc truyền vào.
 */
class PanelSheet(private val scope: CoroutineScope, private val peek: Float, private val full: Float) {
    private val anim = Animatable(0f)
    val fraction: Float get() = anim.value

    fun open(to: Float = peek) {
        scope.launch { anim.animateTo(to, tween(240)) }
    }

    fun close(onClosed: () -> Unit = {}) {
        scope.launch {
            anim.animateTo(0f, tween(220))
            onClosed()
        }
    }

    suspend fun snapClosed() = anim.snapTo(0f)

    fun drag(dyPx: Float, heightPx: Int) {
        if (heightPx <= 0) return
        scope.launch { anim.snapTo((anim.value - dyPx / heightPx).coerceIn(0f, full)) }
    }

    /** [velocity] px/s, dương = đang kéo xuống. */
    fun settle(velocity: Float, onClosed: () -> Unit = {}) {
        val v = anim.value
        val target = when {
            velocity > 1200f -> if (v > peek + 0.05f) peek else 0f
            velocity < -1200f -> if (v < peek - 0.05f) peek else full
            v < peek * 0.55f -> 0f
            v < (peek + full) / 2f -> peek
            else -> full
        }
        scope.launch {
            anim.animateTo(target, tween(220))
            if (target == 0f) onClosed()
        }
    }
}

/**
 * Khung bảng dùng chung cho mô tả, menu và bản ghi của Short. Chiều cao là [fraction] của vùng Short; chỉ kéo ở thanh nắm
 * trên đỉnh mới đổi chiều cao cả bảng, nội dung bên trong vẫn cuộn bình thường.
 */
@Composable
fun DraggablePanel(
    fraction: Float,
    onDrag: (Float) -> Unit,
    onRelease: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** Phần bàn phím đang che đáy vùng Short (px): bảng cao thêm đúng bấy nhiêu để nội dung không bị ép nhỏ. */
    liftPx: Int = 0,
    /** Chiều cao vùng thanh nắm: Short để thoáng 40dp, màn xem video dùng sát hơn như YouTube. */
    handleHeight: Dp = 40.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    // Cuộn tới đầu / cuối nội dung mà còn dư cử chỉ thì nuốt luôn, không cho tràn lên YtPullRefresh bên ngoài (gây vòng tròn tải lại).
    val swallowOverscroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }
    Column(
        modifier.fillMaxWidth()
            .layout { measurable, c ->
                val target = (c.maxHeight * fraction.coerceIn(0f, 1f) + liftPx).toInt().coerceIn(0, c.maxHeight)
                val p = measurable.measure(c.copy(minHeight = target, maxHeight = target))
                layout(p.width, p.height) { p.place(0, 0) }
            }
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(NanColors.Background)
            .nestedScroll(swallowOverscroll)
            .plainClickable { }
    ) {
        PanelHandle(onDrag, onRelease, handleHeight)
        content()
    }
}

/** Thanh nắm ở đỉnh bảng: chỉ kéo ở đây mới đổi chiều cao / đóng bảng. [onRelease] nhận vận tốc px/s, dương = kéo xuống. */
@Composable
fun PanelHandle(onDrag: (Float) -> Unit, onRelease: (Float) -> Unit, height: Dp = 40.dp) {
    val dragNow by rememberUpdatedState(onDrag)
    val releaseNow by rememberUpdatedState(onRelease)
    Box(
        Modifier.fillMaxWidth().height(height).draggable(
            orientation = Orientation.Vertical,
            state = rememberDraggableState { dragNow(it) },
            onDragStopped = { releaseNow(it) }
        ),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(NanColors.TextSecondary))
    }
}

@Composable
fun PanelNote(text: String) {
    Text(text, fontSize = 14.sp, color = NanColors.TextSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
}

/** [pad]: luôn 2 chữ số phút ("00:37"). */
fun clock(ms: Long, pad: Boolean = false): String {
    val s = ms / 1000
    val min = if (pad) "%02d" else "%d"
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "$min:%02d".format(s / 60, s % 60)
}

/**
 * Bản ghi lời thoại của Short như bảng Transcript của YouTube / meetube: dòng đang nói sáng lên và được cuộn theo,
 * chạm một dòng để phát từ đó, ô tìm kiếm lọc các dòng khớp. Dùng đúng bản phụ đề đang chọn,
 * chưa chọn thì lấy bản mặc định; phụ đề đã tải rồi thì lấy từ bộ nhớ đệm của [ShortsCaptions].
 */
@Composable
fun ColumnScope.TranscriptContent(
    tracks: List<CaptionTrack>,
    captionKey: String?,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit
) {
    val track = remember(tracks, captionKey) { ShortsCaptions.pick(tracks, captionKey) ?: ShortsCaptions.defaultTrack(tracks) }
    var cues by remember(track) { mutableStateOf<List<CaptionCue>?>(null) }
    var failed by remember(track) { mutableStateOf(false) }
    LaunchedEffect(track) {
        if (track == null) return@LaunchedEffect
        try {
            cues = ShortsCaptions.load(track)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed = true
        }
    }

    var query by remember { mutableStateOf("") }
    val loaded = cues
    val shown = remember(loaded, query) {
        val text = query.trim()
        if (loaded == null || text.isEmpty()) loaded.orEmpty() else loaded.filter { it.text.contains(text, ignoreCase = true) }
    }
    val said = remember(shown, positionMs) { shown.indexOfLast { it.startMs <= positionMs } }

    val listState = rememberLazyListState()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var lastTouch by remember { mutableLongStateOf(0L) }
    LaunchedEffect(dragged) { if (dragged || lastTouch != 0L) lastTouch = SystemClock.uptimeMillis() }
    LaunchedEffect(said) {
        val idle = !dragged && SystemClock.uptimeMillis() - lastTouch > HANDS_OFF_MS
        if (said >= 0 && idle && query.isBlank()) listState.animateScrollToItem(said)
    }

    val subtitle = track?.label.orEmpty()

    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Bản ghi", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, fontSize = 13.sp, color = NanColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.size(30.dp).clip(CircleShape).plainClickable(onClose), contentAlignment = Alignment.Center) {
            Icon(IconClose, "Đóng", Modifier.size(22.dp), tint = Color.White)
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).clip(RoundedCornerShape(20.dp))
            .background(NanColors.Surface).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(YtSearch, null, Modifier.size(20.dp), tint = NanColors.TextSecondary)
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.weight(1f),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Tìm trong bản ghi", fontSize = 15.sp, color = NanColors.TextSecondary)
                    field()
                }
            }
        )
    }
    when {
        track == null -> PanelNote("Video này không có phụ đề nên chưa có bản ghi.")
        failed -> PanelNote("Không tải được bản ghi.")
        loaded == null -> PanelNote("Đang tải bản ghi…")
        shown.isEmpty() -> PanelNote(if (loaded.isEmpty()) "Video này không có lời thoại." else "Không có dòng nào khớp.")
        else -> LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = WindowInsets.navigationBars.asPaddingValues()
        ) {
            itemsIndexed(shown) { index, cue -> TranscriptRow(cue, index == said) { onSeek(cue.startMs) } }
        }
    }
}

@Composable
private fun TranscriptRow(cue: CaptionCue, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (active) NanColors.Surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(clock(cue.startMs), fontSize = 13.sp, color = NanColors.Blue, modifier = Modifier.width(52.dp).padding(top = 2.dp))
        Text(cue.text, fontSize = 15.sp, lineHeight = 21.sp, color = if (active) Color.White else NanColors.TextSoft)
    }
}
