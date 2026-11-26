package com.nan.nantube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.nan.nantube.data.CaptionTrack
import com.nan.nantube.data.ShortsCaptions
import com.nan.nantube.data.ShortsPlayback
import com.nan.nantube.data.SleepTimer
import com.nan.nantube.data.WatchPrefs

/** Các trang của bảng cài đặt trình phát (nút bánh răng): trang chính, ba trang chọn và trang "Tuỳ chọn khác" cùng trang Hẹn giờ ngủ. */
internal enum class WatchSheetPage(val title: String) {
    Main(""),
    Quality("Chất lượng"),
    Speed("Tốc độ phát"),
    Captions("Phụ đề"),
    More("Tuỳ chọn khác"),
    Sleep("Hẹn giờ ngủ")
}

private fun parentOf(page: WatchSheetPage) = if (page == WatchSheetPage.Sleep) WatchSheetPage.More else WatchSheetPage.Main

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

// Path lucide (sliders-horizontal, gauge, captions, repeat-1, sun/monitor, audio-lines, moon).
private val QualityIcon: ImageVector by lazy {
    lucide(
        "WatchSheetQuality",
        "M21 4h-7", "M10 4H3", "M21 12h-9", "M8 12H3", "M21 20h-5", "M12 20H3", "M14 2v4", "M8 10v4", "M16 18v4"
    )
}
private val SpeedIcon: ImageVector by lazy {
    ImageVector.Builder("WatchSheetSpeed", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M10,7.5v9l6,-4.5zM11,4.07V2.05c-2.01,0.2 -3.84,1 -5.32,2.21L7.1,5.69c1.11,-0.86 2.44,-1.44 3.9,-1.62zM5.69,7.1L4.26,5.68" +
                    "C3.05,7.16 2.25,8.99 2.05,11h2.02c0.18,-1.46 0.76,-2.79 1.62,-3.9zM4.07,13H2.05c0.2,2.01 1,3.84 2.21,5.32l1.43,-1.43" +
                    "c-0.86,-1.1 -1.44,-2.43 -1.62,-3.89zM5.68,19.74C7.16,20.95 9,21.75 11,21.95v-2.02c-1.46,-0.18 -2.79,-0.76 -3.9,-1.62l-1.42,1.43z" +
                    "M22,12c0,5.16 -3.92,9.42 -8.95,9.95v-2.02C16.97,19.41 20,16.05 20,12s-3.03,-7.41 -6.95,-7.93V2.05C18.08,2.58 22,6.84 22,12z"
            ).toNodes(),
            fill = SolidColor(Color.Black)
        )
    }.build()
}
private val CaptionsIcon: ImageVector by lazy {
    lucide(
        "WatchSheetCaptions",
        "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z",
        "M10.5 10a2 2 0 1 0 0 4",
        "M16.5 10a2 2 0 1 0 0 4"
    )
}
private val LoopIcon: ImageVector by lazy {
    lucide("WatchSheetLoop", "m17 2 4 4-4 4", "M3 11v-1a4 4 0 0 1 4-4h14", "m7 22-4-4 4-4", "M21 13v1a4 4 0 0 1-4 4H3", "M11 10h1v4")
}
private val AmbientIcon: ImageVector by lazy {
    lucide(
        "WatchSheetAmbient",
        "M7 7h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z",
        "M12 2v2", "M12 20v2", "M3 12h.01", "M21 12h.01"
    )
}
private val StableVolumeIcon: ImageVector by lazy {
    lucide("WatchSheetStable", "M2 10v3", "M6 6v11", "M10 3v18", "M14 8v7", "M18 5v13", "M22 10v3")
}
private val SleepIcon: ImageVector by lazy { lucide("WatchSheetSleep", "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z") }

/** Lề hai bên của thẻ cài đặt (càng lớn thẻ càng hẹp). */
private val SHEET_SIDE_MARGIN = 12.dp

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun speedShort(speed: Float) = speed.toString().removeSuffix(".0") + "x"
private fun speedLabel(speed: Float) = if (speed == 1f) "Bình thường" else speedShort(speed)

private class SheetEntry(
    val icon: ImageVector,
    val label: String,
    val value: String? = null,
    val chevron: Boolean = false,
    /** Không null = dòng có công tắc. */
    val toggle: Boolean? = null,
    val onClick: () -> Unit
)

