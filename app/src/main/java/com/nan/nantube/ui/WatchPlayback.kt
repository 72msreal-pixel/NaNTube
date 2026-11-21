@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nan.nantube.ui

import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.nan.nantube.data.Account
import com.nan.nantube.data.InnerTube
import com.nan.nantube.data.VideoItem
import com.nan.nantube.data.WatchHistory
import com.nan.nantube.data.WatchRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

internal val LocalWatchOpen = staticCompositionLocalOf<(VideoItem, List<VideoItem>) -> Unit> { { _, _ -> } }
internal val LocalWatchHistory = staticCompositionLocalOf<WatchHistory?> { null }

private tailrec fun Context.activity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.activity() else null
    else -> null
}

@Composable
internal fun WatchPlayback(
    video: VideoItem,
    account: Account?,
    modifier: Modifier,
    onEnded: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.activity()
    val history = LocalWatchHistory.current
    val ended by rememberUpdatedState(onEnded)
    var retry by remember { mutableIntStateOf(0) }
    val player = remember(video.id, video.offlineUri, account?.id, retry) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
            repeatMode = if (video.isShort) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
    }
    val factory = remember(player) {
        DefaultMediaSourceFactory(DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory().setUserAgent(InnerTube.MEDIA_UA)))
    }
    var ready by remember(player) { mutableStateOf(false) }
    var buffering by remember(player) { mutableStateOf(true) }
    var failure by remember(player) { mutableStateOf<String?>(null) }
    var playbackFailure by remember(player) { mutableStateOf<String?>(null) }
    val retryPosition = remember(player) { longArrayOf(0L) }
    val historyItem by rememberUpdatedState(video)

    DisposableEffect(player) {
        var started = false
        var resume = true
        var foreground = activity?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true
        fun save() {
            if (started && (player.duration > 0 || player.currentPosition > 0)) history?.record(account, historyItem, player.currentPosition, player.duration)
        }
        val window = activity?.window
        val hadScreenFlag = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0 && window != null
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) ready = true
                if (state == Player.STATE_ENDED) { save(); ended() }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    if (!started) { started = true; save() }
                    window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else if (!hadScreenFlag) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            override fun onPlayerError(error: PlaybackException) {
                save()
                if (started) retryPosition[0] = player.currentPosition
                playbackFailure = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Máy chủ từ chối luồng phát"
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Mất kết nối mạng"
                    else -> "Không phát được luồng này (${error.errorCodeName})"
                }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> { foreground = false; resume = player.playWhenReady; save(); player.pause() }
                Lifecycle.Event.ON_RESUME -> { foreground = true; if (resume) player.play() }
                else -> Unit
            }
        }
        player.addListener(listener)
        player.playWhenReady = foreground
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            save()
            activity?.lifecycle?.removeObserver(observer)
            player.removeListener(listener)
            player.release()
            if (!hadScreenFlag) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(player) {
        val seed = if (video.isShort) 0 else history?.position(account, video) ?: 0L
        var last = "Không tìm thấy luồng phát"
        for (client in 0..(if (video.offlineUri != null) 0 else 2)) {
            val candidates = try { WatchRepository.sources(video, account, client) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { last = e.message ?: last; continue }
            for (candidate in candidates) {
                playbackFailure = null
                ready = false
                buffering = true
                val primary = factory.createMediaSource(MediaItem.Builder().setUri(candidate.video).setMimeType(candidate.type).build())
                val source = if (candidate.audio != null) MergingMediaSource(primary, factory.createMediaSource(MediaItem.fromUri(candidate.audio))) else primary
                player.setMediaSource(source, maxOf(seed, retryPosition[0]))
                player.prepare()
                // A successful source stays selected; a later network error tries the next source.
                snapshotFlow { playbackFailure }.collectUntilFailure { last = it }
            }
        }
        failure = last
        buffering = false
    }
    LaunchedEffect(player, ready) {
        if (ready) while (true) {
            delay(5_000)
            if (player.isPlaying) history?.record(account, historyItem, player.currentPosition, player.duration)
        }
    }

    Box(modifier.background(Color.Black)) {
        if (video.musicTrack) {
            AsyncImage(video.artwork ?: video.coverThumbnail, null, Modifier.fillMaxSize().padding(24.dp), contentScale = ContentScale.Fit)
        }
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply {
                useController = true
                setControllerShowTimeoutMs(if (video.musicTrack) 0 else 2500)
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                if (video.musicTrack) {
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setKeepContentOnPlayerReset(true)
                    videoSurfaceView?.visibility = android.view.View.INVISIBLE
                }
            } },
            update = { it.player = player; if (video.musicTrack) it.showController() },
            modifier = Modifier.fillMaxSize()
        )
        if (buffering && failure == null) LinearProgressIndicator(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = Color.Red)
        failure?.let { reason ->
            Column(Modifier.align(Alignment.Center).background(Color(0xFF151515)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(reason, color = Color.White)
                TextButton(onClick = { retry++ }) { Text("Thử lại", color = Color.White) }
            }
        }
    }
}

private suspend fun kotlinx.coroutines.flow.Flow<String?>.collectUntilFailure(onFailure: (String) -> Unit) {
    val failure = first { it != null }
    onFailure(checkNotNull(failure))
}
