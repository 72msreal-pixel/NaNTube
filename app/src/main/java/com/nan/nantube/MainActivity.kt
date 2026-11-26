package com.nan.nantube

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.nan.nantube.data.AccountStore
import com.nan.nantube.data.ChannelAvatars
import com.nan.nantube.data.NanStorage
import com.nan.nantube.data.PipPrefs
import com.nan.nantube.data.SavedComments
import com.nan.nantube.data.MusicDetector
import com.nan.nantube.data.ShortDetector
import com.nan.nantube.data.ShortsSeen
import com.nan.nantube.data.TvStore
import com.nan.nantube.ui.AppRoot
import com.nan.nantube.ui.NaNTubeTheme
import com.nan.nantube.ui.BackgroundPlay
import com.nan.nantube.ui.PipState
import com.nan.nantube.ui.ShortsBackground
import com.nan.nantube.ui.ShortsMini
import com.nan.nantube.ui.SplashIntro
import com.nan.nantube.ui.StorageAccessPrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private var accessTick by mutableIntStateOf(0)
    private var hadAccess = false

    // --- Picture-in-Picture + phát nền cho Short (làm theo MobilePlaybackActivity của meetube; logic ở ShortsBackground) ---
    private val pipScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PIP_PREV -> ShortsBackground.prev()
                ACTION_PIP_NEXT -> ShortsBackground.next()
                ACTION_PIP_TOGGLE -> ShortsBackground.toggle()
            }
        }
    }

    // Auto-enter PiP (Android 12+) chỉ được bật khi app đang ở trước mặt (như mIsResumed của meetube): không thì video bắt đầu phát lúc app ẩn
    // sẽ làm hệ thống đẩy thẳng vào PiP.
    private var resumedNow by mutableStateOf(false)
    private var stopped = true

    // Vừa ra khỏi chế độ PiP mà chưa có onResume: là cửa sổ PiP bị đóng (X), không phải bấm mở rộng (mPipDismissPending của meetube).
    private var pipLeftPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NanStorage.init(this)
        AccountStore.init(this)
        ChannelAvatars.init(this)
        ShortsSeen.init(this)
        SavedComments.init(this)
        ShortDetector.init(this)
        MusicDetector.init(this)
        TvStore.init(this)
        PipPrefs.init(this)
        ShortsBackground.attach(this)
        NewUploadsWorker.schedule(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        PipState.supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && PipState.supported) setupPip()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            NaNTubeTheme {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                Box(Modifier.fillMaxSize()) {
                    // AppRoot dựng ngay bên dưới nên dữ liệu tải trong lúc intro chạy.
                    AppRoot()
                    StorageAccessPrompt(accessTick, onGrant = ::requestStorageAccess)
                    if (showSplash) SplashIntro(onFinished = { showSplash = false })
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupPip() {
        ContextCompat.registerReceiver(
            this,
            pipReceiver,
            IntentFilter().apply {
                addAction(ACTION_PIP_PREV)
                addAction(ACTION_PIP_NEXT)
                addAction(ACTION_PIP_TOGGLE)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        addOnPictureInPictureModeChangedListener { info ->
            val inPip = info.isInPictureInPictureMode
            PipState.inPip = inPip
            if (inPip) {
                pipLeftPending = false
            } else if (stopped) {
                // Cửa sổ PiP bị đóng khi app đã onStop: dừng hẳn.
                ShortsBackground.stopAll()
            } else {
                // Có thể là mở rộng (onResume tới ngay) hoặc đóng (onStop tới, không có onResume): onStop quyết định.
                pipLeftPending = true
            }
        }
        // Nút Phát/Tạm dừng và tỉ lệ cửa sổ luôn khớp Short đang hiện; Android 12+ còn tự vào PiP khi vuốt về Home (auto-enter).
        pipScope.launch {
            snapshotFlow { listOf(PipState.armed, PipState.playing, PipState.vertical, PipPrefs.autoPip, resumedNow) }.collect { updatePipParams() }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pipAction(action: String, request: Int, icon: Int, label: String): RemoteAction {
        val intent = PendingIntent.getBroadcast(
            this, request, Intent(action).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return RemoteAction(Icon.createWithResource(this, icon), label, label, intent)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(if (PipState.vertical) Rational(9, 16) else Rational(16, 9))
            .setActions(
                listOf(
                    pipAction(ACTION_PIP_PREV, 1, R.drawable.ic_pip_prev, "Short trước"),
                    if (PipState.playing) pipAction(ACTION_PIP_TOGGLE, 2, R.drawable.ic_pip_pause, "Tạm dừng")
                    else pipAction(ACTION_PIP_TOGGLE, 2, R.drawable.ic_pip_play, "Phát"),
                    pipAction(ACTION_PIP_NEXT, 3, R.drawable.ic_pip_next, "Short tiếp")
                )
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Chỉ tự vào PiP khi đang phát + cài đặt bật + app đang ở trước mặt.
            builder.setAutoEnterEnabled(PipState.armed && PipPrefs.autoPip && resumedNow)
        }
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updatePipParams() {
        try {
            setPictureInPictureParams(buildPipParams())
        } catch (_: Exception) {
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun enterPip() {
        if (!PipState.supported || isInPictureInPictureMode || isFinishing) return
        try {
            enterPictureInPictureMode(buildPipParams())
        } catch (_: Exception) {
            // Máy báo hỗ trợ nhưng từ chối (hạn chế của hãng): ở nguyên toàn màn hình, onStop sẽ chuyển sang phát nền chỉ âm thanh.
        }
    }

    /** Rời app (Home / cử chỉ vuốt) khi Short đang phát: bật dịch vụ nền ngay lúc app còn hiện, rồi vào PiP như YouTube (nếu cài đặt bật). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        ShortsBackground.keepAlive()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && PipPrefs.autoPip && PipState.armed) enterPip()
    }

    override fun onDestroy() {
        PipState.inPip = false
        pipScope.cancel()
        if (PipState.supported) runCatching { unregisterReceiver(pipReceiver) }
        // Thoát app bằng Back: Short không được phát mồ côi (như meetube đóng trình phát). Xoay màn hình / đổi cấu hình thì không dừng.
        if (isFinishing) ShortsBackground.stopAll()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        stopped = false
        ShortsBackground.onAppShown()
    }

    override fun onStop() {
        stopped = true
        when {
            isFinishing || pipLeftPending -> {
                // Thoát app, hoặc cửa sổ PiP vừa bị đóng: dừng hẳn.
                pipLeftPending = false
                ShortsBackground.stopAll()
            }
            // PiP còn hiện thì video đang phát ở đó, onStop không tới. Tới đây mà không PiP là app ẩn thật: tắt màn hình, sang app khác,
            // PiP bị tắt / bị từ chối. Short đang phát chuyển thành phiên nền chỉ phát âm thanh (làm TRƯỚC super.onStop()).
            !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) -> ShortsBackground.onAppHidden()
        }
        super.onStop()
    }

    override fun onPause() {
        resumedNow = false
        // App sắp mất tiêu điểm (cả khi tắt màn hình): bật dịch vụ nền khi app còn hiện, trước khi tới onStop.
        if (!isFinishing) ShortsBackground.keepAlive()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        resumedNow = true
        pipLeftPending = false
        ShortsBackground.onResumed()
        BackgroundPlay.askOnce(this)
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

    private companion object {
        const val ACTION_PIP_PREV = "com.nan.nantube.action.PIP_PREV"
        const val ACTION_PIP_NEXT = "com.nan.nantube.action.PIP_NEXT"
        const val ACTION_PIP_TOGGLE = "com.nan.nantube.action.PIP_TOGGLE"
    }
}
