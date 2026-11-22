package com.nan.nantube.data

import android.net.Uri
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

object TvPlayback {
    private const val ORIGIN = "https://www.youtube.com"
    private const val LOUNGE = "$ORIGIN/api/lounge"
    private const val FORM = "application/x-www-form-urlencoded"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Safari/537.36"
    private val headers = mapOf("User-Agent" to USER_AGENT, "Origin" to ORIGIN, "Referer" to "$ORIGIN/")

    private class Session(val sid: String, val gsessionId: String, val lastEvent: Int)

    suspend fun play(code: String, videoId: String) {
        val token = pair(code).token
        playWithToken(token, videoId)
    }

    /** Phát trên TV đã kết nối trước đó (nút Kết nối TV ở trang chủ), khỏi nhập lại mã. */
    suspend fun playOn(tv: PairedTv, videoId: String) = playWithToken(tv.token, videoId)

    private suspend fun playWithToken(token: String, videoId: String) {
        val rid = (10_000..89_999).random()
        val session = connect(token, rid)
        sendVideo(session, token, rid + 1, videoId)
    }

    /** Ghép với TV bằng mã 12 số (YouTube TV > Cài đặt > Liên kết bằng mã TV), trả tên TV và token để nhớ. */
    suspend fun pair(code: String): PairedTv {
        val digits = code.filter { it.isDigit() }
        require(digits.length == 12) { "Mã TV phải có 12 chữ số" }
        val res = Http.post("$LOUNGE/pairing/get_screen", form("pairing_code" to digits), FORM, headers)
        check(res.code in 200..299) { "Không ghép được TV. Kiểm tra mã và thử lại." }
        val screen = JSONObject(res.body).optJSONObject("screen")
        val token = screen?.optString("loungeToken")?.ifBlank { null } ?: error("Mã TV không hợp lệ hoặc đã hết hạn")
        val name = screen.optString("name").ifBlank { null } ?: "TV"
        return PairedTv(name, token)
    }

    private suspend fun connect(token: String, rid: Int): Session {
        val body = form(
            "app" to "web", "mdx-version" to "3", "name" to "NaNTube", "id" to UUID.randomUUID().toString(),
            "device" to "REMOTE_CONTROL", "capabilities" to "que,dsdtr,atsp", "method" to "setPlaylist",
            "magnaKey" to "cloudPairedDevice", "ui" to "", "theme" to "cl", "loungeIdToken" to token
        )
        val res = Http.post(bindUrl("RID" to rid.toString(), "auth_failure_option" to "send_error"), body, FORM, headers)
        check(res.code in 200..299) { "Không kết nối được với TV" }
        return parseSession(res.body)
    }

    private suspend fun sendVideo(session: Session, token: String, rid: Int, videoId: String) {
        val url = bindUrl(
            "RID" to rid.toString(), "SID" to session.sid, "gsessionid" to session.gsessionId,
            "AID" to session.lastEvent.toString(), "loungeIdToken" to token
        )
        val body = form(
            "count" to "1", "ofs" to "0", "req0__sc" to "setPlaylist", "req0_videoId" to videoId,
            "req0_videoIds" to videoId, "req0_currentIndex" to "0", "req0_currentTime" to "0",
            "req0_listId" to "", "req0_audioOnly" to "false", "req0_params" to "", "req0_playerParams" to ""
        )
        val res = Http.post(url, body, FORM, headers)
        check(res.code in 200..299) { "TV không nhận được lệnh phát" }
    }

    private fun parseSession(body: String): Session {
        var rest = body.trimStart()
        var sid: String? = null
        var gsid: String? = null
        var lastEvent = 0
        while (rest.isNotEmpty()) {
            val newline = rest.indexOf('\n')
            val length = if (newline < 0) null else rest.substring(0, newline).trim().toIntOrNull()
            if (length == null) break
            val end = minOf(newline + 1 + length, rest.length)
            val frame = JSONArray(rest.substring(newline + 1, end))
            for (i in 0 until frame.length()) {
                val message = frame.optJSONArray(i) ?: continue
                lastEvent = maxOf(lastEvent, message.optInt(0))
                val event = message.optJSONArray(1) ?: continue
                when (event.optString(0)) {
                    "c" -> sid = event.optString(1).ifBlank { null }
                    "S" -> gsid = event.optString(1).ifBlank { null }
                }
            }
            rest = rest.substring(end).trimStart()
        }
        if (sid == null || gsid == null) error("TV không trả về phiên kết nối")
        return Session(sid, gsid, lastEvent)
    }

    private fun form(vararg fields: Pair<String, String>): String =
        fields.joinToString("&") { "${Uri.encode(it.first)}=${Uri.encode(it.second)}" }

    private fun bindUrl(vararg params: Pair<String, String>): String =
        Uri.parse("$LOUNGE/bc/bind").buildUpon().apply {
            appendQueryParameter("VER", "8")
            appendQueryParameter("CVER", "1")
            appendQueryParameter("zx", UUID.randomUUID().toString().replace("-", "").take(12))
            params.forEach { appendQueryParameter(it.first, it.second) }
        }.build().toString()
}
