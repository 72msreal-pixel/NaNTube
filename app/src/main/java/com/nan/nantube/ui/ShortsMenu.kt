package com.nan.nantube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextAlign
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

/**
 * Trạng thái giao diện dùng chung của trình phát Short: nút ba chấm nằm ở thanh trên (ngoài pager) còn bảng menu và
 * chế độ làm sạch màn hình nằm trong pager, nên hai bên đọc chung ở đây. Một lúc chỉ có một trình phát Short đang xem.
 */
object ShortsChrome {
    /** Vừa bấm ⋯ ở thanh trên: trình phát Short thấy cờ này thì mở bảng menu rồi hạ cờ xuống. */
    var menuRequest by mutableStateOf(false)

    /** Làm sạch màn hình: ẩn avatar / tên / tiêu đề / các nút / kính lúp / ba chấm, chỉ còn nút nhỏ để hiện lại. */
    var clean by mutableStateOf(false)

    /** Phụ đề đang bật: [CaptionTrack.key] (null = tắt). Nhớ qua các Short như YouTube. */
    var captionKey by mutableStateOf<String?>(null)

    fun reset() {
        menuRequest = false
        clean = false
    }
}

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

private val DescriptionIcon: ImageVector by lazy { lucide("ShortsDescription", "M21 6H3", "M15 12H3", "M17 18H3") }
private val CleanScreenIcon: ImageVector by lazy { lucide("ShortsClean", "M15 3h6v6", "M9 21H3v-6", "M21 3l-7 7", "M3 21l7-7") }
private val RestoreScreenIcon: ImageVector by lazy { lucide("ShortsRestore", "M4 14h6v6", "M20 10h-6V4", "M14 10l7-7", "M3 21l7-7") }
private val CaptionsIcon: ImageVector by lazy {
    lucide(
        "ShortsCaptions",
        "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z",
        "M10.5 10a2 2 0 1 0 0 4",
        "M16.5 10a2 2 0 1 0 0 4"
    )
}
private val TranscriptIcon: ImageVector by lazy {
    lucide("ShortsTranscript", "M3 6h.01", "M3 12h.01", "M3 18h.01", "M8 6h13", "M8 12h13", "M8 18h13")
}
private val SpeedIcon: ImageVector by lazy { lucide("ShortsSpeed", "m12 14 4-4", "M3.34 19a10 10 0 1 1 17.32 0") }
private val AutoScrollIcon: ImageVector by lazy { lucide("ShortsAutoScroll", "m7 6 5 5 5-5", "m7 13 5 5 5-5") }
private val NotInterestedIcon: ImageVector by lazy { lucide("ShortsNotInterested", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0z", "M4.9 4.9l14.2 14.2") }
private val DontRecommendIcon: ImageVector by lazy { lucide("ShortsDontRecommend", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0z", "M15 9l-6 6", "M9 9l6 6") }

private enum class MenuPage(val title: String) {
    Main(""),
    Quality("Chất lượng"),
    Captions("Phụ đề"),
    Speed("Tốc độ phát")
}

private fun MenuPage.parent() = MenuPage.Main

private class MenuEntry(
    val icon: ImageVector,
    val label: String,
    val value: String? = null,
    val chevron: Boolean = false,
    val onClick: () -> Unit
)

private class Choice(val label: String, val selected: Boolean, val onPick: () -> Unit)

private class SubPage(val choices: List<Choice>, val note: String? = null, val link: MenuEntry? = null)

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun speedLabel(speed: Float) = if (speed == 1f) "Bình thường" else "${speed.toString().removeSuffix(".0")}x"

/**
 * Nội dung bảng menu khi bấm ba chấm ở Short, nằm trong [DraggablePanel] nên kéo lên xuống được như bảng mô tả. Các mục như YouTube:
 * Thông tin mô tả, Bản ghi, Làm sạch màn hình, Phụ đề (kèm dịch sang ngôn ngữ khác), Tốc độ phát, Chất lượng, Tự động vuốt,
 * Không quan tâm, Không đề xuất kênh này. Phụ đề, Tốc độ và Chất lượng mở trang con ngay trong bảng.
 */
@Composable
fun ColumnScope.ShortsMenuContent(
    tracks: List<CaptionTrack>,
    qualities: List<Int>,
    playingQuality: Int?,
    onDescription: () -> Unit,
    onTranscript: () -> Unit,
    onClean: () -> Unit,
    onNotInterested: () -> Unit,
    onDontRecommend: () -> Unit,
    onDismiss: () -> Unit
) {
    var page by remember { mutableStateOf(MenuPage.Main) }
    BackHandler { if (page == MenuPage.Main) onDismiss() else page = page.parent() }

    val auto = "Tự động" + (playingQuality?.let { " (${it}p)" } ?: "")
    val selectedTrack = ShortsCaptions.pick(tracks, ShortsChrome.captionKey)

    val entries = listOf(
        MenuEntry(DescriptionIcon, "Thông tin mô tả", onClick = onDescription),
        MenuEntry(TranscriptIcon, "Bản ghi", onClick = onTranscript),
        MenuEntry(CleanScreenIcon, "Làm sạch màn hình", onClick = onClean),
        MenuEntry(CaptionsIcon, "Phụ đề", selectedTrack?.label ?: "Tắt", true) { page = MenuPage.Captions },
        MenuEntry(SpeedIcon, "Tốc độ phát", speedLabel(ShortsPool.speed), true) { page = MenuPage.Speed },
        MenuEntry(Lucide.Settings, "Chất lượng", ShortsPool.quality?.let { "${it}p" } ?: auto, true) { page = MenuPage.Quality },
        MenuEntry(AutoScrollIcon, "Tự động vuốt", if (ShortsPool.autoScroll) "Bật" else "Tắt") {
            ShortsPool.selectAutoScroll(!ShortsPool.autoScroll)
        },
        MenuEntry(NotInterestedIcon, "Không quan tâm", onClick = onNotInterested),
        MenuEntry(DontRecommendIcon, "Không đề xuất kênh này", onClick = onDontRecommend)
    )

    val sub = when (page) {
        MenuPage.Main -> null
        MenuPage.Quality -> {
            // Mức nào của video này gần với lựa chọn đang giữ nhất thì đánh dấu mức đó.
            val chosen = ShortsPool.quality?.let { q -> qualities.minByOrNull { kotlin.math.abs(it - q) } }
            SubPage(
                choices = listOf(Choice(auto, ShortsPool.quality == null) { ShortsPool.selectQuality(null); onDismiss() }) +
                    qualities.map { q -> Choice("${q}p", chosen == q) { ShortsPool.selectQuality(q); onDismiss() } },
                note = if (qualities.isEmpty()) "Video này chỉ có một mức chất lượng." else null
            )
        }
        MenuPage.Captions -> SubPage(
            choices = listOf(Choice("Tắt", selectedTrack == null) { ShortsChrome.captionKey = null; onDismiss() }) +
                tracks.map { track -> Choice(track.label, selectedTrack?.key == track.key) { ShortsChrome.captionKey = track.key; onDismiss() } },
            note = if (tracks.isEmpty()) "Video này không có phụ đề." else null
        )
        MenuPage.Speed -> SubPage(SPEEDS.map { speed -> Choice(speedLabel(speed), ShortsPool.speed == speed) { ShortsPool.selectSpeed(speed); onDismiss() } })
    }

    if (sub != null) SubHeader(page.title) { page = page.parent() }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
        if (sub == null) {
            items(entries) { OptionRow(it) }
        } else {
            items(sub.choices) { ChoiceRow(it) }
            sub.note?.let { note -> item { PanelNote(note) } }
            sub.link?.let { link -> item { OptionRow(link) } }
        }
    }
}

@Composable
private fun OptionRow(entry: MenuEntry) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = entry.onClick).padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(entry.icon, null, Modifier.size(24.dp), tint = Color.White)
        Spacer(Modifier.width(20.dp))
        Text(entry.label, fontSize = 17.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (entry.value != null) {
            Spacer(Modifier.width(12.dp))
            Text(entry.value, fontSize = 15.sp, color = NanColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
        }
        if (entry.chevron) {
            Spacer(Modifier.width(8.dp))
            Icon(Lucide.ChevronRight, null, Modifier.size(20.dp), tint = NanColors.TextSecondary)
        }
    }
}

@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Lucide.ArrowLeft, null, Modifier.size(24.dp), tint = Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun ChoiceRow(choice: Choice) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = choice.onPick).padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (choice.selected) Icon(Lucide.Check, null, Modifier.size(22.dp), tint = Color.White)
        }
        Spacer(Modifier.width(20.dp))
        Text(choice.label, fontSize = 17.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Nút nhỏ ở góc dưới bên phải khi đang làm sạch màn hình: chạm để hiện lại mọi thứ. */
@Composable
fun CleanRestoreButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(48.dp).clip(CircleShape).background(Color(0x80303030)).plainClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(RestoreScreenIcon, null, Modifier.size(24.dp), tint = Color.White)
    }
}

/** Dòng phụ đề đang hiện: chữ trắng trên nền đen mờ như phụ đề YouTube. */
@Composable
fun CaptionOverlay(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.background(Color(0xB3000000), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