private class SheetChoice(val label: String, val selected: Boolean, val onPick: () -> Unit)

/**
 * Bảng cài đặt của trình phát video dài, trượt từ đáy như app YouTube: trang chính gồm Chất lượng, Tốc độ phát, Phụ đề, Tuỳ chọn khác;
 * "Tuỳ chọn khác" có Cho video lặp lại, Chế độ môi trường xung quanh, Âm lượng ổn định, Hẹn giờ ngủ.
 * (Khóa màn hình, Trên đường đi, Xem ở chế độ thực tế ảo, Trợ giúp và phản hồi không làm: không có trong app này.)
 * Nội dung đọc / ghi thẳng vào [WatchPlayback]; công tắc lưu theo tài khoản ở [WatchPrefs].
 */
@Composable
internal fun WatchSettingsSheet(
    pb: WatchPlayback,
    tracks: List<CaptionTrack>,
    page: WatchSheetPage,
    onPage: (WatchSheetPage) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { if (page == WatchSheetPage.Main) onClose() else onPage(parentOf(page)) }

    // Kéo xuống để đóng: chỉ kéo ở thanh nắm xám trên đỉnh (nội dung bên dưới không kéo được bảng). Thả quá ~110dp hoặc vuốt nhanh thì đóng, không thì bật lại.
    var dy by remember { mutableFloatStateOf(0f) }
    val closeAt = with(LocalDensity.current) { 110.dp.toPx() }
    val flingAt = with(LocalDensity.current) { 1_200.dp.toPx() }
    suspend fun settle(velocity: Float) {
        if (dy > closeAt || velocity > flingAt) {
            animate(dy, dy + 1_200f, animationSpec = tween(160)) { v, _ -> dy = v }
            onClose()
        } else {
            animate(dy, 0f, animationSpec = tween(180)) { v, _ -> dy = v }
        }
    }
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(interactionSource = remember2(), indication = null) { onClose() }
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = SHEET_SIDE_MARGIN, end = SHEET_SIDE_MARGIN, bottom = 4.dp)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .offset { IntOffset(0, dy.toInt().coerceAtLeast(0)) }
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NanColors.Popup)
                .clickable(interactionSource = remember2(), indication = null) { }
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = rememberDraggableState { delta -> dy = (dy + delta).coerceAtLeast(0f) },
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> settle(velocity) }
                    )
                    .padding(top = 12.dp, bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x66FFFFFF)))
            }

            val playing = pb.playingSide.takeIf { it > 0 }
            val auto = "Tự động" + (playing?.let { " (${it}p)" } ?: "")
            val activeTrack = activeCaption(tracks)
            val qualities = ShortsPlayback.qualities(pb.video.id)

            val entries: List<SheetEntry>? = when (page) {
                WatchSheetPage.Main -> listOf(
                    SheetEntry(QualityIcon, "Chất lượng", pb.quality?.let { "${it}p" } ?: auto, chevron = true) { onPage(WatchSheetPage.Quality) },
                    SheetEntry(SpeedIcon, "Tốc độ phát", speedShort(pb.speed), chevron = true) { onPage(WatchSheetPage.Speed) },
                    SheetEntry(CaptionsIcon, "Phụ đề", activeTrack?.label, chevron = true) { onPage(WatchSheetPage.Captions) },
                    SheetEntry(Lucide.Settings, "Tuỳ chọn khác", chevron = true) { onPage(WatchSheetPage.More) }
                )
                WatchSheetPage.More -> listOf(
                    SheetEntry(LoopIcon, "Cho video lặp lại", toggle = pb.loop) { pb.setLooping(!pb.loop) },
                    SheetEntry(AmbientIcon, "Chế độ môi trường xung quanh", toggle = WatchPrefs.ambient) { WatchPrefs.updateAmbient(!WatchPrefs.ambient) },
                    SheetEntry(StableVolumeIcon, "Âm lượng ổn định", toggle = WatchPrefs.stableVolume) { WatchPrefs.updateStableVolume(!WatchPrefs.stableVolume) },
                    SheetEntry(SleepIcon, "Hẹn giờ ngủ", SleepTimer.label(SleepTimer.picked), chevron = true) { onPage(WatchSheetPage.Sleep) }
                )
                else -> null
            }

            var note: String? = null
            val choices: List<SheetChoice> = when (page) {
                WatchSheetPage.Quality -> {
                    // Mức nào của video này gần với lựa chọn đang giữ nhất thì đánh dấu mức đó.
                    val chosen = pb.quality?.let { q -> qualities.minByOrNull { kotlin.math.abs(it - q) } }
                    if (qualities.isEmpty()) note = "Video này chỉ có một mức chất lượng."
                    listOf(SheetChoice(auto, pb.quality == null) { pb.selectQuality(null); onClose() }) +
                        qualities.map { q -> SheetChoice("${q}p", chosen == q) { pb.selectQuality(q); onClose() } }
                }
                WatchSheetPage.Speed -> SPEEDS.map { s -> SheetChoice(speedLabel(s), pb.speed == s) { pb.selectSpeed(s); onClose() } }
                WatchSheetPage.Captions -> {
                    if (tracks.isEmpty()) note = "Video này không có phụ đề."
                    listOf(SheetChoice("Tắt", activeTrack == null) { WatchPrefs.setCaption(null); onClose() }) +
                        tracks.map { t -> SheetChoice(t.label, activeTrack?.key == t.key) { WatchPrefs.setCaption(t.key); onClose() } }
                }
                WatchSheetPage.Sleep -> SleepTimer.PRESETS.map { m ->
                    SheetChoice(SleepTimer.label(m), SleepTimer.picked == m) { SleepTimer.set(m); onClose() }
                }
                else -> emptyList()
            }

            if (page != WatchSheetPage.Main) SheetHeader(page.title) { onPage(parentOf(page)) }
            LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 6.dp)) {
                if (entries != null) items(entries) { SheetRow(it) } else {
                    items(choices) { SheetChoiceRow(it) }
                    note?.let { n -> item { Text(n, fontSize = 14.sp, color = NanColors.TextSecondary, modifier = Modifier.padding(20.dp)) } }
                }
            }
        }
    }
}

