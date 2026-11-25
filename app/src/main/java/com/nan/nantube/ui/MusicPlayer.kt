package com.nan.nantube.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.nan.nantube.data.Account
import com.nan.nantube.data.MusicItem
import com.nan.nantube.data.ShortsMedia
import com.nan.nantube.data.ShortsPlayback
import com.nan.nantube.data.VideoDownloads
import com.nan.nantube.data.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Một bài trong hàng đợi. [local] = file âm thanh đã tải (M4A/MP3) trong thư mục Tải xuống, phát không cần mạng. */
data class Track(val id: String, val title: String, val artist: String?, val art: String?, val local: Boolean = false)

enum class RepeatKind { Off, All, One }

/**
 * Trình phát nhạc dùng chung cho cả app (tab Music và mục nhạc đã tải).
 * Link âm thanh lấy bằng đúng `PlaybackSource` của meetube (xem [ShortsPlayback.plan]: VISIONOS → TV_TIZEN → WEB_EMBED → ANDROID_VR),
 * đọc qua bộ đệm đĩa + UA media của meetube ([ShortsMedia.dataSourceFactory]).
 */
object MusicPlayer {
    var queue by mutableStateOf<List<Track>>(emptyList())
        private set
    var index by mutableIntStateOf(-1)
        private set
    var playing by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeat by mutableStateOf(RepeatKind.All)
        private set
    var label by mutableStateOf("Danh sách phát")
        private set

    /** Bài đang phát đã được tài khoản thích chưa (nút trái tim). */
    var liked by mutableStateOf(false)
        private set
    private val likedCache = HashMap<String, Boolean>()

    /** Màn phát nhạc đang mở toàn màn hình (false = chỉ còn thanh nhỏ phía trên thanh tab). */
    var expanded by mutableStateOf(false)

    val current: Track? get() = queue.getOrNull(index)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var app: Context? = null
    private var account: Account? = null
    private var player: ExoPlayer? = null
    private var job: Job? = null
    private var retried = false
    private val past = ArrayList<Int>()

    fun position(): Long = player?.currentPosition ?: 0L
    fun duration(): Long = player?.duration?.takeIf { it > 0 } ?: 0L

    fun play(context: Context, account: Account?, tracks: List<Track>, start: Int, label: String, open: Boolean = true) {
        if (tracks.isEmpty()) return
        app = context.applicationContext
        this.account = account
        queue = tracks
        this.label = label
        past.clear()
        if (open) expanded = true
        load(start.coerceIn(0, tracks.size - 1))
    }

    fun playItems(context: Context, account: Account?, items: List<MusicItem>, tapped: MusicItem, label: String) {
        val songs = items.filter { it.videoId != null }
        val at = songs.indexOfFirst { it === tapped || it.videoId == tapped.videoId }.coerceAtLeast(0)
        play(context, account, songs.map { Track(it.videoId!!, it.title, it.subtitle, it.art) }, at, label)
    }

