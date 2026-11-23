package com.nan.nantube.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import androidx.javascriptengine.JavaScriptSandbox
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Player công khai, không cookie; JS chạy trong tiến trình sandbox, không có WebView/bridge của app. */
internal object PlayerJs {
    private const val ORIGIN = "https://www.youtube.com"
    private const val TTL = 30 * 60 * 1000L
    private const val MAX_JS_BYTES = 12 * 1024 * 1024
    private val idPattern = Regex("/s/player/([a-fA-F0-9]{8})/")
    private val stsPattern = Regex("(?:signatureTimestamp|\\bsts)[\"']?\\s*:\\s*([0-9]{5})\\b")
    private val playerLock = Mutex()
    private val solverLock = Mutex()
    @Volatile private var cached: Player? = null
    @Volatile private var bundle: String? = null

    internal class Player(val id: String, val code: String, val sts: Int, val loadedAt: Long)

    fun supported(): Boolean = Build.VERSION.SDK_INT >= 26 && Api26.supported()

    /** signatureTimestamp của player đã tải (còn hạn) hoặc null; không gọi mạng, cho các nguồn không cần giải mã. */
    fun cachedSts(): Int? = cached?.takeIf { android.os.SystemClock.elapsedRealtime() - it.loadedAt < TTL }?.sts

    suspend fun get(preferredId: String? = null): Player = playerLock.withLock {
        val now = android.os.SystemClock.elapsedRealtime()
        cached?.takeIf { now - it.loadedAt < TTL && (preferredId == null || it.id == preferredId) }
            ?.let { return@withLock it }
        val playerId = preferredId ?: run {
            val api = Http.get("$ORIGIN/iframe_api")
            if (api.code !in 200..299) throw IOException("iframe_api: HTTP ${api.code}")
            idFrom(api.body) ?: throw IOException("Không tìm thấy player JS")
        }
        require(Regex("[a-fA-F0-9]{8}").matches(playerId))
        val response = Http.get("$ORIGIN/s/player/$playerId/player_ias.vflset/en_US/base.js")
        if (response.code !in 200..299 || response.body.length > MAX_JS_BYTES) {
            throw IOException("Không đọc được player JS")
        }
        val sts = stsPattern.find(response.body)?.groupValues?.get(1)?.toIntOrNull()
            ?: throw IOException("Player JS không có signatureTimestamp")
        Player(playerId, response.body, sts, now).also { cached = it }
    }

    fun idFrom(text: String): String? = idPattern.find(text.replace("\\/", "/"))?.groupValues?.get(1)

    internal fun mediaUri(raw: String?): Uri? {
        if (raw.isNullOrBlank()) return null
        val uri = Uri.parse(raw)
        val host = uri.host ?: return null
        return uri.takeIf {
            it.scheme == "https" && it.userInfo == null &&
                (host == "googlevideo.com" || host.endsWith(".googlevideo.com"))
        }
    }

    private class Candidate(val format: JSONObject, val uri: Uri, val sig: String?, val sp: String?, val n: String?)

