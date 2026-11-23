package com.nan.nantube.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.nan.nantube.data.Account
import com.nan.nantube.data.ShortsMedia
import com.nan.nantube.data.ShortsPlayback
import com.nan.nantube.data.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Một ô phát: một ExoPlayer đã / đang nạp đúng một Short, đứng sẵn ở khung hình đầu (tạm dừng) để vuốt tới là hiện ngay.
 * Trạng thái là state của Compose nên màn Short đọc trực tiếp.
 */
class ShortSlot internal constructor(val video: VideoItem, val player: ExoPlayer) {
    /** Khung hình đầu đã lên màn (cần một PlayerView đang gắn với [player]). */
    var frame by mutableStateOf(false)
        internal set
    var buffering by mutableStateOf(false)
        internal set

    /** Đã nạp xong (ExoPlayer READY ít nhất một lần): từ đây phát là có hình ngay. */
    var ready by mutableStateOf(false)
        internal set
    var vertical by mutableStateOf(true)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set

    internal var source: String? = null
    internal var autoTries = 0
    internal var resumeMs = 0L
    internal val failed = HashSet<String>()
    internal var job: Job? = null
    internal var wantPlay = false
    internal var activeRetried = false
    internal var released = false
}

/**
 * Các ô phát của tab Short theo cách meetube (ShortsPeekPlayer + tải trước video kế): luôn giữ sẵn video đang xem, video kế
 * và video trước đã nạp xong ở khung hình đầu. Vuốt qua thì ô kế chỉ việc phát, không phải lấy link / dựng nguồn / chờ
 * khung hình như bản một trình phát cũ; lúc kéo, trang bên cạnh đã có hình thật dưới ngón tay.
 *
 * Sống ngoài vòng đời của pager nên làm mới danh sách có thể nạp sẵn video đầu của bộ mới ([preloadHead]) trước khi đổi sang.
 * Mọi hàm gọi trên luồng chính.
 */
object ShortsPool {
    private const val MAX_AUTO_RECOVERY = 4
    private const val READY_TIMEOUT_MS = 9_000L
    private const val NEIGHBOUR_WAIT_MS = 1_500L
    private const val HEAD_WAIT_MS = 4_000L
    private const val TRIM_DELAY_MS = 3_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val slots = LinkedHashMap<String, ShortSlot>()
    private var app: Context? = null
    private var account: Account? = null
    private var claimed: Set<String> = emptySet()
    private var neighbourJob: Job? = null
    private var trimJob: Job? = null

    /** Tăng mỗi khi thêm / bỏ ô để màn Short (đọc qua [slot]) tự vẽ lại. */
    private var version by mutableIntStateOf(0)

    fun bind(context: Context, account: Account?) {
        app = context.applicationContext
        this.account = account
    }

    fun slot(id: String): ShortSlot? {
        if (version < 0) return null // chỉ để đăng ký theo dõi state
        return slots[id]
    }

    /**
     * Giữ đúng các video này (phần tử đầu là video đang xem, kế đến video sau rồi video trước): ô thiếu thì tạo và nạp, ô ngoài danh sách
     * thì giải phóng. Video đang xem nạp trước; ô bên cạnh nạp sau khi ô đó sẵn sàng để không tranh băng thông.
     */
    fun window(videos: List<VideoItem>) {
        trimJob?.cancel()
        claimed = videos.map { it.id }.toHashSet()
        trimUnclaimed()
        val current = videos.firstOrNull() ?: return
        val currentSlot = slots[current.id] ?: create(current)
        if (currentSlot != null && currentSlot.error != null && !currentSlot.activeRetried) {
            // Ô nạp trước bị lỗi: tới lượt xem thì thử lại một lần cho người dùng khỏi thấy lỗi ngay.
            currentSlot.activeRetried = true
            retry(currentSlot)
        }
        neighbourJob?.cancel()
        neighbourJob = scope.launch {
            if (currentSlot != null) withTimeoutOrNull(NEIGHBOUR_WAIT_MS) { snapshotFlow { currentSlot.ready || currentSlot.error != null }.first { it } }
            for (video in videos.drop(1)) {
                val slot = slots[video.id] ?: create(video) ?: continue
                withTimeoutOrNull(NEIGHBOUR_WAIT_MS) { snapshotFlow { slot.ready || slot.error != null }.first { it } }
            }
        }
    }

    /** Chỉ ô của [id] được phát (và chỉ khi [playing]); các ô còn lại đứng yên ở khung đầu. */
    fun setActive(id: String?, playing: Boolean) {
        for ((videoId, slot) in slots) {
            val want = videoId == id && playing
            slot.wantPlay = want
            if (slot.player.playWhenReady != want) slot.player.playWhenReady = want
        }
    }

    fun pauseAll() = setActive(null, false)

