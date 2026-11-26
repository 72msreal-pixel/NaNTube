package com.nan.nantube.data

import android.util.Base64
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/**
 * Lời bài hát, tìm theo đúng cách MeeTube/BitChord làm:
 * 1. YouTube Music cho biết video này là bản thu nào (tên, nghệ sĩ, độ dài, tab Lời).
 * 2. lrc.red xác định bản thu đó (ISRC + độ dài thật).
 * 3. Hỏi đồng thời mọi nguồn, lấy theo thứ tự ưu tiên: bản có thời gian đầu tiên thắng, không có thì bản chữ đầu tiên.
 * 4. PaxSenix và Genius nặng hơn nên chỉ hỏi khi các nguồn trên chưa đủ.
 *
 * Kết quả quy về [CaptionCue] để bảng lời của player dùng lại. Thời gian chỉ giữ khi chắc chắn là của video này
 * (bản art track, hoặc độ dài bản thu khớp video); không thì hiện chữ thường để dòng sáng không chạy lệch tiếng hát.
 */
object MusicLyrics {
    private const val AGENT = "NaNTube/1.0 (Android)"
    private const val MUSIC_VERSION = "1.20250922.03.00"
    private const val MIN_LINES = 4
    private const val SAME_LENGTH_S = 3
    private const val KUGOU_SLACK_S = 8
    private const val ASK_MS = 8_000L
    private const val SLOW_MS = 20_000L
    private const val IDENTIFY_MS = 2_500L
    private const val NO_END = Long.MAX_VALUE

    // API web của Zing MP3 (không chính thức): các khóa nằm trong code web nên có thể đổi bất cứ lúc nào
    private const val ZING = "https://zingmp3.vn"
    private const val ZING_VERSION = "1.11.11"
    private const val ZING_KEY = "X5BM3w8N7MKozC0B85o4KMlzLZKhV00y"
    private const val ZING_SECRET = "acOrvUS15XRW2o9JksiK1KgQ6Vbds8ZW"

    private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    private val LYRICS_PLUS = listOf(
        "https://lyricsplus.binimum.org", "https://lyricsplus.prjktla.my.id", "https://lyricsplus.atomix.one",
        "https://lyricsplus.prjktla.workers.dev", "https://lyricsplus-seven.vercel.app",
        "https://lyrics-plus-backend.vercel.app"
    )

