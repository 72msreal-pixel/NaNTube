package com.nan.nantube.data

import android.util.Log
import java.net.URLEncoder
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.os.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** TV dùng TrackingService của MeeTube; WEB lưu theo tài khoản ngay trên máy. */
object WatchHistory {
    private const val TAG = "WatchHistory"
    private const val STATS = "https://www.youtube.com/api/stats/"
    private const val HISTORY_RENEW_MS = 30L * 60 * 1000
    private const val TRACKING_TTL_MS = 25L * 60 * 1000
    private const val START_THRESHOLD_SEC = 3 * 60
    private const val MIN_POSITION_SEC = 3f
    private const val ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private class Tracking(
        val ei: String, val vm: String, val of: String?, val length: Float?, val visitor: String?, val at: Long,
        val cpn: String
    )

    /** Bản ghi gần nhất (như `mPosition` của TrackingService): video, vị trí, thời điểm. */
    private class Position(val key: String, val sec: Float, val at: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex() // ping của cùng một video phải đi đúng thứ tự (create trước, update sau)
    private val trackingCache = LinkedHashMap<String, Tracking>()
    private var last: Position? = null
    private val random = SecureRandom()


    internal data class Playback(val positionSec: Float, val lengthSec: Float?)

    /** Chạy cùng vòng đời phiên phát thật, không ghi ô nạp sẵn hoặc video chưa bắt đầu. */
    internal suspend fun track(account: Account?, video: VideoItem, sample: () -> Playback?) {
        if (account == null || video.id.length != 11) return
        var latest: Playback? = null
        var sent: Playback? = null
        var sentAt = 0L
        fun save(reading: Playback) {
            val previous = sent
            if (reading == previous) return
            if (account.kind == LoginKind.WEB) {
                val progress = reading.lengthSec?.takeIf { it.isFinite() && it > 0f }
                    ?.let { (reading.positionSec / it).coerceIn(0f, 1f) }
                WebWatchHistory.record(account, video, progress ?: 0f, newPlayback = previous == null)
            }
            // Cả hai loại tài khoản đều phải ping YouTube, nếu không video chỉ hiện trong app mà không có trên YouTube.
            sendInBackground(account, video.id, reading)
            sent = reading
            sentAt = SystemClock.elapsedRealtime()
        }
        fun capture() {
            sample()?.takeIf { it.positionSec.isFinite() && it.positionSec > 0f }?.let { latest = it }
        }
        try {
            while (true) {
                capture()
                latest?.let { reading ->
                    if ((sent == null && reading.positionSec >= MIN_POSITION_SEC) ||
                        (sent != null && SystemClock.elapsedRealtime() - sentAt >= 30_000L)) save(reading)
                }
                delay(if (sent == null) 200L else 1_000L)
            }
        } finally {
            capture()
            latest?.let(::save)
        }
    }

    private fun sendInBackground(account: Account, videoId: String, reading: Playback) {
        scope.launch {
            try {
                lock.withLock { send(account, videoId, reading.positionSec, reading.lengthSec) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Không ghi được lịch sử xem TV: ${e.javaClass.simpleName}")
            }
        }
    }

    private suspend fun send(account: Account, videoId: String, positionRaw: Float, lengthHint: Float?) {
        val key = "${account.id}|$videoId"
        val tracking = tracking(account, videoId, key)
        val length = (tracking.length ?: lengthHint)?.takeIf { it > 0f } ?: 30f
        // Kẹp trong [3s, độ dài]; Short loop có thể báo vị trí vượt độ dài.
        val position = positionRaw.coerceAtLeast(MIN_POSITION_SEC).coerceAtMost(length)
        val headers = if (account.kind == LoginKind.WEB) InnerTube.webStatsHeaders(account, tracking.visitor)
        else InnerTube.tvStatsHeaders(account, tracking.visitor)

        fun e(s: String) = URLEncoder.encode(s, "UTF-8")
        val common = "&docid=${e(videoId)}&cpn=${tracking.cpn}&ei=${e(tracking.ei)}&vm=${e(tracking.vm)}" +
            (tracking.of?.let { "&of=${e(it)}" } ?: "")

        suspend fun call(path: String) {
            val res = Http.get("$STATS$path&prettyPrint=false$common", headers)
            if (res.code !in 200..299) {
                // Dữ liệu theo dõi có thể đã cũ/bị từ chối: bỏ đi để lần sau lấy lại từ /player.
                trackingCache.remove(key)
                error("stats/${path.substringBefore('?')} HTTP ${res.code}")
            }
        }

        val now = System.currentTimeMillis()
        val previous = last?.takeIf { it.key == key }
        val almostWatched = length - position < length * 0.05f
        if (almostWatched) {
            // createWatchRecordShort + updateWatchTimeShort: đánh dấu đã xem trọn.
            call("playback?ns=yt&ver=2&final=1")
            call("watchtime?ns=yt&ver=2&final=1&st=$length&et=$length")
        } else {
            val old = previous?.sec ?: if (position < START_THRESHOLD_SEC) 0f else position
            if (previous == null || now - previous.at > HISTORY_RENEW_MS) {
                // createWatchRecordLong
                call("playback?ns=yt&ver=2&len=$length&cmt=$old")
            }
            // updateWatchTimeLong
            call("watchtime?ns=yt&ver=2&len=$length&st=$old&et=$position&cmt=$position")
        }
        last = Position(key, position, now)
        Log.d(TAG, "Đã ghi lịch sử xem $videoId pos=$position/$length final=$almostWatched")
    }

    private fun e(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun newCpn() = buildString { repeat(16) { append(ID_ALPHABET[random.nextInt(ID_ALPHABET.length)]) } }

    /** ei / vm / of từ /player của tài khoản (như `getTrackingFormatInfo` + `syncWithAuthFormatIfNeeded`), có nhớ tạm. */
    private suspend fun tracking(account: Account, videoId: String, key: String): Tracking {
        val now = System.currentTimeMillis()
        trackingCache[key]?.takeIf { now - it.at < TRACKING_TTL_MS }?.let { return it }
        // Như AppService.getClientPlaybackNonce: một cpn cho mỗi lần lấy /player, dùng chung cho body /player và mọi ping stats của video.
        val cpn = newCpn()
        val player = if (account.kind == LoginKind.WEB) InnerTube.webTrackingPlayer(account, videoId, cpn)
        else InnerTube.tvTrackingPlayer(account, videoId, cpn)
        val json = player.json
        val info = json.optJSONObject("playbackTracking")
        val watchUrl = info.dig("videostatsWatchtimeUrl", "baseUrl") as? String
            ?: info.dig("videostatsPlaybackUrl", "baseUrl") as? String
        if (watchUrl == null) {
            val status = json.dig("playabilityStatus", "status") as? String
            val reason = json.dig("playabilityStatus", "reason") as? String
            error("Không có playbackTracking (${listOfNotNull(status, reason).joinToString(" - ").ifBlank { "không rõ lý do" }})")
        }
        val query = android.net.Uri.parse(watchUrl)
        val ei = query.getQueryParameter("ei")
        val vm = query.getQueryParameter("vm")
        val of = query.getQueryParameter("of")?.takeIf { it.isNotBlank() }
        if (ei.isNullOrBlank() || vm.isNullOrBlank()) error("Thiếu ei/vm")
        val length = json.dig("videoDetails", "lengthSeconds")?.toString()?.toFloatOrNull()
        return Tracking(ei, vm, of, length, player.visitor, now, cpn).also {
            trackingCache.remove(key)
            if (trackingCache.size >= 64) trackingCache.remove(trackingCache.keys.first())
            trackingCache[key] = it
        }
    }
}
