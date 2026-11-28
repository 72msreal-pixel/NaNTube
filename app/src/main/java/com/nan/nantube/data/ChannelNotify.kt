package com.nan.nantube.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Chuông thông báo của từng kênh, lưu hoàn toàn trên máy (giống chuông từng kênh của meetube): chỉ Bật / Tắt, mặc định Bật.
 * Không gọi YouTube. Lưu các kênh đang TẮT (mã kênh và tên kênh viết thường, vì feed của client TV có khi không kèm mã kênh).
 */
object ChannelNotify {
    private const val KEY = "notify_off"

    /** Tăng mỗi lần đổi để Compose vẽ lại các nơi đang đọc [isOn]. */
    private var version by mutableIntStateOf(0)

    private fun prefs() = AccountStore.libraryPrefs()

    private fun off(account: Account): MutableSet<String> =
        prefs()?.getStringSet("$KEY:${account.id}", emptySet())?.toMutableSet() ?: mutableSetOf()

    private fun idKey(channelId: String?) = channelId?.takeIf { it.isNotBlank() }?.let { "id:$it" }
    private fun nameKey(name: String?) = name?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { "n:$it" }

    fun isOn(account: Account?, channelId: String?, name: String?): Boolean {
        @Suppress("UNUSED_VARIABLE") val v = version // đăng ký theo dõi trạng thái Compose
        account ?: return true
        val off = off(account)
        return idKey(channelId)?.let { it !in off } != false && nameKey(name)?.let { it !in off } != false
    }

    fun set(account: Account?, channelId: String?, name: String?, on: Boolean) {
        account ?: return
        val off = off(account)
        listOfNotNull(idKey(channelId), nameKey(name)).forEach { if (on) off.remove(it) else off.add(it) }
        prefs()?.edit()?.putStringSet("$KEY:${account.id}", off)?.apply()
        version++
    }
}