    private val STAMP = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val WORD_STAMP = Regex("<\\d{1,3}:\\d{2}[.:]\\d{1,3}>")
    private val ARTISTS = Regex("\\s*(?:,|&|/|\\band\\b|\\bfeat\\.?|\\bft\\.?|\\bx\\b)\\s*", RegexOption.IGNORE_CASE)
    private val NOISE = Regex(
        "[(\\[]\\s*(?:from|feat|ft|featuring|with|official|lyric|lyrical|video|audio|full)\\b[^)\\]]*[)\\]]" +
            "|\\s(?:feat|ft|featuring)\\.?\\s.*\$" +
            "|\\b(?:official\\s+)?(?:full\\s+)?(?:music|lyric(?:al)?|official)\\s+video(?:\\s+song)?\\b" +
            "|\\b(?:full\\s+)?video\\s+song\\b" +
            "|\\b(?:official\\s+audio|full\\s+(?:audio\\s+)?song|audio\\s+song|lyrics?|lyrical)\\b",
        RegexOption.IGNORE_CASE
    )
    private val CREDIT = Regex(
        "^(?:lyrics|lyricist|written|composed|composer|music|produced|producer|arranged|arranger|mixed|mastered)" +
            "(?:\\s+by)?\\s*[:\uFF1A]",
        RegexOption.IGNORE_CASE
    )
    private val TOPIC = Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE)
    private val BRACKETS = Regex("\\s*[(\\[][^)\\]]*[)\\]]")
    private val SECTION = Regex("^\\[[^\\]]*]$")
    private val SPACES = Regex("\\s+")
    private val NON_LETTER = Regex("[^\\p{L}\\p{N}]")
    private val BREAK = Regex("(?i)<br\\s*/?>")
    private val TAG = Regex("<[^>]+>")
    private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#\\d+|amp|lt|gt|quot|apos|nbsp);")
    private val QRC_LINE = Regex("^\\[(\\d+),\\d+](.*)$")
    private val QRC_WORD = Regex("\\(\\d+,\\d+\\)")
    private val QRC_CONTENT = Regex("LyricContent=\"([^\"]*)\"")
    private val APPLE_SCRIPT = Regex("/assets/index~[^\"]+\\.js")
    private val JWT = Regex("eyJ[\\w-]+\\.eyJ[\\w-]+\\.[\\w-]+")

    /**
     * Lời một nguồn trả về; [times] null nghĩa là chỉ có chữ.
     * [verified]: chính nguồn đó cho biết độ dài bản thu khớp video, nên timing dùng được dù chưa nhận diện được bản thu.
     */
    private class Lyric(val lines: List<String>, val times: List<Long>? = null, val verified: Boolean = false)

    /** Thứ mỗi nguồn được hỏi. */
    private class Query(val title: String, val artist: String, val seconds: Int, val isrc: String?, val lyricsId: String?)

    private class Recording(val isrc: String, val seconds: Int)

    /** Video này theo YouTube Music. */
    private class Track(val title: String, val artist: String, val seconds: Int, val audioTrack: Boolean, val lyricsId: String?)

    private val cache = ConcurrentHashMap<String, List<CaptionCue>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<List<CaptionCue>>>()

    @Volatile private var appleJwt: String? = null
    @Volatile private var zingJar: String? = null

    /** Các nguồn hỏi đồng thời, theo thứ tự ưu tiên của BitChord. */
    private val racing: List<suspend (Query) -> Lyric?> = listOf(
        { q -> q.isrc?.let { parseTtml(get("https://lrc.red/s/$it.ttml")) } },
        // BiniLyrics cũng là kho Apple của lrc.red nhưng tìm theo tên: chỉ cần khi chưa có ISRC
        { q -> if (q.isrc == null) biniLyrics(q) else null },
        { q -> betterLyrics(q) },
        { q -> portato(q) },
        { q -> lyricsPlus(q) },
        { q -> unison(q) },
        { q -> youtubeMusic(q) },
        { q -> zingMp3(q) },
        { q -> kuGou(q) },
        { q -> lrclib(q) }
    )

    suspend fun find(track: String, title: String, artistLine: String?, refresh: Boolean = false): List<CaptionCue> {
        if (!VIDEO_ID.matches(track)) return emptyList()
        if (refresh) cache.remove(track)
        cache[track]?.let { return it }
        return withContext(Dispatchers.IO) {
            cache[track]?.let { return@withContext it }
            // Mở bảng lời hai lần liền thì dùng chung một lượt tìm, không gọi nguồn hai lần.
            val deferred = synchronized(inFlight) {
                inFlight[track] ?: scope.async { resolve(track, title, artistLine.orEmpty()) }.also { inFlight[track] = it }
            }
            try {
                val result = deferred.await()
                if (result.isNotEmpty()) cache[track] = result
                result
            } finally {
                synchronized(inFlight) { if (inFlight[track] === deferred) inFlight.remove(track) }
            }
        }
    }

    private suspend fun resolve(videoId: String, fallbackTitle: String, fallbackArtist: String): List<CaptionCue> {
        val track = lookupTrack(videoId, fallbackTitle, fallbackArtist)
        val recording = identify(track.title, track.artist, track.seconds)
        val query = Query(track.title, track.artist, recording?.seconds ?: track.seconds, recording?.isrc, track.lyricsId)
        val timedOk = track.audioTrack ||
            (recording != null && track.seconds > 0 && abs(recording.seconds - track.seconds) <= SAME_LENGTH_S)
        val found = search(query, timedOk) ?: return emptyList()
        return toCues(found, timedOk || found.verified)
    }

    private suspend fun lookupTrack(videoId: String, fallbackTitle: String, fallbackArtist: String): Track {
        val next = quiet { musicPost("next", JSONObject().put("videoId", videoId)) }
        val own = next?.let { findAllKeys(it, "playlistPanelVideoRenderer") }?.firstOrNull { it.optString("videoId") == videoId }
        val artist = ytText(own?.opt("longBylineText"))?.substringBefore(" \u2022 ")?.trim()?.takeIf { it.isNotEmpty() }
            ?: fallbackArtist.substringBefore(" \u2022 ").replace(TOPIC, "").trim()
        val title = cleanTitle(ytText(own?.opt("title"))?.takeIf { it.isNotBlank() } ?: fallbackTitle, artist)
        val seconds = clockSeconds(ytText(own?.opt("lengthText")))
            .takeIf { it > 0 } ?: next?.optJSONObject("videoDetails")?.optString("lengthSeconds")?.toIntOrNull() ?: 0
        // Tab Lời bị YouTube Music làm xám (unselectable) khi bài không có lời
        val lyricsId = next?.let { findAllKeys(it, "tabRenderer") }.orEmpty().firstNotNullOfOrNull { tab ->
            (tab.dig("endpoint", "browseEndpoint") as? JSONObject)
                ?.takeIf { !tab.optBoolean("unselectable") && it.toString().contains("MUSIC_PAGE_TYPE_TRACK_LYRICS") }
                ?.optString("browseId")?.takeIf { it.isNotBlank() }
        }
        return Track(title, artist, seconds, own?.toString()?.contains("MUSIC_VIDEO_TYPE_ATV") == true, lyricsId)
    }

    /** lrc.red cho biết bản thu nào (ISRC, độ dài thật); chờ tối đa [IDENTIFY_MS] để host chậm không kéo cả lượt tìm. */
    private suspend fun identify(title: String, artist: String, videoSeconds: Int): Recording? = withTimeoutOrNull(IDENTIFY_MS) {
        quiet {
            JSONObject(get("https://lrc.red/search.json?q=${enc(title + " " + firstArtist(artist))}"))
                .optJSONArray("hits").objects()
                .filter {
                    it.optString("isrc").isNotEmpty() && sameArtist(it.optString("artist"), artist) &&
                        sameTitle(it.optString("title"), title)
                }
                .minByOrNull { if (videoSeconds > 0) abs(it.optDouble("duration", 0.0) - videoSeconds) else 0.0 }
                ?.let { Recording(it.optString("isrc"), it.optDouble("duration", 0.0).roundToInt()) }
        }
    }

    private suspend fun search(q: Query, timedOk: Boolean): Lyric? = coroutineScope {
        val answers = racing.map { provider -> async { ask(ASK_MS, q, provider) } }
        try {
            choose(answers, q, timedOk)
        } finally {
            answers.forEach { it.cancel() }
        }
    }

    private suspend fun choose(answers: List<Deferred<Lyric?>>, q: Query, timedOk: Boolean): Lyric? {
        var plain: Lyric? = null
        for (answer in answers) {
            val found = answer.await() ?: continue
            if (found.times != null && (timedOk || found.verified)) return found
            plain = plain ?: found
        }
        // PaxSenix tải 3 MB web player của Apple lấy token, Genius cào trang: chỉ khi cần mới hỏi
        if (timedOk || plain == null) {
            val apple = ask(SLOW_MS, q) { paxSenix(it) }
            if (apple != null && (apple.times != null || plain == null)) return apple
        }
        return plain ?: ask(SLOW_MS, q) { genius(it) }
    }

    /** Hỏi một nguồn; lỗi, quá hạn hay quá ít dòng (bài rác, bản stub) đều tính là không có. */
    private suspend fun ask(timeoutMs: Long, q: Query, provider: suspend (Query) -> Lyric?): Lyric? =
        withTimeoutOrNull(timeoutMs) { quiet { provider(q) } }?.takeIf { it.lines.size >= MIN_LINES }

    private suspend fun <T> quiet(block: suspend () -> T?): T? =
        try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    // ---- các nguồn ----

    private suspend fun biniLyrics(q: Query): Lyric? {
        val url = JSONObject(get("https://lyrics-api.binimum.org/?track=${enc(q.title)}&artist=${enc(q.artist)}${durationParam("&duration=", q)}"))
            .optJSONArray("results").objects().firstOrNull()?.optString("lyricsUrl").orEmpty()
        return if (url.isEmpty()) null else parseTtml(get(url))
    }

    private suspend fun betterLyrics(q: Query): Lyric? =
        JSONObject(get("https://lyrics-api.boidu.dev/getLyrics${betterParams(q)}")).optString("ttml")
            .takeIf { it.isNotEmpty() }?.let { parseTtml(it) }

    /** BetterLyrics qua QQ Music (QRC), chỉ có bài họ đã cache. */
    private suspend fun portato(q: Query): Lyric? =
        JSONObject(get("https://lyrics-api.boidu.dev/qq/getLyrics${betterParams(q)}")).optString("lyrics")
            .takeIf { it.isNotEmpty() }?.let { dropCredits(parseQrc(it), q.title) }

    private fun betterParams(q: Query) = "?s=${enc(q.title)}&a=${enc(q.artist)}${durationParam("&d=", q)}"

    /** YouLy+: gương đầu tiên trả lời được, phần lớn hay sập hay đổi chỗ. */
    private suspend fun lyricsPlus(q: Query): Lyric? {
        val path = "/v2/lyrics/get?title=${enc(q.title)}&artist=${enc(q.artist)}${durationParam("&duration=", q)}" +
            (q.isrc?.let { "&isrc=${enc(it)}" } ?: "")
        for (host in LYRICS_PLUS) {
            val answer = quiet { JSONObject(get(host + path)) } ?: continue
            val rows = answer.optJSONArray("lyrics").objects().mapNotNull { line ->
                val lead = StringBuilder()
                val backing = StringBuilder()
                for (syllable in line.optJSONArray("syllabus").objects()) {
                    (if (syllable.optBoolean("isBackground")) backing else lead).append(syllable.optString("text"))
                }
                val text = withEcho(lead.toString().trim().ifEmpty { line.optString("text").trim() }, backing.toString().trim())
                if (text.isEmpty()) null else line.optLong("time") to text
            }
            val timed = answer.optString("type").lowercase(Locale.ROOT) in setOf("word", "line", "syllable")
            return Lyric(rows.map { it.second }, if (timed) rows.map { it.first } else null)
        }
        return null
    }

    private suspend fun unison(q: Query): Lyric? {
        val answer = JSONObject(get("https://unison.boidu.dev/lyrics?song=${enc(q.title)}&artist=${enc(q.artist)}${durationParam("&duration=", q)}"))
        val data = answer.optJSONObject("data")?.takeIf { answer.optBoolean("success") } ?: return null
        val text = data.optString("lyrics").takeIf { it.isNotEmpty() } ?: return null
        val found = if (data.optString("format").equals("ttml", ignoreCase = true)) parseTtml(text) else parseLrc(text)
        return if (found.lines.isEmpty()) plain(text) else found
    }

    private suspend fun youtubeMusic(q: Query): Lyric? {
        val id = q.lyricsId ?: return null
        val shelf = findAllKeys(musicPost("browse", JSONObject().put("browseId", id)), "musicDescriptionShelfRenderer")
            .firstOrNull() ?: return null
        return plain(ytText(shelf.opt("description")).orEmpty())
    }

    /** Zing MP3: tìm bài lấy ID, rồi tải file LRC của bài đó. Kho lời tiếng Việt, phần lớn có timing. */
    private suspend fun zingMp3(q: Query): Lyric? {
        val song = zing("/api/v2/search", emptyMap(), mapOf("q" to "${q.title} ${firstArtist(q.artist)}"))
            .optJSONArray("songs").objects()
            .filter { sameArtist(it.optString("artistsNames"), q.artist) && sameTitle(it.optString("title"), q.title) }
            .associateWith { if (q.seconds > 0) abs(it.optInt("duration") - q.seconds) else 0 }
            .minByOrNull { it.value } ?: return null
        val data = zing("/api/v2/lyric/get/lyric", mapOf("id" to song.key.optString("encodeId")))
        val file = data.optString("file")
        val lyric = if (file.isNotEmpty()) parseLrc(get(file)) else {
            // không có file LRC thì ghép từ các câu: mốc của dòng là mốc từ đầu tiên
            val rows = data.optJSONArray("sentences").objects().mapNotNull { sentence ->
                val words = sentence.optJSONArray("words").objects()
                if (words.isEmpty()) null else words.first().optLong("startTime") to words.joinToString(" ") { it.optString("data") }
            }
            Lyric(rows.map { it.second }, rows.map { it.first })
        }
        return if (q.seconds > 0 && song.value <= SAME_LENGTH_S) lyric.verified() else lyric
    }

    /**
     * Gọi API web của Zing: sig = HMAC-SHA512(path + SHA256(các tham số đã ký, xếp theo tên, nối "tên=giá trị")).
     * [signed] nằm trong chữ ký; [extra] chỉ đi kèm query (như q của tìm kiếm).
     */
    private suspend fun zing(path: String, signed: Map<String, String>, extra: Map<String, String> = emptyMap()): JSONObject {
        val params = (signed + mapOf("ctime" to (System.currentTimeMillis() / 1000).toString(), "version" to ZING_VERSION)).toSortedMap()
        val sig = hex(Mac.getInstance("HmacSHA512").apply { init(SecretKeySpec(ZING_SECRET.toByteArray(), "HmacSHA512")) }
            .doFinal((path + hex(MessageDigest.getInstance("SHA-256").digest(params.entries.joinToString("") { "${it.key}=${it.value}" }.toByteArray()))).toByteArray()))
        val query = (extra + params + mapOf("apiKey" to ZING_KEY, "sig" to sig)).entries.joinToString("&") { "${it.key}=${enc(it.value)}" }
        val answer = JSONObject(get("$ZING$path?$query", mapOf("Cookie" to zingCookie(), "User-Agent" to Http.DEFAULT_UA)))
        if (answer.optInt("err") != 0) throw Exception("Zing err ${answer.optInt("err")}")
        return answer.optJSONObject("data") ?: throw Exception("Zing không có data")
    }

    /** Zing đòi cookie của trang chủ; lấy một lần rồi dùng lại. */
    private suspend fun zingCookie(): String = zingJar ?: withContext(Dispatchers.IO) {
        val conn = URL(ZING).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", Http.DEFAULT_UA)
            conn.responseCode
            conn.headerFields["Set-Cookie"].orEmpty().joinToString("; ") { it.substringBefore(';') }
        } finally {
            conn.disconnect()
        }
    }.also { if (it.isNotEmpty()) zingJar = it }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    /** KuGou: tìm bài lấy hash, tìm lời của hash đó, rồi tải LRC. Mạnh với bài ngoài tiếng Anh. */
    private suspend fun kuGou(q: Query): Lyric? {
        val keyword = "${BRACKETS.replace(q.title, "").trim()} - ${BRACKETS.replace(firstArtist(q.artist), "").trim()}"
        val song = JSONObject(get("https://mobileservice.kugou.com/api/v3/search/song?version=9108&plat=0&pagesize=8&showtype=0&keyword=${enc(keyword)}"))
            .optJSONObject("data")?.optJSONArray("info").objects()
            .filter { it.optString("hash").isNotEmpty() }
            .associateWith { if (q.seconds > 0) abs(it.optInt("duration") - q.seconds) else 0 }
            .filterValues { it <= KUGOU_SLACK_S }
            .minByOrNull { it.value } ?: return null
        val hash = song.key.optString("hash")
        val candidate = JSONObject(get("https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=${enc(hash)}"))
            .optJSONArray("candidates").objects().firstOrNull() ?: return null
        val content = JSONObject(get("https://lyrics.kugou.com/download?fmt=lrc&charset=utf8&client=pc&ver=1&id=${enc(candidate.optString("id"))}&accesskey=${enc(candidate.optString("accesskey"))}"))
            .optString("content").takeIf { it.isNotEmpty() } ?: return null
        val lrc = String(Base64.decode(content, Base64.DEFAULT), Charsets.UTF_8).removePrefix("\uFEFF")
        val lyric = dropCredits(parseLrc(lrc), q.title)
        return if (q.seconds > 0 && song.value <= SAME_LENGTH_S) lyric.verified() else lyric
    }

    private suspend fun lrclib(q: Query): Lyric? {
        fun lyricsOf(hit: JSONObject) = hit.optText("plainLyrics") ?: hit.optText("syncedLyrics")
        fun timed(hit: JSONObject) = hit.optText("syncedLyrics") != null && q.seconds > 0 &&
            abs(hit.optDouble("duration", 0.0) - q.seconds) <= SAME_LENGTH_S

        // Tìm theo tên bài thôi rồi lọc theo nghệ sĩ: hit của người khác là bài khác trùng tên
        val best = JSONArray(get("https://lrclib.net/api/search?track_name=${enc(q.title)}")).objects()
            .filter { hit ->
                lyricsOf(hit).orEmpty().trim().lines().size >= MIN_LINES &&
                    (q.artist.isEmpty() || sameArtist(hit.optString("artistName"), q.artist))
            }
            .minByOrNull { hit ->
                (if (timed(hit)) 0.0 else 100_000.0) +
                    (if (q.seconds > 0) abs(hit.optDouble("duration", 0.0) - q.seconds) else 0.0)
            } ?: return null
        if (timed(best)) return parseLrc(best.optString("syncedLyrics")).verified()
        return Lyric(lyricsOf(best).orEmpty().trim().lines().map { WORD_STAMP.replace(STAMP.replace(it, ""), "").trim() })
    }

    /** Danh mục Apple tìm theo tên, bằng token chính web player của Apple, rồi lấy TTML qua PaxSenix. */
    private suspend fun paxSenix(q: Query): Lyric? {
        val apple = mapOf("Origin" to "https://music.apple.com", "Referer" to "https://music.apple.com/")
        val found = try {
            get(
                "https://amp-api.music.apple.com/v1/catalog/us/search?term=${enc(q.title + " " + firstArtist(q.artist))}&types=songs&limit=10&l=en-US",
                apple + ("Authorization" to "Bearer ${appleToken()}")
            )
        } catch (e: Exception) {
            appleJwt = null // hết hạn hoặc bị từ chối: lần sau cào lại
            throw e
        }
        val id = JSONObject(found).optJSONObject("results")?.optJSONObject("songs")?.optJSONArray("data").objects()
            .mapNotNull { song -> song.optJSONObject("attributes")?.let { song to it } }
            .filter { (_, about) -> sameArtist(about.optString("artistName"), q.artist) && sameTitle(about.optString("name"), q.title) }
            .minByOrNull { (_, about) -> if (q.seconds > 0) abs(about.optLong("durationInMillis") / 1000.0 - q.seconds) else 0.0 }
            ?.first?.optString("id")?.takeIf { it.isNotEmpty() } ?: return null
        return JSONObject(get("https://lyrics.paxsenix.org/apple-music/lyrics?id=${enc(id)}&ttml=true"))
            .optString("content").takeIf { it.isNotEmpty() }?.let { parseTtml(it) }
    }

    private suspend fun appleToken(): String = appleJwt ?: run {
        val script = APPLE_SCRIPT.find(get("https://music.apple.com/us/new", mapOf("Accept" to "text/html")))?.value
            ?: throw Exception("no Apple script")
        (JWT.find(get("https://music.apple.com$script", timeoutMs = 20_000))?.value ?: throw Exception("no Apple token"))
            .also { appleJwt = it }
    }

    /** Genius: tìm bài, rồi lấy chữ trong các khối lyrics của trang. Cuối cùng mới dùng tới. */
    private suspend fun genius(q: Query): Lyric? {
        val html = mapOf("Accept" to "text/html,application/json")
        val song = JSONObject(get("https://genius.com/api/search/multi?q=${enc(firstArtist(q.artist) + " " + q.title)}", html))
            .optJSONObject("response")?.optJSONArray("sections").objects()
            .filter { it.optString("type") == "song" }
            .flatMap { it.optJSONArray("hits").objects() }
            .mapNotNull { it.optJSONObject("result") }
            .firstOrNull {
                sameArtist(it.optString("artist_names"), q.artist) && sameTitle(it.optString("title"), q.title) &&
                    it.optString("url").isNotEmpty()
            } ?: return null
        return Lyric(geniusLines(get(song.optString("url"), html)))
    }

    private fun geniusLines(html: String): List<String> {
        val lines = ArrayList<String>()
        var at = 0
        while (true) {
            at = html.indexOf("data-lyrics-container=\"true\"", at)
            if (at < 0) break
            val end = divEnd(html, html.lastIndexOf("<div", at))
            if (end < 0) break
            var inner = html.substring(html.indexOf('>', at) + 1, end - "</div>".length)
            while (true) {
                val excluded = inner.indexOf("data-exclude-from-selection=\"true\"")
                if (excluded < 0) break
                val from = inner.lastIndexOf("<div", excluded)
                val to = divEnd(inner, from)
                if (to < 0) break
                inner = inner.substring(0, from) + inner.substring(to)
            }
            val text = unescape(TAG.replace(BREAK.replace(inner, "\n"), ""))
            for (line in text.lines().map { it.trim() }) {
                val skip = SECTION.matches(line) || (line.isEmpty() && (lines.isEmpty() || lines.last().isEmpty()))
                if (!skip) lines += line
            }
            at = end
        }
        while (lines.lastOrNull()?.isEmpty() == true) lines.removeAt(lines.lastIndex)
        return lines
    }

    /** Chỗ thẻ `<div` ở [open] đóng lại (ngay sau `</div>`); -1 nếu không đóng. */
    private fun divEnd(html: String, open: Int): Int {
        if (open < 0) return -1
        var depth = 0
        var at = open
        while (true) {
            val nextOpen = html.indexOf("<div", at)
            val nextClose = html.indexOf("</div", at)
            if (nextClose < 0) return -1
            if (nextOpen in 0 until nextClose) {
                depth++
                at = nextOpen + 4
            } else {
                depth--
                at = html.indexOf('>', nextClose) + 1
                if (at == 0) return -1
                if (depth == 0) return at
            }
        }
    }

    // ---- định dạng ----

    /** "[mm:ss.xx] chữ": một dòng nhiều mốc (điệp khúc) xuất hiện ở từng mốc. */
    private fun parseLrc(lrc: String): Lyric {
        val rows = ArrayList<Pair<Long, String>>()
        for (raw in lrc.lines()) {
            val stamps = ArrayList<Long>()
            var end = 0
            for (m in STAMP.findAll(raw)) {
                if (m.range.first != end) break
                stamps += stampMs(m)
                end = m.range.last + 1
            }
            val text = WORD_STAMP.replace(raw.substring(end), "").trim()
            if (text.isEmpty()) continue // thẻ metadata hoặc mốc chỉ để ngắt nghỉ
            stamps.forEach { rows += it to text }
        }
        rows.sortBy { it.first }
        return Lyric(rows.map { it.second }, rows.map { it.first })
    }

    private fun stampMs(m: MatchResult): Long {
        val (minutes, seconds, fraction) = m.destructured
        val scale = when (fraction.length) { 1 -> 100L; 2 -> 10L; else -> 1L }
        return minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + (fraction.toLongOrNull() ?: 0L) * scale
    }

    /**
     * TTML của Apple Music: mỗi `<p begin>` là một dòng hát, giọng đáp (ttm:role x-bg) đặt trong ngoặc sau lời chính,
     * bản dịch và phiên âm bỏ qua. Đọc bằng DOM không namespace để máy và JVM cho kết quả như nhau.
     */
    private fun parseTtml(ttml: String): Lyric {
        val factory = DocumentBuilderFactory.newInstance()
        mapOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false
        ).forEach { (feature, on) -> quietly { factory.setFeature(feature, on) } } // parser của Android không có hết
        factory.setExpandEntityReferences(false)
        val paragraphs = factory.newDocumentBuilder().parse(InputSource(StringReader(ttml))).getElementsByTagName("p")
        val sung = ArrayList<Pair<Long, String>>()
        for (i in 0 until paragraphs.length) {
            val paragraph = paragraphs.item(i) as Element
            val lead = StringBuilder()
            val backing = StringBuilder()
            collect(paragraph, lead, backing, false)
            val text = withEcho(squash(lead), squash(backing))
            if (text.isNotEmpty()) sung += ttmlTime(paragraph.getAttribute("begin")) to text
        }
        val timed = sung.all { it.first >= 0 }
        val rows = if (timed) sung.sortedBy { it.first } else sung
        return Lyric(rows.map { it.second }, if (timed) rows.map { it.first } else null)
    }

    private fun collect(node: Node, lead: StringBuilder, backing: StringBuilder, inBacking: Boolean) {
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                val role = attribute(child, "ttm:role")
                if (role != "x-translation" && role != "x-roman") collect(child, lead, backing, inBacking || role == "x-bg")
            } else if (child.nodeType == Node.TEXT_NODE) {
                (if (inBacking) backing else lead).append(child.nodeValue)
            }
        }
    }

    /** JVM giữ nguyên "ttm:role", Android lưu thành "role". */
    private fun attribute(element: Element, name: String): String {
        val direct = element.getAttribute(name)
        if (direct.isNotEmpty()) return direct
        val local = name.substringAfter(':')
        val attributes = element.attributes ?: return ""
        for (i in 0 until attributes.length) {
            val item = attributes.item(i)
            if (item.nodeName == name || item.nodeName == local || item.localName == local) return item.nodeValue
        }
        return ""
    }

    /** "12.345", "1:02.345", "00:01:02.345", "62.3s" hay "62345ms" ra ms; -1 khi thiếu hoặc đọc không được. */
    private fun ttmlTime(value: String?): Long {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return -1L
        return try {
            if (raw.endsWith("ms")) raw.dropLast(2).toDouble().toLong()
            else (raw.removeSuffix("s").split(":").fold(0.0) { sum, part -> sum * 60 + part.toDouble() } * 1000).roundToLong()
        } catch (_: NumberFormatException) { -1L }
    }

    /** QRC của QQ Music: "[start,len]Chữ(start,len) ..." mỗi dòng. */
    private fun parseQrc(qrc: String): Lyric {
        val text = QRC_CONTENT.find(qrc)?.let { unescape(it.groupValues[1]) } ?: qrc
        val rows = text.lines().mapNotNull { raw ->
            QRC_LINE.matchEntire(raw.trim())?.let { m ->
                val words = QRC_WORD.replace(m.groupValues[2], "").trim()
                if (words.isEmpty()) null else m.groupValues[1].toLong() to words
            }
        }
        return Lyric(rows.map { it.second }, rows.map { it.first })
    }

    private fun Lyric.verified() = Lyric(lines, times, true)

    private fun plain(text: String) = Lyric(text.trim().lines().map { it.trim() })

    /** Bỏ dòng tên bài và dòng credit một số nguồn chèn đầu bài ("Bài - Ca sĩ", "Lyrics by：..."). */
    private fun dropCredits(lyric: Lyric, title: String): Lyric {
        val name = letters(title)
        val skip = lyric.lines.take(8).takeWhile { line ->
            '\uFF1A' in line || CREDIT.containsMatchIn(line) || (" - " in line && name.isNotEmpty() && letters(line).contains(name))
        }.size
        return if (skip == 0) lyric else Lyric(lyric.lines.drop(skip), lyric.times?.drop(skip), lyric.verified)
    }

    private fun toCues(lyric: Lyric, timed: Boolean): List<CaptionCue> {
        val times = lyric.times
        if (!timed || times == null) {
            return lyric.lines.filter { it.isNotBlank() && !SECTION.matches(it) }.map { CaptionCue(-1L, NO_END, it) }
        }
        return lyric.lines.mapIndexed { i, line -> CaptionCue(times[i], times.getOrNull(i + 1) ?: (times[i] + 6_000), line) }
    }

    // ---- tên bài và nghệ sĩ ----

    /**
     * Tên YouTube về tên mà trang lời bài hát lưu: "Bài Video Song | Phim | ..." thành "Bài",
     * "Ca sĩ - Bài (Official Video)" thành "Bài". "(Remix)", "(Live)" giữ lại vì là bản thu khác.
     */
    private fun cleanTitle(title: String, artist: String): String {
        var name = NOISE.replace(title.substringBefore(" | "), " ")
        val dash = name.indexOf(" - ")
        if (dash > 0 && artist.isNotEmpty() && sameArtist(name.substring(0, dash), artist)) name = name.substring(dash + 3)
        name = SPACES.replace(name, " ").trim().trimEnd { it.isWhitespace() || it in "-\u2013\u2014,:" }
        return name.ifEmpty { title.trim() }
    }

    private fun sameTitle(theirs: String, ours: String): Boolean {
        val a = letters(cleanTitle(theirs, ""))
        val b = letters(ours)
        return a.isNotEmpty() && b.isNotEmpty() && (a.contains(b) || b.contains(a))
    }

    /** Chỉ cần trùng một tên: "Sai Abhyankkar" nằm trong "A, B & Sai Abhyankkar". */
    private fun sameArtist(theirs: String, ours: String): Boolean {
        val all = letters(ours)
        if (ARTISTS.split(theirs).any { letters(it).let { one -> one.isNotEmpty() && all.contains(one) } }) return true
        val first = letters(firstArtist(ours))
        return first.isNotEmpty() && letters(theirs).contains(first)
    }

    private fun firstArtist(artist: String) = ARTISTS.split(artist).first().trim()

    private fun letters(name: String) = NON_LETTER.replace(name.lowercase(Locale.ROOT), "")

    // ---- mạng và tiện ích ----

    private suspend fun musicPost(endpoint: String, body: JSONObject): JSONObject {
        val client = JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", MUSIC_VERSION).put("hl", "en")
        val payload = JSONObject(body.toString()).put("context", JSONObject().put("client", client))
        val origin = "https://music.youtube.com"
        return Http.postJson("$origin/youtubei/v1/$endpoint?prettyPrint=false", payload, mapOf(
            "Origin" to origin, "X-Origin" to origin, "Referer" to "$origin/",
            "X-Youtube-Client-Name" to "67", "X-Youtube-Client-Version" to MUSIC_VERSION
        )).requireJson()
    }

    private suspend fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 8_000): String {
        val answer = Http.get(url, mapOf("User-Agent" to AGENT, "Accept" to "application/json") + headers, timeoutMs)
        if (answer.code !in 200..299) throw Exception("HTTP ${answer.code}")
        return answer.body
    }

    private fun quietly(block: () -> Unit) {
        try { block() } catch (_: Exception) { }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    /** Khác optString: khóa thiếu hoặc null trả null chứ không phải "" / "null". */
    private fun JSONObject.optText(key: String): String? = if (isNull(key)) null else optString(key)

    private fun withEcho(text: String, echo: String) =
        if (echo.isEmpty()) text else "$text ${if (echo.startsWith("(")) echo else "($echo)"}".trim()

    private fun squash(text: CharSequence) = SPACES.replace(text, " ").trim()

    /** "3:46" hay "1:02:03" ra giây; 0 khi đọc không được. */
    private fun clockSeconds(text: String?): Int {
        var total = 0
        for (part in text.orEmpty().trim().split(":")) total = total * 60 + (part.toIntOrNull() ?: return 0)
        return total
    }

    private fun unescape(text: String) = ENTITY.replace(text) { m ->
        when (val name = m.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            "nbsp" -> " "
            else -> String(Character.toChars(if (name.startsWith("#x")) name.substring(2).toInt(16) else name.substring(1).toInt()))
        }
    }

    private fun durationParam(name: String, q: Query) = if (q.seconds > 0) name + q.seconds else ""

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
