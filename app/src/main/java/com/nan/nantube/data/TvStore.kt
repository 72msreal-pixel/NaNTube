package com.nan.nantube.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** TV đã ghép bằng mã: tên hiển thị + token Lounge để lần sau phát luôn, không phải nhập lại mã. */
data class PairedTv(val name: String, val token: String)

/** Nhớ TV đang kết nối (nút "Kết nối TV" ở thanh trên và mục "Phát trên thiết bị khác"). */
object TvStore {
    private const val PREFS = "nantube_tv"
    private var appContext: Context? = null

    var paired by mutableStateOf<PairedTv?>(null)
        private set

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val p = appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = p.getString("name", null)
        val token = p.getString("token", null)
        if (!name.isNullOrBlank() && !token.isNullOrBlank()) paired = PairedTv(name, token)
    }

    fun save(tv: PairedTv) {
        paired = tv
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString("name", tv.name)?.putString("token", tv.token)?.apply()
    }

    fun clear() {
        paired = null
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.clear()?.apply()
    }
}
