package com.nan.nantube

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.nan.nantube.data.AccountStore
import com.nan.nantube.data.ChannelAvatars
import com.nan.nantube.data.NanStorage
import com.nan.nantube.data.SavedComments
import com.nan.nantube.data.ShortDetector
import com.nan.nantube.data.ShortsSeen
import com.nan.nantube.data.TvStore
import com.nan.nantube.ui.AppRoot
import com.nan.nantube.ui.NaNTubeTheme
import com.nan.nantube.ui.StorageAccessPrompt

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private var accessTick by mutableIntStateOf(0)
    private var hadAccess = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NanStorage.init(this)
        AccountStore.init(this)
        ChannelAvatars.init(this)
        ShortsSeen.init(this)
        SavedComments.init(this)
        ShortDetector.init(this)
        TvStore.init(this)
        NewUploadsWorker.schedule(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            NaNTubeTheme {
                AppRoot()
                StorageAccessPrompt(accessTick, onGrant = ::requestStorageAccess)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val now = NanStorage.hasAccess()
        if (now && !hadAccess) NanStorage.onAccessChanged()
        hadAccess = now
        accessTick++
    }

    /** Android 11+: mở màn "Cho phép quản lý tất cả tệp" của app; cũ hơn: hộp thoại quyền ghi bộ nhớ. */
    private fun requestStorageAccess() {
        val intent = NanStorage.accessSettingsIntent(this)
        if (intent == null) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        }
    }
}