    /** Chuẩn hoá thành format.url. Giữ nguyên query đã mã hoá, trừ đúng tham số cần cập nhật. */
    suspend fun normalize(context: Context, player: Player, data: JSONObject) {
        val candidates = buildList {
            for (name in listOf("formats", "adaptiveFormats")) {
                val formats = data.optJSONArray(name) ?: continue
                for (index in 0 until formats.length()) {
                    val format = formats.optJSONObject(index) ?: continue
                    val direct = mediaUri(format.opt("url") as? String)
                    // Uri đang đọc query giả lập, không truy cập URL này.
                    val cipher = sequenceOf("signatureCipher", "cipher")
                        .mapNotNull { format.opt(it) as? String }.firstOrNull { it.isNotBlank() }
                        ?.let { Uri.parse("https://localhost/?$it") }
                    val uri = direct ?: mediaUri(cipher?.getQueryParameter("url")) ?: continue
                    val sig = if (direct == null) cipher?.getQueryParameter("s")?.takeIf { it.isNotEmpty() } else null
                    val sp = sig?.let { cipher?.getQueryParameter("sp")?.ifBlank { null } ?: "signature" }
                    if (sp != null && (!Regex("[A-Za-z0-9_-]{1,40}").matches(sp) || sp == "n")) continue
                    val n = uri.getQueryParameter("n")?.takeIf { it.isNotEmpty() }
                    add(Candidate(format, uri, sig, sp, n))
                    if (n != null) format.remove("url") // Không đưa URL có n chưa giải xuống ExoPlayer.
                }
            }
        }
        val sigs = candidates.mapNotNull { it.sig }.distinct()
        val ns = candidates.mapNotNull { it.n }.distinct()
        val solved = if (sigs.isEmpty() && ns.isEmpty()) emptyMap() else solverLock.withLock {
            try {
                withTimeoutOrNull(12_000) { solve(context, player, sigs, ns) } ?: emptyMap()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Các format URL thẳng không cần giải vẫn dùng được.
                emptyMap()
            }
        }
        val bySig = solved["sig"].orEmpty()
        val byN = solved["n"].orEmpty()
        for (entry in candidates) {
            val replacements = mutableMapOf<String, String>()
            entry.sig?.let { bySig[it]?.let { value -> replacements[entry.sp!!] = value } }
            entry.n?.let { byN[it]?.let { value -> replacements["n"] = value } }
            if ((entry.sig != null && entry.sp !in replacements) || (entry.n != null && "n" !in replacements)) continue
            entry.format.put("url", replaceQuery(entry.uri, replacements).toString())
        }
    }

    internal fun replaceQuery(uri: Uri, replacements: Map<String, String>): Uri {
        if (replacements.isEmpty()) return uri
        val query = uri.encodedQuery.orEmpty().split('&').filter {
            it.isNotEmpty() && Uri.decode(it.substringBefore('=')) !in replacements
        } + replacements.map { "${Uri.encode(it.key)}=${Uri.encode(it.value)}" }
        return uri.buildUpon().encodedQuery(query.joinToString("&")).build()
    }

    private suspend fun solve(context: Context, player: Player, sigs: List<String>, ns: List<String>): Map<String, Map<String, String>> =
        withContext(Dispatchers.IO) {
            val libs = bundle ?: context.assets.open("player-solver.js").bufferedReader().use { it.readText() }
                .also { bundle = it }
            val taskGroups = listOf("sig" to sigs, "n" to ns).filter { it.second.isNotEmpty() }
            val request = JSONArray().apply {
                for ((type, inputs) in taskGroups) put(JSONObject().put("type", type).put("challenges", JSONArray(inputs)))
            }
            val temp = File.createTempFile("player-solver-", ".js", context.cacheDir)
            try {
                temp.bufferedWriter().use { out ->
                    out.write(libs)
                    out.write("\n;JSON.stringify(jsc({type:'player',player:")
                    out.write(JSONObject.quote(player.code).replace("\u2028", "\\u2028").replace("\u2029", "\\u2029"))
                    out.write(",requests:")
                    out.write(request.toString())
                    out.write("}));\n")
                }
                val response = if (Build.VERSION.SDK_INT >= 26) Api26.evaluate(context, temp) else null
                val answers = response?.let { JSONObject(it).optJSONArray("responses") }
                taskGroups.mapIndexed { index, (type, inputs) ->
                    val data = answers?.optJSONObject(index)?.optJSONObject("data")
                    type to inputs.mapNotNull { input ->
                        val output = data?.opt(input) as? String
                        output?.takeIf { it.isNotBlank() && (type != "n" || (it != input && !it.startsWith("enhanced_except_"))) }
                            ?.let { input to it }
                    }.toMap()
                }.toMap()
            } finally {
                temp.delete()
            }
        }

    @RequiresApi(26)
    private object Api26 {
        fun supported(): Boolean = JavaScriptSandbox.isSupported()

        suspend fun evaluate(context: Context, file: File): String? = runInterruptible(Dispatchers.IO) {
            val connect = JavaScriptSandbox.createConnectedInstanceAsync(context)
            try {
                connect.get(4, TimeUnit.SECONDS).use { sandbox ->
                    if (!sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_EVALUATE_FROM_FD)) {
                        return@runInterruptible null
                    }
                    sandbox.createIsolate().use { isolate ->
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                            isolate.evaluateJavaScriptAsync(fd).get(7, TimeUnit.SECONDS)
                        }
                    }
                }
            } finally {
                if (!connect.isDone) connect.cancel(true)
            }
        }
    }
}