    /** Bấm "Thử lại": làm lại từ đầu với client đầu tiên. */
    fun retry(slot: ShortSlot) {
        slot.autoTries = 0
        slot.resumeMs = 0L
        slot.failed.clear()
        ShortsPlayback.forget(slot.video.id)
        start(slot)
    }

    /** Pager rời màn: dừng mọi ô, ít giây sau không ai nhận lại thì giải phóng hết (nhả bộ giải mã). */
    fun pagerGone() {
        pauseAll()
        claimed = emptySet()
        scheduleTrim(TRIM_DELAY_MS)
    }

    /**
     * Làm mới danh sách: nạp sẵn 2 video đầu của bộ mới (link + khung hình đầu + kênh / số thích / bình luận) và đợi video đầu sẵn sàng
     * (tối đa [HEAD_WAIT_MS]) rồi mới để màn đổi sang bộ mới, nên video hiện ra là phát luôn thay vì vòng xoay.
     * Không đụng tới ô của video đang xem: nó vẫn phát cho tới khi bộ mới thay vào.
     */
    suspend fun preloadHead(account: Account?, videos: List<VideoItem>) {
        val context = app ?: return
        this.account = account
        val head = videos.take(2)
        val first = head.firstOrNull() ?: return
        val slot = slots[first.id] ?: create(first)
        head.drop(1).forEach { if (slots[it.id] == null) create(it) }
        scheduleTrim(20_000L)
        withTimeoutOrNull(HEAD_WAIT_MS) {
            coroutineScope {
                if (slot != null) launch { snapshotFlow { slot.ready || slot.error != null }.first { it } }
                launch {
                    try {
                        ShortsPlayback.info(context, account, first).await()
                        ShortsPlayback.commentCount(first.id).await()
                        ShortsPlayback.description(first.id).await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    // ---- bên trong --------------------------------------------------------------------------------

    private fun scheduleTrim(delayMs: Long) {
        trimJob?.cancel()
        trimJob = scope.launch {
            delay(delayMs)
            trimUnclaimed()
        }
    }

    private fun trimUnclaimed() {
        val iterator = slots.entries.iterator()
        var changed = false
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in claimed) {
                release(entry.value)
                iterator.remove()
                changed = true
            }
        }
        if (changed) version++
    }

    private fun release(slot: ShortSlot) {
        slot.released = true
        slot.job?.cancel()
        slot.player.release()
    }

    private fun create(video: VideoItem): ShortSlot? {
        val context = app ?: return null
        val player = ExoPlayer.Builder(context)
            .setLoadControl(ShortsMedia.loadControl())
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = false
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                    true
                )
            }
        val slot = ShortSlot(video, player)
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                recover(slot)
            }

            override fun onRenderedFirstFrame() {
                slot.frame = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                slot.buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) slot.ready = true
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) slot.vertical = size.width.toFloat() / size.height < 0.8f
            }
        })
        slots[video.id] = slot
        version++
        start(slot)
        return slot
    }

    /** Lấy link → dựng DASH → nạp vào ExoPlayer và đứng ở khung đầu (tạm dừng, trừ khi ô này đang được xem). */
    private fun start(slot: ShortSlot) {
        val context = app ?: return
        if (slot.released) return
        slot.job?.cancel()
        slot.error = null
        slot.ready = false
        slot.frame = false
        slot.player.stop()
        slot.job = scope.launch {
            try {
                val plan = ShortsPlayback.plan(context, account, slot.video, slot.failed).await()
                slot.source = plan.source
                val source = withContext(Dispatchers.IO) { ShortsMedia.buildSource(context, plan) }
                if (slot.released) return@launch
                slot.player.setMediaSource(source, slot.resumeMs)
                slot.player.prepare()
                slot.player.playWhenReady = slot.wantPlay
                val loaded = withTimeoutOrNull(READY_TIMEOUT_MS) { snapshotFlow { slot.ready }.first { it } }
                if (loaded == null) recover(slot)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                slot.error = e.message ?: "Không phát được video này."
            }
        }
    }

    /**
     * Link bị từ chối / hết hạn / kẹt / ExoPlayer báo lỗi giữa chừng: đổi sang client khác lấy link mới rồi phát tiếp từ chỗ đang xem.
     * Quá [MAX_AUTO_RECOVERY] lần mới hiện lỗi cho người dùng bấm "Thử lại".
     */
    private fun recover(slot: ShortSlot) {
        if (slot.released) return
        if (slot.autoTries >= MAX_AUTO_RECOVERY) {
            slot.error = "Không phát được video này."
            return
        }
        slot.autoTries++
        slot.resumeMs = slot.player.currentPosition.coerceAtLeast(0L)
        slot.source?.let { slot.failed.add(it) }
        ShortsPlayback.forget(slot.video.id)
        start(slot)
    }
}
