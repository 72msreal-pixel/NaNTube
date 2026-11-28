package com.nan.nantube.data

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class HttpResult(val code: Int, val body: String) {
    fun requireJson(): JSONObject {
        if (code !in 200..299) {
            val reason = try {
                JSONObject(body).dig("error", "message") as? String
            } catch (_: Exception) {
                null
            }
            throw Exception("YouTube trả lỗi HTTP $code" + (reason?.let { ": $it" } ?: ""))
        }
        return try {
            JSONObject(body)
        } catch (_: Exception) {
            throw Exception("Phản hồi không đọc được")
        }
    }
}

/** Mạng tối giản bằng HttpURLConnection, không thêm thư viện. Luôn chạy trên Dispatchers.IO. */
object Http {
    const val DEFAULT_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    suspend fun post(
        url: String,
        body: String,
        contentType: String,
        headers: Map<String, String> = emptyMap()
    ): HttpResult = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", contentType)
            conn.setRequestProperty("User-Agent", DEFAULT_UA)
            conn.setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { s ->
                val out = ByteArrayOutputStream()
                s.copyTo(out)
                out.toString("UTF-8")
            } ?: ""
            HttpResult(code, text)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 8_000): HttpResult = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 8_000
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", DEFAULT_UA)
            conn.setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            HttpResult(code, text)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun postForm(url: String, fields: Map<String, String>): HttpResult =
        post(
            url,
            fields.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" },
            "application/x-www-form-urlencoded"
        )

    suspend fun postJson(url: String, json: JSONObject, headers: Map<String, String> = emptyMap()): HttpResult =
        post(url, json.toString(), "application/json", headers)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

/** Đi sâu vào JSON theo đường dẫn (String = key, Int = chỉ số mảng). Sai đường thì trả null. */
fun Any?.dig(vararg path: Any): Any? {
    var cur: Any? = this
    for (p in path) {
        cur = when {
            p is String && cur is JSONObject -> cur.opt(p)
            p is Int && cur is JSONArray -> cur.opt(p)
            else -> return null
        }
        if (cur == null || cur == JSONObject.NULL) return null
    }
    return cur
}

/** Lấy chữ từ các dạng của InnerTube: simpleText, runs[], hoặc content (view model). */
fun ytText(node: Any?): String? {
    return when (node) {
        null -> null
        is String -> node.ifBlank { null }
        is JSONObject -> {
            node.optString("simpleText").ifBlank { null }
                ?: node.optJSONArray("runs")?.let { runs ->
                    buildString { for (i in 0 until runs.length()) append(runs.optJSONObject(i)?.optString("text").orEmpty()) }
                        .ifBlank { null }
                }
                ?: node.optString("content").ifBlank { null }
        }
        else -> null
    }
}

/** Tìm đệ quy object con đầu tiên nằm dưới key [name]. */
fun findKey(node: Any?, name: String): JSONObject? {
    when (node) {
        is JSONObject -> {
            node.optJSONObject(name)?.let { return it }
            val it = node.keys()
            while (it.hasNext()) findKey(node.opt(it.next()), name)?.let { r -> return r }
        }
        is JSONArray -> for (i in 0 until node.length()) findKey(node.opt(i), name)?.let { return it }
    }
    return null
}

/** Gom đệ quy mọi object nằm dưới key [name]. */
fun findAllKeys(node: Any?, name: String, out: MutableList<JSONObject> = mutableListOf()): MutableList<JSONObject> {
    when (node) {
        is JSONObject -> {
            val it = node.keys()
            while (it.hasNext()) {
                val k = it.next()
                val v = node.opt(k)
                if (k == name && v is JSONObject) out.add(v) else findAllKeys(v, name, out)
            }
        }
        is JSONArray -> for (i in 0 until node.length()) findAllKeys(node.opt(i), name, out)
    }
    return out
}


/** Link thật từ endpoint của YouTube: bóc tham số q của trang /redirect, còn lại giữ nguyên. */
fun realLinkUrl(raw: String?): String? {
    val u = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (u.contains("youtube.com/redirect")) {
        Regex("[?&]q=([^&]+)").find(u)?.groupValues?.get(1)?.let {
            return try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
        }
    }
    return u
}

private fun isCut(t: String) = t.endsWith("…") || t.endsWith("...")

/**
 * Như [ytText] nhưng link bị YouTube rút gọn kiểu "https://s.shopee.vn/7V…" được thay bằng link đầy đủ lấy từ endpoint của đoạn đó.
 * Hỗ trợ cả dạng `runs` (mỗi đoạn có navigationEndpoint) lẫn dạng `content` + `commandRuns` (attributedDescription).
 */
fun ytTextFullLinks(node: Any?): String? {
    if (node !is JSONObject) return ytText(node)
    val runs = node.optJSONArray("runs")
    if (runs != null) {
        val out = buildString {
            for (i in 0 until runs.length()) {
                val r = runs.optJSONObject(i) ?: continue
                val text = r.optString("text")
                val url = if (isCut(text.trim())) realLinkUrl(findKey(r.optJSONObject("navigationEndpoint"), "urlEndpoint")?.optString("url")) else null
                append(url ?: text)
            }
        }
        return out.ifBlank { null }
    }
    val content = node.optString("content").ifBlank { null } ?: return ytText(node)
    val cmds = node.optJSONArray("commandRuns") ?: return content
    val sb = StringBuilder(content)
    val list = (0 until cmds.length()).mapNotNull { cmds.optJSONObject(it) }.sortedByDescending { it.optInt("startIndex") }
    for (c in list) {
        val start = c.optInt("startIndex", -1)
        val len = c.optInt("length", 0)
        if (start < 0 || len <= 0 || start + len > sb.length) continue
        if (!isCut(sb.substring(start, start + len).trim())) continue
        val url = realLinkUrl(findKey(c, "urlEndpoint")?.optString("url")) ?: continue
        sb.replace(start, start + len, url)
    }
    return sb.toString().ifBlank { null }
}
