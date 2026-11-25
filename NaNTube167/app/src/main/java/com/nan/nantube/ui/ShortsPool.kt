package com.nan.nantube.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.nan.nantube.data.Account
import com.nan.nantube.data.Connectivity
import com.nan.nantube.data.NanStorage
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

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

    /** Đã phát hết video (chỉ xảy ra khi bật tự vuốt, lúc đó ô không lặp lại). */
    var ended by mutableStateOf(false)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set

    internal var source: String? = null
    internal var autoTries = 0
    internal var resumeMs = 0L
    internal val failed = HashSet<String>()
    internal var job: Job? = null
    internal var retryJob: Job? = null
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
    private const val RETRY_DELAY_MS = 4_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val slots = LinkedHashMap<String, ShortSlot>()
    private var app: Context? = null
    private var account: Account? = null
    private var claimed: Set<String> = emptySet()
    private var neighbourJob: Job? = null
    private var trimJob: Job? = null

    init {
        scope.launch { snapshotFlow { Connectivity.tick }.drop(1).collect { retryFailed() } }
    }

    private fun retryFailed() {
        slots.values.filter { it.error != null }.forEach { retry(it) }
    }

    /** Lỗi tải (mất mạng, link bị chặn…) không báo ra màn hình: ô cứ quay vòng và tự thử lại sau ít giây, hoặc ngay khi có mạng. */
    private fun giveUp(slot: ShortSlot) {
        slot.error = "Không phát được video này."
        slot.retryJob?.cancel()
        slot.retryJob = scope.launch {
            delay(RETRY_DELAY_MS)
            if (!slot.released && slot.error != null) retry(slot)
        }
    }

    /** Chất lượng người dùng chọn ở menu Short (cạnh ngắn của video, vd 480); null = Tự động. Áp cho mọi ô, kể cả ô nạp sau. */
    var quality by mutableStateOf<Int?>(null)
        private set

    /** Tốc độ phát người dùng chọn ở menu Short, áp cho mọi ô kể cả ô nạp sau. */
    var speed by mutableFloatStateOf(1f)
        private set

    /** Bản âm thanh người dùng chọn ([com.nan.nantube.data.AudioChoice.key]); null = bản gốc của từng video. Video nào không có bản đó thì dùng bản gốc. */
    var audioKey by mutableStateOf<String?>(null)
        private set

    /** Bật thì hết video là tự vuốt sang Short kế (ô không lặp lại); tắt thì lặp lại như YouTube. Lưu trong tệp NaNTube/guest/shorts_settings.json. */
    var autoScroll by mutableStateOf(false)
        private set

    fun selectSpeed(value: Float) {
        speed = value
        slots.values.forEach { it.player.setPlaybackSpeed(value) }
    }

    /** Đang mở bảng hoặc bình luận: video lặp lại thay vì kết thúc, để đóng bảng rồi mới tự vuốt không bị vuốt dồn. */
    private var held = false

    fun selectAutoScroll(on: Boolean) {
        autoScroll = on
        applyRepeat()
        saveSettings()
    }

    // ---- Cài đặt lưu vào tệp -----------------------------------------------------------------------

    private const val SETTINGS = "shorts_settings"
    private const val KEY_AUTO_SCROLL = "autoScroll"
    private var settingsHooked = false
    private var settingsOwner: String? = null

    /**
     * Cài đặt Short của từng tài khoản: NaNTube/accounts/<id tài khoản>/shorts_settings.json, chưa đăng nhập thì NaNTube/guest/shorts_settings.json
     * (cấu trúc chung của [NanStorage]). Đổi tài khoản thì nạp lại theo tài khoản mới; chưa có tệp thì về mặc định (tắt).
     */
    private fun loadSettings(owner: Account?) {
        val ownerId = owner?.id.orEmpty()
        scope.launch {
            val json = withContext(Dispatchers.IO) { NanStorage.readJson(SETTINGS, owner) as? JSONObject }
            // Trong lúc đọc mà đã đổi sang tài khoản khác thì bỏ kết quả này.
            if (settingsOwner != ownerId) return@launch
            autoScroll = json?.optBoolean(KEY_AUTO_SCROLL, false) ?: false
            applyRepeat()
        }
    }

    private fun saveSettings() {
        val owner = account
        val json = JSONObject().put(KEY_AUTO_SCROLL, autoScroll)
        scope.launch(Dispatchers.IO) { NanStorage.writeJson(SETTINGS, owner, json) }
    }

    fun selectAudio(key: String?) {
        audioKey = key
        for (slot in slots.values) applyAudio(slot)
    }

    private val AUDIO_REP = Regex("aud(\\d+)_")

    /** Số thứ tự bản âm thanh của một nhóm track (đọc từ id biểu diễn trong bản kê DASH, xem ShortsMedia.mpd). */
    private fun audioIndex(format: androidx.media3.common.Format): Int? =
        format.id?.let { AUDIO_REP.find(it)?.groupValues?.get(1)?.toIntOrNull() }

    /** Khóa bản âm thanh đang phát ở ô [id] (null nếu chưa nạp xong hoặc video chỉ có một bản). */
    fun playingAudio(id: String): String? {
        val slot = slots[id] ?: return null
        val choices = com.nan.nantube.data.ShortsPlayback.audioChoices(id)
        val group = slot.player.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 && it.isSelected } ?: return null
        val index = audioIndex(group.getTrackFormat(0)) ?: return null
        return choices.firstOrNull { it.index == index }?.key
    }

    /**
     * Ép ExoPlayer của ô vào đúng bản âm thanh: bản người dùng chọn nếu video có, không thì bản gốc (mặc định của video), kể cả khi
     * máy đặt ngôn ngữ khác và YouTube có bản lồng tiếng sang ngôn ngữ đó. Video chỉ có một bản thì để ExoPlayer tự chọn.
     */
    private fun applyAudio(slot: ShortSlot) {
        if (slot.released) return
        val player = slot.player
        val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
        if (groups.size < 2) return
        val choices = com.nan.nantube.data.ShortsPlayback.audioChoices(slot.video.id)
        if (choices.isEmpty()) return
        val want = audioKey?.let { key -> choices.firstOrNull { it.key == key } } ?: choices.first()
        val group = groups.firstOrNull { audioIndex(it.getTrackFormat(0)) == want.index } ?: return
        val params = player.trackSelectionParameters
        // Đã ép đúng rồi thì thôi, tránh vòng lặp onTracksChanged → đặt lại → onTracksChanged.
        if (params.overrides[group.mediaTrackGroup]?.trackIndices == listOf(0)) return
        player.trackSelectionParameters = params.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
            .build()
    }

    fun hold(on: Boolean) {
        held = on
        applyRepeat()
    }

    private fun repeatModeFor() = if (autoScroll && !held) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE

    private fun applyRepeat() {
        for (slot in slots.values) {
            slot.player.repeatMode = repeatModeFor()
            if (slot.ended && repeatModeFor() == Player.REPEAT_MODE_ONE) slot.player.seekTo(0L)
        }
    }

    /** Cạnh ngắn của khung hình đang phát ở ô [id] (để ghi "Tự động (480p)"); null nếu chưa có hình. */
    fun playingQuality(id: String): Int? =
        slots[id]?.player?.videoFormat?.let { minOf(it.width, it.height).takeIf { side -> side > 0 } }

    fun selectQuality(value: Int?) {
        quality = value
        for (slot in slots.values) applyQuality(slot)
    }

    /** Ép ExoPlayer của ô vào đúng mức [quality] (mức gần nhất nếu video không có đúng mức đó); Tự động thì bỏ ép. */
    private fun applyQuality(slot: ShortSlot) {
        if (slot.released) return
        val player = slot.player
        val params = player.trackSelectionParameters
        val want = quality
        if (want == null) {
            if (params.overrides.keys.any { it.type == C.TRACK_TYPE_VIDEO }) {
                player.trackSelectionParameters = params.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
            }
            return
        }
        for (group in player.currentTracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO || group.length == 0) continue
            var best = -1
            var bestGap = Int.MAX_VALUE
            for (i in 0 until group.length) {
                val f = group.getTrackFormat(i)
                val gap = kotlin.math.abs(minOf(f.width, f.height) - want)
                if (gap < bestGap) {
                    bestGap = gap
                    best = i
                }
            }
            if (best < 0) continue
            // Đã ép đúng rồi thì thôi, tránh vòng lặp onTracksChanged → đặt lại → onTracksChanged.
            if (params.overrides[group.mediaTrackGroup]?.trackIndices == listOf(best)) continue
            player.trackSelectionParameters = params.buildUpon()
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, best))
                .build()
        }
    }

    /** Tăng mỗi khi thêm / bỏ ô để màn Short (đọc qua [slot]) tự vẽ lại. */
    private var version by mutableIntStateOf(0)

    fun bind(context: Context, account: Account?) {
        app = context.applicationContext
        this.account = account
        val owner = account?.id.orEmpty()
        if (settingsOwner != owner) {
            settingsOwner = owner
            NanStorage.init(context)
            loadSettings(account)
        }
        if (!settingsHooked) {
            settingsHooked = true
            // Cấp quyền tệp xong thì đọc lại: tệp cũ của lần cài trước có thể đã nằm sẵn ở thư mục NaNTube.
            NanStorage.addReloadListener { scope.launch { loadSettings(this@ShortsPool.account) } }
        }
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
            if (want && slot.ended) slot.player.seekTo(0L)
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
        slot.retryJob?.cancel()
        slot.player.release()
    }

    private fun create(video: VideoItem): ShortSlot? {
        val context = app ?: return null
        Connectivity.start(context)
        val player = ExoPlayer.Builder(context)
            .setLoadControl(ShortsMedia.loadControl())
            .build().apply {
                repeatMode = repeatModeFor()
                playbackParameters = PlaybackParameters(ShortsPool.speed)
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

            override fun onTracksChanged(tracks: Tracks) {
                applyQuality(slot)
                applyAudio(slot)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                slot.buffering = playbackState == Player.STATE_BUFFERING
                slot.ended = playbackState == Player.STATE_ENDED
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
                giveUp(slot)
            }
        }
    }

    /**
     * Link bị từ chối / hết hạn / kẹt / ExoPlayer báo lỗi giữa chừng: đổi sang client khác lấy link mới rồi phát tiếp từ chỗ đang xem.
     * Quá [MAX_AUTO_RECOVERY] lần thì [giveUp]: ô quay vòng chờ rồi tự thử lại từ đầu.
     */
    private fun recover(slot: ShortSlot) {
        if (slot.released) return
        if (slot.autoTries >= MAX_AUTO_RECOVERY) {
            giveUp(slot)
            return
        }
        slot.autoTries++
        slot.resumeMs = slot.player.currentPosition.coerceAtLeast(0L)
        slot.source?.let { slot.failed.add(it) }
        ShortsPlayback.forget(slot.video.id)
        start(slot)
    }
}
