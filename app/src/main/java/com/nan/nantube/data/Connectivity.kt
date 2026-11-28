package com.nan.nantube.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Tăng [tick] mỗi lần máy có mạng trở lại, để các chỗ đang chờ (Short lỗi tải) thử lại ngay thay vì đợi hết lượt chờ. */
object Connectivity {
    var tick by mutableIntStateOf(0)
        private set

    fun onWifi(context: Context): Boolean {
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private var started = false

    fun start(context: Context) {
        if (started) return
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
        try {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    tick++
                }
            }, Handler(Looper.getMainLooper()))
            started = true
        } catch (_: Exception) {
        }
    }
}