    fun playVideos(context: Context, account: Account?, videos: List<VideoItem>, tapped: VideoItem, label: String, local: Boolean) {
        val at = videos.indexOfFirst { it.id == tapped.id }.coerceAtLeast(0)
        play(context, account, videos.map { Track(it.id, it.title, it.channel, "https://i.ytimg.com/vi/${it.id}/hqdefault.jpg", local) }, at, label)
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val context = checkNotNull(app)
        return ExoPlayer.Builder(context).build().also { p ->
            p.setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true
            )
            p.setHandleAudioBecomingNoisy(true)
            p.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> loading = true
                        Player.STATE_READY -> { loading = false; retried = false }
                        Player.STATE_ENDED -> onEnded()
                    }
                }
                override fun onPlayerError(e: PlaybackException) { onFailed(e) }
            })
            player = p
        }
    }

    private fun load(i: Int, resumeMs: Long = 0L) {
        val track = queue.getOrNull(i) ?: return
        index = i
        liked = likedCache[track.id] ?: false
        refreshLike(track.id)
        loading = true
        error = null
        job?.cancel()
        job = scope.launch {
            try {
                val source = withContext(Dispatchers.IO) { source(track) }
                val p = ensurePlayer()
                p.setMediaSource(source, resumeMs)
                p.prepare()
                p.playWhenReady = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loading = false
                error = e.message?.takeIf { it.isNotBlank() } ?: "Không phát được bài này."
            }
        }
    }

    /** Hỏi YouTube bài này đã thích chưa (như nút tim ở Short: `next` của tài khoản). */
    private fun refreshLike(id: String) {
        val acc = account ?: return
        scope.launch {
            val status = ShortsPlayback.likeStatus(acc, id) ?: return@launch
            likedCache[id] = status
            if (current?.id == id) liked = status
        }
    }

    /** Thích / bỏ thích đúng như meetube (ActionsService.setLike / removeLike: `like/like`, `like/removelike`, body TV với target.videoId). */
    fun toggleLike() {
        val context = app ?: return
        val track = current ?: return
        val acc = account ?: run {
            android.widget.Toast.makeText(context, "Hãy đăng nhập để thích bài hát", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val now = !liked
        liked = now
        likedCache[track.id] = now
        scope.launch {
            try {
                ShortsPlayback.like(acc, track.id, now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                likedCache[track.id] = !now
                if (current?.id == track.id) liked = !now
                android.widget.Toast.makeText(context, "Không thích được bài hát", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun source(track: Track): MediaSource {
        val context = checkNotNull(app)
        if (track.local) {
            val uri = VideoDownloads.audioUri(context, track.id) ?: throw IllegalStateException("Không tìm thấy file âm thanh đã tải.")
            return ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context)).createMediaSource(MediaItem.fromUri(uri))
        }
        val plan = ShortsPlayback.plan(context, account, VideoItem(track.id, track.title, track.artist, null, null, false)).await()
        val stream = plan.audio ?: plan.audios.firstOrNull() ?: plan.muxed ?: throw IllegalStateException("YouTube không trả luồng âm thanh cho bài này.")
        return ProgressiveMediaSource.Factory(ShortsMedia.dataSourceFactory(context)).createMediaSource(MediaItem.fromUri(stream.url))
    }

    /** Như recovery của meetube: link bị từ chối thì bỏ kế hoạch cũ, lấy link mới và phát tiếp đúng chỗ đang nghe (một lần). */
    private fun onFailed(e: PlaybackException) {
        val track = current ?: return
        if (!track.local && !retried && ShortsMedia.isRefused(e.cause)) {
            retried = true
            ShortsPlayback.forget(track.id)
            load(index, position())
            return
        }
        loading = false
        error = if (track.local) "Không mở được file này." else "Không phát được bài này. Hãy thử lại."
    }

    private fun onEnded() {
        if (repeat == RepeatKind.One) { player?.seekTo(0); player?.play(); return }
        step(auto = true)
    }

    fun next() = step(auto = false)

    private fun step(auto: Boolean) {
        val n = queue.size
        if (n == 0) return
        val target = when {
            shuffle && n > 1 -> {
                past.add(index)
                generateSequence { (0 until n).random() }.first { it != index }
            }
            index + 1 < n -> index + 1
            repeat == RepeatKind.All || !auto -> 0
            else -> { player?.pause(); return }
        }
        load(target)
    }

    fun prev() {
        if (position() > 3_000L) { player?.seekTo(0); return }
        val n = queue.size
        if (n == 0) return
        val target = when {
            shuffle && past.isNotEmpty() -> past.removeAt(past.size - 1)
            index > 0 -> index - 1
            else -> n - 1
        }
        load(target)
    }

    fun toggle() {
        val p = player ?: return
        if (error != null) { load(index); return }
        if (p.isPlaying) p.pause() else p.play()
    }

    fun retry() = load(index)
    fun seekTo(ms: Long) { player?.seekTo(ms) }
    fun toggleShuffle() { shuffle = !shuffle }
    fun cycleRepeat() {
        repeat = when (repeat) { RepeatKind.All -> RepeatKind.One; RepeatKind.One -> RepeatKind.Off; RepeatKind.Off -> RepeatKind.All }
    }

    fun close() {
        job?.cancel()
        player?.release()
        player = null
        queue = emptyList()
        index = -1
        playing = false
        loading = false
        error = null
        expanded = false
    }
}
