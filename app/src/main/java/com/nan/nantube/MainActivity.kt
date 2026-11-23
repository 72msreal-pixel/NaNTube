package com.nan.nantube

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.nan.nantube.data.AccountStore
import com.nan.nantube.data.ChannelAvatars
import com.nan.nantube.data.ShortDetector
import com.nan.nantube.data.ShortsSeen
import com.nan.nantube.data.TvStore
import com.nan.nantube.ui.AppRoot
import com.nan.nantube.ui.NaNTubeTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AccountStore.init(this)
        ChannelAvatars.init(this)
        ShortsSeen.init(this)
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
            NaNTubeTheme { AppRoot() }
        }
    }
}
