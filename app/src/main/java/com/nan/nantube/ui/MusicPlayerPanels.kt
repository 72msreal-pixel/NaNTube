package com.nan.nantube.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.nan.nantube.data.CaptionCue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private val TranscriptAccent = Color(0xFFFF6159)
private const val FOLLOW_PAUSE_MS = 4_000L

private sealed interface TranscriptState {
    data object Loading : TranscriptState
    data object Failed : TranscriptState
    data class Ready(val cues: List<CaptionCue>) : TranscriptState
}

@Composable
internal fun MusicTranscript(
    track: Track,
    positionMs: Long,
    canSeek: Boolean,
    modifier: Modifier = Modifier,
    onSeek: (Long) -> Unit
) {
    var retry by remember(track.id, track.local) { mutableIntStateOf(0) }
    var state by remember(track.id, track.local, retry) { mutableStateOf<TranscriptState>(TranscriptState.Loading) }
    LaunchedEffect(track.id, track.local, retry) {
        state = try {
            TranscriptState.Ready(MusicPlayer.transcript(track, refresh = retry > 0))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            TranscriptState.Failed
        }
    }
    Column(modifier.fillMaxWidth()) {
        PanelTitle("Bản chép lời")
        MusicTrackRow(track)
        when (val result = state) {
            TranscriptState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(26.dp), color = Color.White, strokeWidth = 2.dp)
            }
            TranscriptState.Failed -> PanelMessage("Không tải được bản chép lời.") { retry++ }
            is TranscriptState.Ready -> if (result.cues.isEmpty()) {
                PanelMessage(if (track.local) "Bản tải xuống không kèm bản chép lời." else "Video này không có bản chép lời.")
            } else {
                TranscriptLines(result.cues, positionMs, canSeek, onSeek)
            }
        }
    }
}

/** Áp dụng cách bám thời gian của MeeTube TranscriptSheet vào danh sách Compose của NaNTube. */
@Composable
private fun TranscriptLines(cues: List<CaptionCue>, positionMs: Long, canSeek: Boolean, onSeek: (Long) -> Unit) {
    val list = rememberLazyListState()
    val dragging by list.interactionSource.collectIsDraggedAsState()
    val active = latestCue(cues, positionMs)
    val highlighted = active.takeIf { canSeek && it >= 0 && positionMs < cues[it].endMs } ?: -1
    val follow = remember(cues) { FollowState() }

    LaunchedEffect(active, dragging, canSeek, cues) {
        if (dragging) {
            follow.manuallyScrolled = true
            return@LaunchedEffect
        }
        if (follow.manuallyScrolled) {
            follow.resumeAt = SystemClock.uptimeMillis() + FOLLOW_PAUSE_MS
            follow.manuallyScrolled = false
        }
        if (!canSeek) return@LaunchedEffect
        delay((follow.resumeAt - SystemClock.uptimeMillis()).coerceAtLeast(0L))
        // Đợi fling kết thúc trước khi trả danh sách về dòng đang phát.
        snapshotFlow { list.isScrollInProgress }.first { !it }
        val anchor = (active - 1).coerceAtLeast(0)
        if (anchor != list.firstVisibleItemIndex) list.animateScrollToItem(anchor)
    }

    LazyColumn(
        Modifier.fillMaxSize(), state = list,
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        items(cues.size, key = { it }) { i ->
            val cue = cues[i]
            val current = i == highlighted
            val line = if (!current || cue.marks.isEmpty()) {
                buildAnnotatedString { append(cue.text) }
            } else {
                val sung = cue.marks.firstOrNull { it.offsetMs > positionMs - cue.startMs }?.charIndex ?: cue.text.length
                buildAnnotatedString {
                    withStyle(SpanStyle(color = TranscriptAccent)) { append(cue.text.substring(0, sung)) }
                    withStyle(SpanStyle(color = Color.White)) { append(cue.text.substring(sung)) }
                }
            }
            Text(
                line,
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = canSeek) { onSeek(cue.startMs) }
                    .padding(horizontal = 4.dp, vertical = 9.dp),
                fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold,
                color = if (current) TranscriptAccent else NanColors.TextSecondary
            )
        }
    }
}

private class FollowState {
    var manuallyScrolled = false
    var resumeAt: Long = 0
}

private fun latestCue(cues: List<CaptionCue>, positionMs: Long): Int {
    if (cues.firstOrNull()?.startMs == -1L) return -1
    var low = 0
    var high = cues.size
    while (low < high) {
        val mid = low + (high - low) / 2
        if (cues[mid].startMs <= positionMs) low = mid + 1 else high = mid
    }
    return low - 1
}

@Composable
internal fun MusicQueue(modifier: Modifier = Modifier) {
    val queue = MusicPlayer.queue
    val current = MusicPlayer.current ?: return
    val currentIndex = MusicPlayer.index
    val shuffle = MusicPlayer.shuffle
    val repeat = MusicPlayer.repeat
    val upcoming = remember(queue, currentIndex, shuffle, repeat) { MusicPlayer.upcomingIndices() }
    Column(modifier.fillMaxWidth()) {
        PanelTitle("Nhạc tiếp theo")
        Text(MusicPlayer.label, fontSize = 13.sp, color = NanColors.TextSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
            item(key = "playing") { MusicTrackRow(current, status = "Đang phát") }
            if (shuffle && upcoming.isNotEmpty()) {
                item { Text("Phát ngẫu nhiên", fontSize = 12.sp, color = NanColors.TextSecondary, modifier = Modifier.padding(8.dp)) }
            }
            if (upcoming.isEmpty()) {
                item {
                    Text(if (repeat == RepeatKind.One) "Đang bật lặp lại một bài." else "Không còn bài nào trong hàng đợi.",
                        fontSize = 14.sp, color = NanColors.TextSecondary, modifier = Modifier.padding(8.dp))
                }
            }
            items(upcoming, key = { it }) { index ->
                queue.getOrNull(index)?.let { MusicTrackRow(it, onClick = { MusicPlayer.playQueued(index) }) }
            }
        }
    }
}

@Composable
private fun PanelTitle(title: String) {
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
}

@Composable
private fun MusicTrackRow(track: Track, status: String? = null, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(8.dp)
    val row = Modifier.fillMaxWidth().clip(shape)
    Row(
        (if (onClick != null) row.clickable(onClick = onClick) else row)
            .heightIn(min = 54.dp).padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(42.dp).clip(shape).background(NanColors.Surface), contentAlignment = Alignment.Center) {
            if (track.art != null) AsyncImage(track.art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Lucide.Music, null, Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(track.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (status != null) TranscriptAccent else Color.White)
            Text(track.artist ?: "Không rõ nghệ sĩ", fontSize = 12.sp, maxLines = 1,
                color = NanColors.TextSecondary, overflow = TextOverflow.Ellipsis)
        }
        if (status != null) Text(status, fontSize = 11.sp, color = NanColors.TextSoft)
    }
}

@Composable
private fun PanelMessage(message: String, retry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(message, fontSize = 14.sp, color = NanColors.TextSecondary)
        if (retry != null) {
            Text("Thử lại", Modifier.padding(top = 8.dp).clip(RoundedCornerShape(20.dp)).background(NanColors.Surface)
                .clickable(onClick = retry).padding(horizontal = 16.dp, vertical = 10.dp), fontSize = 14.sp)
        }
    }
}
