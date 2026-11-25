\
package com.nan.nantube.data

import android.text.Html
import java.net.URLEncoder
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lyrics resolver inspired by MeeTube's Lyrics/LyricsSources flow:
 * identify the actual recording, query independent providers, validate artist/title/duration,
 * prefer synchronized lyrics, and only cache successful answers.
 *
 * UI remains owned by NaNTube. Results are normalized to CaptionCue so the existing player
 * transcript panel can render and follow them without importing MeeTube UI.
 */
object MusicLyrics {
    private val cache = ConcurrentHashMap<String, List<CaptionCue>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<List<CaptionCue>>>()

    suspend fun find(track: String, title: String, artistLine: String?): List<CaptionCue> {
        if (!Regex("[A-Za-z0-9_-]{11}").matches(track)) return emptyList()
        cache[track]?.let { return it }
        return withContext(Dispatchers.IO) {
            cache[track]?.let { return@withContext it }
            // Prevent simultaneous panel opens from launching duplicate provider searches.
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
        var title = cleanTitle(fallbackTitle)
        var artist = fallbackArtist.substringBefore(" • ").replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "").trim()
        var duration = 0
        var youtubeLyricsId: String? = null
        var isArtTrack = false

        // YouTube Music's own metadata is more reliable than the search-result subtitle.
        try {
            val next = musicPost("next", JSONObject().put("videoId", videoId))
            val details = next.optJSONObject("videoDetails")
            title = details?.optString("title")?.takeIf { it.isNotBlank() }?.let(::cleanTitle) ?: title
            artist = details?.optString("author")?.takeIf { it.isNotBlank() } ?: artist
            duration = details?.optString("lengthSeconds")?.toIntOrNull() ?: 0
            val panelVideos = findAllKeys(next, "playlistPanelVideoRenderer")
            val own = panelVideos.firstOrNull { it.optString("videoId") == videoId }
            if (own != null) {
                val byline = ytText(own.opt("longBylineText")).orEmpty()
                if (byline.isNotBlank()) artist = byline.substringBefore(" • ").trim()
                isArtTrack = own.toString().contains("MUSIC_VIDEO_TYPE_ATV")
            }
            val tabs = findAllKeys(next, "tabRenderer")
            youtubeLyricsId = tabs.firstNotNullOfOrNull { tab ->
                val browse = tab.dig("endpoint", "browseEndpoint") as? JSONObject
                browse?.takeIf {
                    !tab.optBoolean("unselectable") &&
                        it.toString().contains("MUSIC_PAGE_TYPE_TRACK_LYRICS")
                }?.optString("browseId")?.takeIf(String::isNotBlank)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { }

        val cleanArtist = artist.trim()
        val queryTitle = title.ifBlank { cleanTitle(fallbackTitle) }
        val queryArtist = cleanArtist.ifBlank { fallbackArtist }
        val answers = ArrayList<Pair<Boolean, List<CaptionCue>>>()

        // Independent sources: provider failures are isolated; a dead mirror must not block the others.
        suspend fun attempt(block: suspend () -> List<CaptionCue>) {
            try {
                val cues = block()
                if (cues.size >= 4) answers += (cues.firstOrNull()?.endMs == Long.MAX_VALUE) to cues
            } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }

        if (youtubeLyricsId != null) attempt {
            val body = musicPost("browse", JSONObject().put("browseId", youtubeLyricsId!!))
            val shelf = findAllKeys(body, "musicDescriptionShelfRenderer").firstOrNull()
            val text = ytText(shelf?.opt("description")).orEmpty()
            plainCues(text, "YouTube Music")
        }

        attempt { lrclib(queryTitle, queryArtist, duration) }
        attempt { betterLyrics(queryTitle, queryArtist, duration) }
        attempt { unison(queryTitle, queryArtist, duration) }
        attempt { kuGou(queryTitle, queryArtist, duration) }

        if (answers.isEmpty()) return emptyList()
        // Prefer synchronized answers only when this is the recording itself or durations agree.
        val timingAllowed = isArtTrack || duration <= 0
        val timed = answers.firstOrNull { !it.first && timingAllowed }
        return (timed ?: answers.first()).second
    }

    private suspend fun lrclib(title: String, artist: String, seconds: Int): List<CaptionCue> {
        val url = "https://lrclib.net/api/search?track_name=${enc(title)}&artist_name=${enc(artist)}" +
            if (seconds > 0) "&duration=$seconds" else ""
        val arr = JSONArray(Http.get(url, mapOf("User-Agent" to "NaNTube/1.0 (Android)"), 9000).also {
            if (it.code !in 200..299) throw Exception("LRCLIB HTTP ${it.code}")
        }.body)
        val hits = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .filter { hit ->
                val lyrics = hit.optString("plainLyrics").ifBlank { hit.optString("syncedLyrics") }
                lyrics.isNotBlank() && lineCount(lyrics) >= 4 &&
                    sameArtist(hit.optString("artistName"), artist)
            }
            .sortedBy { hit ->
                val d = hit.optDouble("duration", 0.0)
                (if (seconds > 0 && kotlin.math.abs(d - seconds) <= 3) 0 else 100000) +
                    if (seconds > 0) kotlin.math.abs(d - seconds) else 0.0
            }
        val hit = hits.firstOrNull() ?: return emptyList()
        val synced = hit.optString("syncedLyrics").takeIf { it.isNotBlank() }
        return if (synced != null && (seconds <= 0 || kotlin.math.abs(hit.optDouble("duration") - seconds) <= 3))
            parseLrc(synced)
        else plainCues(hit.optString("plainLyrics").ifBlank { synced.orEmpty() }, "LRCLIB")
    }

    private suspend fun betterLyrics(title: String, artist: String, seconds: Int): List<CaptionCue> {
        val url = "https://lyrics-api.boidu.dev/getLyrics?s=${enc(title)}&a=${enc(artist)}" +
            if (seconds > 0) "&d=$seconds" else ""
        val json = JSONObject(Http.get(url, timeoutMs = 9000).also {
            if (it.code !in 200..299) throw Exception("BetterLyrics HTTP ${it.code}")
        }.body)
        val ttml = json.optString("ttml")
        if (ttml.isNotBlank()) return parseTtml(ttml)
        return plainCues(json.optString("lyrics"), "BetterLyrics")
    }

    private suspend fun unison(title: String, artist: String, seconds: Int): List<CaptionCue> {
        val url = "https://unison.boidu.dev/lyrics?song=${enc(title)}&artist=${enc(artist)}" +
            if (seconds > 0) "&duration=$seconds" else ""
        val json = JSONObject(Http.get(url, timeoutMs = 9000).also {
            if (it.code !in 200..299) throw Exception("Unison HTTP ${it.code}")
        }.body)
        val data = json.optJSONObject("data")?.takeIf { json.optBoolean("success") } ?: return emptyList()
        val lyrics = data.optString("lyrics")
        return when (data.optString("format").lowercase(Locale.ROOT)) {
            "ttml" -> parseTtml(lyrics)
            "lrc" -> parseLrc(lyrics)
            else -> plainCues(lyrics, "Unison")
        }
    }

    private suspend fun kuGou(title: String, artist: String, seconds: Int): List<CaptionCue> {
        val keyword = "$title - ${artist.substringBefore(',').trim()}"
        val search = JSONObject(Http.get(
            "https://mobileservice.kugou.com/api/v3/search/song?version=9108&plat=0&pagesize=8&showtype=0&keyword=${enc(keyword)}",
            timeoutMs = 9000
        ).body).optJSONObject("data")?.optJSONArray("info") ?: return emptyList()
        val candidates = (0 until search.length()).mapNotNull { search.optJSONObject(it) }
            .filter { it.optString("hash").isNotBlank() }
            .sortedBy { if (seconds > 0) kotlin.math.abs(it.optInt("duration") - seconds) else 0 }
        val song = candidates.firstOrNull() ?: return emptyList()
        if (seconds > 0 && kotlin.math.abs(song.optInt("duration") - seconds) > 8) return emptyList()
        val hash = song.optString("hash")
        val found = JSONObject(Http.get("https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=${enc(hash)}", timeoutMs=9000).body)
            .optJSONArray("candidates")?.optJSONObject(0) ?: return emptyList()
        val download = JSONObject(Http.get(
            "https://lyrics.kugou.com/download?fmt=lrc&charset=utf8&client=pc&ver=1&id=${enc(found.optString("id"))}&accesskey=${enc(found.optString("accesskey"))}",
            timeoutMs=9000
        ).body)
        val raw = android.util.Base64.decode(download.optString("content"), android.util.Base64.DEFAULT)
            .toString(Charsets.UTF_8).removePrefix("\uFEFF")
        return parseLrc(raw)
    }

    private suspend fun musicPost(endpoint: String, body: JSONObject): JSONObject {
        val version = "1." + SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date()) + ".01.00"
        val client = JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", version).put("hl", "vi").put("gl", "VN")
        val payload = JSONObject(body.toString()).put("context", JSONObject().put("client", client))
        val origin = "https://music.youtube.com"
        return Http.postJson("$origin/youtubei/v1/$endpoint?prettyPrint=false", payload, mapOf(
            "Origin" to origin, "X-Origin" to origin, "Referer" to "$origin/",
            "X-Youtube-Client-Name" to "67", "X-Youtube-Client-Version" to version
        )).requireJson()
    }

    private fun parseLrc(raw: String): List<CaptionCue> {
        val stamp = Regex("^\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
        val points = ArrayList<Pair<Long, String>>()
        raw.lineSequence().forEach { line ->
            val matches = stamp.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val text = line.substring(matches.last().range.last + 1)
                .replace(Regex("<\\d{1,3}:\\d{2}[.:]\\d{1,3}>"), "").trim()
            if (text.isBlank()) return@forEach
            matches.forEach { m ->
                val frac = m.groupValues[3]
                val ms = m.groupValues[1].toLong() * 60000 + m.groupValues[2].toLong() * 1000 +
                    (frac.toLongOrNull() ?: 0L) * when (frac.length) { 1 -> 100; 2 -> 10; else -> 1 }
                points += ms to text
            }
        }
        return points.sortedBy { it.first }.mapIndexed { i, p ->
            CaptionCue(p.first, points.sortedBy { it.first }.getOrNull(i + 1)?.first ?: p.first + 6000, p.second)
        }
    }

    private fun parseTtml(ttml: String): List<CaptionCue> {
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setExpandEntityReferences(false)
        }.newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(ttml)))
        val nodes = doc.getElementsByTagName("p")
        val rows = (0 until nodes.length).mapNotNull { i ->
            val e = nodes.item(i) as? org.w3c.dom.Element ?: return@mapNotNull null
            val text = e.textContent.replace(Regex("\\s+"), " ").trim()
            if (text.isBlank()) return@mapNotNull null
            val begin = timeMs(e.getAttribute("begin"))
            if (begin < 0) null else begin to text
        }.sortedBy { it.first }
        return rows.mapIndexed { i, p -> CaptionCue(p.first, rows.getOrNull(i + 1)?.first ?: p.first + 6000, p.second) }
    }

    private fun timeMs(raw: String): Long {
        if (raw.isBlank()) return -1
        return try {
            if (raw.endsWith("ms")) raw.dropLast(2).toDouble().toLong()
            else raw.removeSuffix("s").split(":").fold(0.0) { acc, part -> acc * 60 + part.toDouble() }.let { (it * 1000).toLong() }
        } catch (_: Exception) { -1 }
    }

    private fun plainCues(raw: String, source: String): List<CaptionCue> {
        val text = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() && !it.matches(Regex("^\\[[^]]+]$")) }
        return lines.mapIndexed { i, line -> CaptionCue(-1L, Long.MAX_VALUE, line) }
    }

    private fun lineCount(s: String) = s.lines().count { it.isNotBlank() }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun cleanTitle(raw: String): String = raw.substringBefore(" | ")
        .replace(Regex("(?i)\\s*\\((official|lyrics?|audio|video)[^)]*\\)"), "")
        .replace(Regex("\\s+"), " ").trim()

    private fun sameArtist(a: String, b: String): Boolean {
        fun norm(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
        val left = norm(a); val right = norm(b)
        return left.isNotBlank() && right.isNotBlank() &&
            (left.contains(right) || right.contains(left) ||
                a.split(Regex("\\s*(,|&|/| feat\\.? | ft\\.? )\\s*", RegexOption.IGNORE_CASE))
                    .any { norm(it).let { n -> n.isNotBlank() && right.contains(n) } })
    }
}