/** Phụ đề đang áp dụng cho video: bản trong [WatchPrefs.captionKey] nếu video có, không có thì bản mặc định của video; null nếu đang tắt. */
internal fun activeCaption(tracks: List<CaptionTrack>): CaptionTrack? {
    val key = WatchPrefs.captionKey ?: return null
    return ShortsCaptions.pick(tracks, key) ?: ShortsCaptions.defaultTrack(tracks)
}

@Composable
private fun remember2() = remember { MutableInteractionSource() }

@Composable
private fun SheetRow(entry: SheetEntry) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = entry.onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(entry.icon, null, Modifier.size(26.dp), tint = Color.White)
        Spacer(Modifier.width(10.dp))
        Text(entry.label, fontSize = 15.5.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (entry.value != null) {
            Spacer(Modifier.width(12.dp))
            Text(entry.value, fontSize = 13.5.sp, color = NanColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 170.dp))
        }
        if (entry.toggle != null) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.padding(end = 6.dp)) { SheetSwitch(entry.toggle, entry.onClick) }
        }
        if (entry.chevron) {
            Icon(Lucide.ChevronRight, null, Modifier.size(17.dp), tint = NanColors.TextSecondary)
        }
    }
}

/** Công tắc của bảng cài đặt trình phát, đo từ YouTube: ray 37x22dp (tắt xám, bật trắng), núm tối 18dp cách mép 2dp. */
@Composable
private fun SheetSwitch(checked: Boolean, onToggle: () -> Unit) {
    val t by animateFloatAsState(if (checked) 1f else 0f, tween(150), label = "sheetSwitch")
    Box(
        Modifier.size(37.dp, 22.dp).clip(RoundedCornerShape(11.dp))
            .background(lerp(Color(0xFF7A7A7A), Color(0xFFE6E6E6), t))
            .toggleable(value = checked, role = Role.Switch, interactionSource = remember2(), indication = null) { onToggle() }
    ) {
        Box(Modifier.align(Alignment.CenterStart).offset(x = 2.dp + 15.dp * t).size(18.dp).clip(CircleShape).background(Color(0xFF272727)))
    }
}

@Composable
private fun SheetHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Lucide.ArrowLeft, null, Modifier.size(24.dp), tint = Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun SheetChoiceRow(choice: SheetChoice) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = choice.onPick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (choice.selected) Icon(Lucide.Check, null, Modifier.size(22.dp), tint = Color.White)
        }
        Spacer(Modifier.width(20.dp))
        Text(choice.label, fontSize = 15.5.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
