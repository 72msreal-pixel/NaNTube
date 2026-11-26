package com.nan.nantube.data

import android.util.Log
import java.net.URLEncoder
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ghi video (Short) đã xem vào lịch sử của tài khoản, port từ `TrackingService` + `YouTubeMediaItemService.updateHistoryPosition`
 * của MediaServiceCore (meetube):
 *
 * 1. Lấy `ei` / `vm` / `of` từ `playbackTracking` của /player CỦA TÀI KHOẢN ([InnerTube.trackingPlayer], tương ứng
 *    `getAuthVideoInfo`: client TV + Bearer; phản hồi ẩn danh của VISIONOS / ANDROID_VR không gắn với tài khoản nên
 *    YouTube không ghi gì). Kết quả được nhớ để các lần ping sau không gọi lại /player (như `mMobileTrackingCache`).
 * 2. Gửi `stats/playback` + `stats/watchtime` như `TrackingService.updateWatchTimeFull`:
 *    - còn dưới 5% độ dài nữa là hết  => "xem trọn" (`createWatchRecordShort` + `updateWatchTimeShort`, final=1);
 *    - ngược lại => bản ghi "xem dở" (`createWatchRecordLong` nếu chưa có bản ghi trong 30 phút, rồi `updateWatchTimeLong`).
 *    Vị trí tối thiểu 3 giây (meetube: `Math.max(positionMs, 3_000)`), nên chỉ xem một chút vẫn vào "Video đã xem".
 * 3. Header gửi stats giống interceptor của MediaServiceCore: Bearer + UA/Referer của app TV (tài khoản mã) hoặc cookie +
 *    SAPISIDHASH (tài khoản web), kèm `X-Goog-Visitor-Id`, và KIỂM TRA mã HTTP (trước đây bỏ qua kết quả nên ping bị
 *    YouTube từ chối vẫn tưởng là đã ghi, còn lỗi lấy /player thì bị nuốt im lặng).
 */
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
        val cpn: String, val mobile: Boolean, val extra: String = ""
    )

    /** Bản ghi gần nhất (như `mPosition` của TrackingService): video, vị trí, thời điểm. */
    private class Position(val key: String, val sec: Float, val at: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex() // ping của cùng một video phải đi đúng thứ tự (create trước, update sau)
    private val trackingCache = HashMap<String, Tracking>()
    private var last: Position? = null
    private val random = SecureRandom()


    /**
     * Ghi [videoId] vào lịch sử với vị trí đang xem [positionSec] (độ dài [lengthSec] nếu biết). Chạy nền, lỗi chỉ ghi log
     * và lần ping sau tự thử lại. Gọi được nhiều lần cho cùng một video (đang xem, rồi khi rời đi).
     */
    fun update(account: Account?, videoId: String, positionSec: Float, lengthSec: Float? = null) {
        if (account == null || videoId.length != 11) return
        scope.launch {
            try {
                lock.withLock { send(account, videoId, positionSec, lengthSec) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Không ghi được lịch sử xem $videoId: ${e.message}")
            }
        }
    }

    /** Đánh dấu [videoId] đã xem trọn (Short xem hết). */
    fun markWatched(account: Account?, videoId: String, lengthSec: Float? = null) =
        update(account, videoId, Float.MAX_VALUE, lengthSec)

    /** Đổi tài khoản / đăng xuất: không dùng lại dữ liệu theo dõi của danh tính cũ (như `invalidateCache`). */
    fun clear() {
        scope.launch { lock.withLock { trackingCache.clear(); last = null } }
    }

    private suspend fun send(account: Account, videoId: String, positionRaw: Float, lengthHint: Float?) {
        val key = "${account.id}|$videoId"
        val tracking = tracking(account, videoId, key)
        val length = (tracking.length ?: lengthHint)?.takeIf { it > 0f } ?: 30f
        // Kẹp trong [3s, độ dài]; Short loop có thể báo vị trí vượt độ dài.
        val position = positionRaw.coerceAtLeast(MIN_POSITION_SEC).coerceAtMost(length)
        val headers = InnerTube.statsHeaders(account, tracking.visitor, tracking.mobile)

        fun e(s: String) = URLEncoder.encode(s, "UTF-8")
        val common = "&docid=${e(videoId)}&cpn=${tracking.cpn}&ei=${e(tracking.ei)}&vm=${e(tracking.vm)}" +
            (tracking.of?.let { "&of=${e(it)}" } ?: "") + tracking.extra

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
        val player = InnerTube.trackingPlayer(account, videoId, cpn)
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
        // Phiên web: stats cần biết client nào đã xin link (c / cver) như URL YouTube trả.
        val extra = if (account.kind == LoginKind.WEB) listOf("c", "cver", "cplayer").mapNotNull { k ->
            query.getQueryParameter(k)?.takeIf { it.isNotBlank() }?.let { "&$k=${e(it)}" }
        }.joinToString("") else ""
        val mobile = query.getQueryParameter("c") == "MWEB"
        return Tracking(ei, vm, of, length, player.visitor, now, cpn, mobile, extra).also { trackingCache[key] = it }
    }
}
