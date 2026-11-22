package com.nan.nantube.data

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Đăng ký / hủy đăng ký kênh YouTube bằng tài khoản đang chọn. */
object ChannelSubscriptions {
    /** Trạng thái đã đổi trong phiên này, ưu tiên hơn trạng thái YouTube trả kèm kết quả tìm kiếm (có thể đã cũ). */
    private val changed = mutableStateMapOf<String, Boolean>()

    private fun key(account: Account?, channelId: String) = "${account?.id}|$channelId"

    fun isSubscribed(account: Account?, channel: SearchResult.Channel): Boolean =
        changed[key(account, channel.id)] ?: (channel.subscribed == true)

    /** Chạy hết dù thẻ kênh đã cuộn khỏi màn hình giữa chừng, để không bỏ lửng một thao tác ghi. */
    suspend fun set(account: Account, channel: SearchResult.Channel, subscribe: Boolean) = withContext(NonCancellable) {
        // Giống ActionsService của MediaServiceCore: đăng ký kèm params của nút, hủy đăng ký để params rỗng.
        val params = if (subscribe) channel.subscribeParams.orEmpty() else ""
        val body = JSONObject().put("channelIds", JSONArray().put(channel.id)).put("params", params)
        InnerTube.callAny(if (subscribe) "subscription/subscribe" else "subscription/unsubscribe", body, account)
        changed[key(account, channel.id)] = subscribe
    }
}
