package com.nan.nantube.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class VideoQualityMode { AUTO, HIGH, SAVER }

enum class AudioQualityMode { NORMAL, HIGH }

data class PlayQuality(val video: VideoQualityMode = VideoQualityMode.AUTO, val audio: AudioQualityMode = AudioQualityMode.NORMAL)

object QualityPrefs {
    private const val FEATURE = "quality_settings"

    var mobile by mutableStateOf(VideoQualityMode.AUTO)
        private set
    var wifi by mutableStateOf(VideoQualityMode.AUTO)
        private set
    var audio by mutableStateOf(AudioQualityMode.NORMAL)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var account: Account? = null
    private var ownerId: String? = null

    suspend fun bind(account: Account?) {
        val id = account?.id.orEmpty()
        if (ownerId == id) return
        val json = withContext(Dispatchers.IO) { NanStorage.readJson(FEATURE, account) as? JSONObject }
        this.account = account
        ownerId = id
        mobile = enumOf(json?.optString("mobile"), VideoQualityMode.AUTO)
        wifi = enumOf(json?.optString("wifi"), VideoQualityMode.AUTO)
        audio = enumOf(json?.optString("audio"), AudioQualityMode.NORMAL)
    }

    suspend fun current(context: Context, account: Account?): PlayQuality {
        bind(account)
        return PlayQuality(if (Connectivity.onWifi(context)) wifi else mobile, audio)
    }

    fun updateMobile(mode: VideoQualityMode) {
        mobile = mode
        save()
    }

    fun updateWifi(mode: VideoQualityMode) {
        wifi = mode
        save()
    }

    fun updateAudio(mode: AudioQualityMode) {
        audio = mode
        save()
    }

    private inline fun <reified T : Enum<T>> enumOf(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private fun save() {
        val owner = account
        val json = JSONObject().put("mobile", mobile.name).put("wifi", wifi.name).put("audio", audio.name)
        scope.launch(Dispatchers.IO) { NanStorage.writeJson(FEATURE, owner, json) }
    }
}
