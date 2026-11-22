package com.nan.nantube.data

import android.content.Context
import java.io.IOException
import java.util.UUID
import org.json.JSONObject
import org.json.JSONTokener

/** Dùng chung thứ tự fallback giữa Shorts và nút Tải xuống. */
internal enum class PlaybackSource {
    VISIONOS, ANDROID_VR, TV_TIZEN, WEB_EMBED, ACCOUNT;

    suspend fun load(context: Context, account: Account?, id: String): JSONObject = when (this) {
        VISIONOS -> InnerTube.directPlayer(id, vision = true)
        ANDROID_VR -> InnerTube.directPlayer(id, vision = false)
        TV_TIZEN -> CipherPlayer.load(context, id, embed = false)
        WEB_EMBED -> CipherPlayer.load(context, id, embed = true)
        ACCOUNT -> InnerTube.call("player", JSONObject().put("videoId", id), account)
    }

    companion object {
        fun available(): List<PlaybackSource> = if (PlayerJs.supported()) entries else listOf(VISIONOS, ANDROID_VR, ACCOUNT)
    }
}

private object CipherPlayer {
    private const val ORIGIN = "https://www.youtube.com"
    private const val EMBED_REFERER = "https://www.reddit.com/" // Giữ cùng embed context như MeeTube.
    private const val EMBED_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
        "(KHTML, like Gecko) Version/15.5 Safari/605.1.15,gzip(gfe)"
    private const val TIZEN_UA = "Mozilla/5.0 (SMART-TV; Linux; Tizen 2.4.0) AppleWebKit/538.1 " +
        "(KHTML, like Gecko) Version/2.4.0 TV Safari/538.1"
    private val videoIdPattern = Regex("[A-Za-z0-9_-]{11}")
    private val configStart = Regex("ytcfg\\.set\\(\\s*(?=\\{)")

    private class Embed(val visitor: String, val flags: String, val playerId: String?)

    suspend fun load(context: Context, videoId: String, embed: Boolean): JSONObject {
        require(videoIdPattern.matches(videoId)) { "ID video không hợp lệ" }
        val ua = if (embed) EMBED_UA else TIZEN_UA
        val embedConfig = if (embed) embedConfig(videoId, ua) else null
        val player = PlayerJs.get(embedConfig?.playerId)
        val visitor = embedConfig?.visitor ?: InnerTube.visitorData()
        val clientVersion = if (embed) "2.20260708.00.00" else "5.20260707"
        val client = JSONObject()
            .put("clientName", if (embed) "WEB_EMBEDDED_PLAYER" else "TVHTML5")
            .put("clientVersion", clientVersion).put("clientScreen", "WATCH").put("userAgent", ua)
            .put("browserName", "Safari").put("browserVersion", if (embed) "605.1.15" else "538.1")
            .put("hl", "vi").put("gl", "VN").put("acceptLanguage", "vi").put("acceptRegion", "VN")
            .put("utcOffsetMinutes", (java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000).toString())
        if (visitor != null) client.put("visitorData", visitor)
        if (!embed) client.put("deviceMake", "Samsung").put("deviceModel", "SmartTV")
            .put("osName", "Tizen").put("osVersion", "2.4.0")
        val ctx = JSONObject().put("client", client)
        if (embed) ctx.put("thirdParty", JSONObject().put("embedUrl", EMBED_REFERER))
        val content = JSONObject().put("signatureTimestamp", player.sts)
            .put("html5Preference", "HTML5_PREF_WANTS").put("lactMilliseconds", 60000)
        if (embedConfig != null) content.put("encryptedHostFlags", embedConfig.flags)
        val playback = JSONObject().put("contentPlaybackContext", content)
            .put("devicePlaybackCapabilities", JSONObject().put("supportsVp9Encoding", true).put("supportXhr", false))
        val body = JSONObject().put("context", ctx).put("playbackContext", playback)
            .put("videoId", videoId).put("cpn", UUID.randomUUID().toString().replace("-", "").take(16))
        val headers = mutableMapOf(
            "Origin" to ORIGIN, "User-Agent" to ua, "X-YouTube-Client-Name" to if (embed) "56" else "7"
        )
        headers["X-YouTube-Client-Version"] = clientVersion
        headers["Referer"] = if (embed) "$ORIGIN/embed/$videoId" else "$ORIGIN/tv"
        if (visitor != null) headers["X-Goog-Visitor-Id"] = visitor
        val result = Http.postJson("$ORIGIN/youtubei/v1/player?prettyPrint=false", body, headers).requireJson()
        if (result.dig("playabilityStatus", "status") == "OK") {
            result.optJSONObject("streamingData")?.let { PlayerJs.normalize(context, player, it) }
        }
        return result
    }

    private suspend fun embedConfig(videoId: String, ua: String): Embed {
        val res = Http.get("$ORIGIN/embed/$videoId?html5=1", mapOf("User-Agent" to ua, "Referer" to EMBED_REFERER))
        if (res.code !in 200..299) throw IOException("Trang nhúng: HTTP ${res.code}")
        val cfg = JSONObject()
        for (match in configStart.findAll(res.body)) {
            val value = try {
                JSONTokener(res.body.substring(match.range.last + 1)).nextValue() as? JSONObject
            } catch (_: org.json.JSONException) { null }
            if (value != null) for (key in value.keys()) cfg.put(key, value.opt(key))
        }
        val visitor = (cfg.opt("VISITOR_DATA") as? String)
            ?: (cfg.dig("INNERTUBE_CONTEXT", "client", "visitorData") as? String)
        val flags = cfg.dig("WEB_PLAYER_CONTEXT_CONFIGS", "WEB_PLAYER_CONTEXT_CONFIG_ID_EMBEDDED_PLAYER", "encryptedHostFlags") as? String
        if (visitor.isNullOrBlank() || flags.isNullOrBlank()) throw IOException("Trang nhúng thiếu cấu hình phát")
        val js = cfg.opt("PLAYER_JS_URL") as? String
        return Embed(visitor, flags, PlayerJs.idFrom(js ?: res.body))
    }
}
