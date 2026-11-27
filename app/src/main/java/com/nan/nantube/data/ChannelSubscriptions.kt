package com.nan.nantube.data

import android.util.Base64
import androidx.compose.runtime.mutableStateMapOf
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Đăng ký / hủy đăng ký kênh YouTube bằng tài khoản đang chọn. */
object ChannelSubscriptions {
    /** Trạng thái đã đổi trong phiên này, ưu tiên hơn trạng thái YouTube trả kèm kết quả tìm kiếm (có thể đã cũ). */
    private val changed = mutableStateMapOf<String, Boolean>()

    /**
     * Kênh tài khoản đang đăng ký, tải từ danh sách đăng ký của YouTube. Cần cho tài khoản đăng nhập bằng mã: kết quả của
     * client TV không kèm nút đăng ký nên không biết kênh nào đã đăng ký. Key là "idTàiKhoản|mãKênh".
     */
    private val fromList = mutableStateMapOf<String, Boolean>()
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private val loaded = ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val CHANNEL_ID = Regex("UC[\\w-]{22}")

    private fun key(account: Account?, channelId: String) = "${account?.id}|$channelId"

    fun isSubscribed(account: Account?, channel: SearchResult.Channel): Boolean {
        if (account?.kind == LoginKind.CODE) preload(account)
        return changed[key(account, channel.id)] ?: (fromList[key(account, channel.id)] == true || channel.subscribed == true)
    }

    /** Tải danh sách kênh đã đăng ký của tài khoản mã ở nền (một lần mỗi tài khoản; lỗi thì lần sau thử lại). */
    fun preload(account: Account?) {
        if (account == null || account.kind != LoginKind.CODE) return
        if (account.id in loaded || !loading.add(account.id)) return
        scope.launch {
            try {
                val ids = fetchSubscribedIds(account)
                if (ids.isNotEmpty()) {
                    ids.forEach { fromList[key(account, it)] = true }
                    loaded.add(account.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Lỗi mạng / bị từ chối: lần sau thử lại, thẻ kênh vẫn dùng trạng thái YouTube trả kèm.
            } finally {
                loading.remove(account.id)
            }
        }
    }

    /**
     * Đúng thứ tự của meetube (BrowseService2.getSubscribedChannels): client TV (browse FEsubscriptions) trước,
     * không có thì đọc `guide` của client WEB.
     */
    private suspend fun fetchSubscribedIds(account: Account): Set<String> {
        val tv = try {
            channelIdsFromTvTabs(InnerTube.call("browse", JSONObject().put("browseId", "FEsubscriptions"), account))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptySet()
        }
        if (tv.isNotEmpty()) return tv
        return try {
            channelIdsFromGuide(InnerTube.call("guide", JSONObject(), account, asWeb = true))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptySet()
        }
    }

    /**
     * Trang Kênh đăng ký của TV: mỗi kênh là một tab (tvSecondaryNavSectionRenderer.tabs). Tab chung "Tất cả" đứng đầu và
     * không có ảnh nên bỏ qua. Tab kênh browse FEsubscriptions nên mã kênh nằm trong params (base64 protobuf), như
     * `TabRenderer.getChannelId` của MediaServiceCore.
     */
    private fun channelIdsFromTvTabs(json: JSONObject): Set<String> {
        val tabs = findKey(json, "tvSecondaryNavSectionRenderer")?.optJSONArray("tabs") ?: return emptySet()
        val out = LinkedHashSet<String>()
        for (i in 0 until tabs.length()) {
            val wrapper = tabs.optJSONObject(i) ?: continue
            val tab = wrapper.optJSONObject("tabRenderer") ?: wrapper.optJSONObject("expandableTabRenderer") ?: continue
            if (i == 0 && !tab.has("thumbnail")) continue
            val endpoint = tab.dig("endpoint", "browseEndpoint") as? JSONObject
            val browseId = endpoint?.optString("browseId").orEmpty()
            val id = browseId.takeIf { CHANNEL_ID.matches(it) } ?: channelIdFromParams(endpoint?.optString("params").orEmpty())
            if (id != null) out.add(id)
        }
        return out
    }

    private fun channelIdFromParams(params: String): String? {
        if (params.isBlank()) return null
        val decoded = try {
            URLDecoder.decode(params, "UTF-8")
        } catch (_: Exception) {
            params
        }
        for (flags in intArrayOf(Base64.DEFAULT, Base64.URL_SAFE)) {
            try {
                val text = String(Base64.decode(decoded, flags), Charsets.ISO_8859_1)
                CHANNEL_ID.find(text)?.value?.let { return it }
            } catch (_: Exception) {
            }
        }
        return null
    }

    /** `guide` của WEB: mục đăng ký gồm các guideEntryRenderer đứng đầu và danh sách thu gọn; bỏ mục đặc biệt không có ảnh. */
    private fun channelIdsFromGuide(json: JSONObject): Set<String> {
        val root = findKey(json, "guideSubscriptionsSectionRenderer") ?: return emptySet()
        val items = root.optJSONArray("items") ?: return emptySet()
        val entries = mutableListOf<JSONObject>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            item.optJSONObject("guideEntryRenderer")?.let(entries::add)
            item.optJSONObject("guideCollapsibleEntryRenderer")?.optJSONArray("expandableItems")?.let { arr ->
                for (j in 0 until arr.length()) arr.optJSONObject(j)?.optJSONObject("guideEntryRenderer")?.let(entries::add)
            }
        }
        val out = LinkedHashSet<String>()
        for (e in entries) {
            if (!e.has("thumbnail")) continue
            val id = e.dig("navigationEndpoint", "browseEndpoint", "browseId") as? String
            if (id != null && CHANNEL_ID.matches(id)) out.add(id)
        }
        return out
    }

    /** Chạy hết dù thẻ kênh đã cuộn khỏi màn hình giữa chừng, để không bỏ lửng một thao tác ghi. */
    suspend fun set(account: Account, channel: SearchResult.Channel, subscribe: Boolean) = withContext(NonCancellable) {
        // Giống ActionsService của MediaServiceCore: đăng ký kèm params của nút, hủy đăng ký để params rỗng.
        val params = if (subscribe) channel.subscribeParams.orEmpty() else ""
        val body = JSONObject().put("channelIds", JSONArray().put(channel.id)).put("params", params)
        InnerTube.callAny(if (subscribe) "subscription/subscribe" else "subscription/unsubscribe", body, account)
        changed[key(account, channel.id)] = subscribe
        // Ghi vào danh sách đã tải để các màn khác (Short, trang xem) thấy ngay.
        fromList[key(account, channel.id)] = subscribe
    }
}
