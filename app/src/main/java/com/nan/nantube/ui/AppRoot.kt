package com.nan.nantube.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.composables.icons.lucide.CircleUser
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Search
import com.nan.nantube.data.AccountStore
import kotlinx.coroutines.launch
import com.nan.nantube.data.VideoItem
import com.nan.nantube.data.VideoRepository

private enum class Tab(val label: String) {
    Main("Main"), Shorts("Short"), Music("Music"), Search("Search"), Info("Info");

    fun icon(selected: Boolean): ImageVector = when (this) {
        Main -> if (selected) HouseFilled else HouseOutline
        Shorts -> if (selected) ShortsFilled else ShortsOutline
        Music -> Lucide.Music
        Search -> Lucide.Search
        Info -> Lucide.CircleUser
    }
}

private enum class AddStep { None, Chooser, Code, Web }

/** Trạng thái luồng thêm tài khoản. */
private class AddFlow {
    var step by mutableStateOf(AddStep.None)
    var hint by mutableStateOf<String?>(null)
    var keepSelected = false

    fun web(hint: String? = null, keepSelected: Boolean = false) {
        this.hint = hint
        this.keepSelected = keepSelected
        step = AddStep.Web
    }

    fun close() {
        step = AddStep.None
        hint = null
        keepSelected = false
    }
}

/** Rời app lâu hơn mức này rồi quay lại thì tab Short lấy bộ video mới (như YouTube). */
private const val SHORTS_STALE_MS = 10 * 60 * 1000L

/** Chu kỳ tự tải lại lịch sử xem khi đang ở tab Short/Info. */
private const val HISTORY_POLL_MS = 1_000L

@Composable
fun AppRoot() {
    var tab by remember { mutableStateOf(Tab.Main) }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var openList by remember { mutableStateOf<OpenList?>(null) }
    var saveVideo by remember { mutableStateOf<VideoItem?>(null) }
    // Video vừa thu thành thẻ nổi: bảng "Lưu vào danh sách phát" đang mở (từ màn xem) phải đóng để không che thẻ.
    LaunchedEffect(WatchMini.pb) { if (WatchMini.pb != null) saveVideo = null }
    var showAccounts by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    val add = remember { AddFlow() }
    var homeChip by remember { mutableStateOf(0) }
    // Nơi khác (khung chat live của tài khoản mã) xin mở đăng nhập web: mở thẳng bước tự nhập Gmail, gợi ý email của tài khoản mã
    // và giữ nguyên tài khoản mã đang dùng sau khi đăng nhập xong.
    LaunchedEffect(WebSignInRequest.requested) {
        if (WebSignInRequest.requested) {
            add.web(WebSignInRequest.hint, keepSelected = true)
            WebSignInRequest.requested = false
        }
    }


    val account = AccountStore.selected
    // Công tắc popup / phát nền lưu riêng theo từng tài khoản.
    LaunchedEffect(account?.id) { com.nan.nantube.data.PipPrefs.bind(account) }
    val mainFeed = remember { homeFeed() }
    val homeShorts = remember { homeShortsFeed() }
    val shorts = remember { shortsFeed() }
    val shortsTab = remember { ShortsTabState() }
    // Đổi tài khoản thì làm mới màn tìm kiếm: ô tìm kiếm và kết quả của tài khoản trước không được giữ lại.
    val searchUi = remember(account?.id) { SearchUi() }
    val musicUi = remember { MusicUi() }
    // Lịch sử dùng chung: hàng "Video đã xem" ở Info và trang Video đã xem đầy đủ cùng một danh sách.
    val historyFeed = remember(account?.id) {
        HistoryFeed(forHistory = true, webAccount = account) { acc -> VideoRepository.historyPage(acc) }
    }
    val libraryCovers = remember { LibraryCovers() }

    // Main, Short, Nhạc, Info cùng tải song song ngay từ lúc intro chạy (MainFeedScreen nằm dưới intro nên tự tải khi dựng,
    // Short thì tab chưa mở nên tải ở đây, cùng khóa với ShortsFeedScreen nên mở tab Short sau đó không tải lại).
    // Nhưng intro CHỈ chờ Main: Short về kịp thì tốt, chưa về cũng không cản thanh load.
    LaunchedEffect(account?.id) { shorts.load(account) }
    // Nhạc và Info cũng tải song song, cũng không chặn intro. Cùng khóa với MusicScreen / InfoScreen nên mở tab là có sẵn;
    // Info tải nền (silent) để lỗi mạng không hiện gì, khách chưa đăng nhập thì không có lịch sử nên bỏ qua.
    LaunchedEffect(account?.id) { musicUi.home.load(account, musicUi.submitted) }
    LaunchedEffect(account?.id) {
        if (account == null) return@LaunchedEffect
        launch { libraryCovers.load(account) }
    }
    val mainReady = mainFeed.items.isNotEmpty() || mainFeed.error != null || mainFeed.failures > 0
    LaunchedEffect(mainReady) { if (mainReady) SplashGate.ready = true }

    // WEB nhận bản ghi ngay từ kho trên máy; tải FEhistory một lần khi vào Info để gộp với lịch sử YouTube.
    var foreground by remember { mutableStateOf(true) }
    val watching = account != null && foreground && (tab == Tab.Shorts || tab == Tab.Info)
    val infoVisible = foreground && tab == Tab.Info
    LaunchedEffect(historyFeed, watching, infoVisible) {
        if (account == null) return@LaunchedEffect
        if (account.kind == com.nan.nantube.data.LoginKind.WEB) {
            if (infoVisible || historyFeed.loadedKey == null) {
                historyFeed.refresh(account, silent = !infoVisible || historyFeed.loadedKey != null)
                historyFeed.classifyShorts()
            }
            return@LaunchedEffect
        }
        if (!watching) {
            if (historyFeed.loadedKey == null) historyFeed.refresh(account, silent = true)
            return@LaunchedEffect
        }
        while (true) {
            historyFeed.refresh(account, silent = historyFeed.loadedKey != null)
            historyFeed.classifyShorts()
            kotlinx.coroutines.delay(HISTORY_POLL_MS)
        }
    }

    // Thoát app hẳn rồi mở lại thì mọi thứ dựng mới nên tự có Short khác; chỉ ẩn app một lúc lâu cũng làm mới.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        var stoppedAt = 0L
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) foreground = false
            if (event == Lifecycle.Event.ON_START) foreground = true
            if (event == Lifecycle.Event.ON_STOP) {
                stoppedAt = SystemClock.elapsedRealtime()
            } else if (event == Lifecycle.Event.ON_START && stoppedAt != 0L &&
                SystemClock.elapsedRealtime() - stoppedAt > SHORTS_STALE_MS
            ) {
                shortsTab.refresh()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Đang xem Short (tab Short hoặc màn xem Short mở từ nơi khác) thì khoá dọc, không xoay màn hình; rời Short thì mở lại xoay tự do.
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    val lockPortrait = tab == Tab.Shorts || ShortsViewer.items != null || WatchViewer.video != null
    val watchFullscreen = WatchViewer.fullscreen && WatchViewer.video != null
    DisposableEffect(lockPortrait, watchFullscreen, activity) {
        activity?.requestedOrientation = when {
            // Xem video toàn màn hình: xoay ngang (theo cảm biến, được lật hai chiều ngang).
            watchFullscreen -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            lockPortrait -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose { activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    // Bấm Hình trong hình ở Short: đóng màn Short, app về tab Main, Short phát tiếp trong cửa sổ nhỏ nổi (ShortsMiniWindow ở cuối cây).
    LaunchedEffect(ShortsMini.startSerial) {
        if (ShortsMini.startSerial == 0) return@LaunchedEffect
        ShortsViewer.close()
        tab = Tab.Main
        showHistory = false
        showSettings = false
        openList = null
        PlaylistViewer.open = null
    }
    // Popup Short xem tới gần cuối danh sách thì nạp thêm từ feed Short (không phụ thuộc giao diện nên lúc app ẩn vẫn chạy).
    DisposableEffect(account?.id) {
        ShortsMini.loader = { shorts.loadMoreAndWait(account) }
        onDispose { ShortsMini.loader = null }
    }
    // Bấm Hình trong hình mà popup đang tắt: mở thẳng trang cài đặt để bật lại.
    LaunchedEffect(PipState.openExperiments) {
        if (!PipState.openExperiments) return@LaunchedEffect
        ShortsViewer.close()
        WatchViewer.close()
        tab = Tab.Info
        showHistory = false
        openList = null
        PlaylistViewer.open = null
        showSettings = true
    }
    // Bấm nút mở rộng ở cửa sổ nhỏ: vào thẳng tab Short, đúng video đang phát.
    LaunchedEffect(ShortsMini.expandSerial) {
        if (ShortsMini.expandSerial == 0) return@LaunchedEffect
        val list = ShortsMini.expandList
        val video = list.getOrNull(ShortsMini.expandIndex) ?: return@LaunchedEffect
        val at = shorts.items.indexOfFirst { it.id == video.id }
        if (at >= 0) {
            shortsTab.page = at
            shortsTab.pageVersion = shorts.version
            showHistory = false
            showSettings = false
            openList = null
            PlaylistViewer.open = null
            tab = Tab.Shorts
        } else {
            ShortsViewer.open(list, ShortsMini.expandIndex)
        }
    }
    // Mở lại Short toàn màn hình (tab Short hoặc màn xem Short) thì cửa sổ nhỏ tắt để hai bên không giành một trình phát.
    LaunchedEffect(tab, ShortsViewer.items) {
        if (tab == Tab.Shorts || ShortsViewer.items != null) {
            ShortsMini.close()
            WatchMini.close()
        }
    }

    // Back: đóng lớp trên cùng trước, rồi mới về tab Main.
    BackHandler(enabled = tab != Tab.Main) { tab = Tab.Main }
    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = showHistory && tab == Tab.Info) { showHistory = false }
    BackHandler(enabled = openList != null && tab == Tab.Info) { openList = null }
    BackHandler(enabled = showAccounts) { showAccounts = false }
    BackHandler(enabled = showNotifications) { showNotifications = false }
    // Đăng ký sau cùng nên được ưu tiên: Back đóng màn xem Short trước.
    BackHandler(enabled = PlaylistViewer.open != null) { PlaylistViewer.open = null }
    BackHandler(enabled = ShortsViewer.items != null) { ShortsViewer.close() }
    BackHandler(enabled = MusicPlayer.expanded) { MusicPlayer.expanded = false }

    Surface(Modifier.fillMaxSize(), color = NanColors.Background, contentColor = Color.White) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when {
                        PlaylistViewer.open != null -> PlaylistScreen(
                            list = PlaylistViewer.open!!,
                            account = account,
                            onSaveToPlaylist = { saveVideo = it },
                            onBack = { PlaylistViewer.open = null }
                        )
                        tab == Tab.Info && showSettings -> SettingsScreen(onBack = { showSettings = false })
                        tab == Tab.Info && openList != null -> PlaylistScreen(
                            list = openList!!,
                            account = account,
                            onSaveToPlaylist = { saveVideo = it },
                            onBack = { openList = null }
                        )
                        tab == Tab.Info && showHistory -> HistoryScreen(
                            base = historyFeed,
                            account = account,
                            onSaveToPlaylist = { saveVideo = it },
                            onBack = { showHistory = false }
                        )
                        tab == Tab.Info -> InfoScreen(
                            account = account,
                            history = historyFeed,
                            covers = libraryCovers,
                            onOpenHistory = { showHistory = true },
                            onOpenList = { openList = it },
                            onSaveToPlaylist = { saveVideo = it },
                            onAccountClick = { showAccounts = true },
                            onSettingsClick = { showSettings = true },
                            onSearchClick = { searchUi.startNew(); tab = Tab.Search },
                            onBellClick = { showNotifications = true },
                            onSignIn = { showAccounts = true; add.step = AddStep.Chooser }
                        )
                        tab == Tab.Main -> MainFeedScreen(
                            feed = mainFeed,
                            shortsFeed = homeShorts,
                            account = account,
                            chip = homeChip,
                            onChip = { homeChip = it },
                            onSearch = { searchUi.startNew(); tab = Tab.Search },
                            onNotifications = { showNotifications = true },
                            onSaveToPlaylist = { saveVideo = it }
                        )
                        tab == Tab.Shorts -> ShortsFeedScreen(
                            feed = shorts,
                            state = shortsTab,
                            account = account,
                            onSearch = { searchUi.startNew(); tab = Tab.Search },
                            onSaveToPlaylist = { saveVideo = it }
                        )
                        tab == Tab.Music -> MusicScreen(musicUi, account) { saveVideo = it }
                        else -> SearchScreen(
                            ui = searchUi,
                            account = account,
                            onBack = { tab = Tab.Main },
                            onSaveToPlaylist = { saveVideo = it }
                        )
                    }
                    // Thanh nhạc nhỏ nổi trên nội dung, sát thanh tab dưới, hiện ở mọi tab khi đang có bài. Không có nền
                    // nên nhìn xuyên được xuống nội dung phía sau.
                    if (MusicPlayer.current != null && !MusicPlayer.expanded) {
                        Box(Modifier.align(Alignment.BottomCenter)) { MiniMusicBar() }
                    }
                }
                // Mở bảng mô tả Short thì thanh tab dưới trượt xuống ẩn đi.
                AnimatedVisibility(visible = !(tab == Tab.Shorts && ShortsSheet.open) && !PipState.inPip) {
                    BottomBar(selected = tab, onSelect = {
                        tab = it
                        showHistory = false
                        openList = null
                        PlaylistViewer.open = null
                        if (it == Tab.Info) showSettings = false
                    })
                }
            }
            if (showNotifications) {
                NotificationsScreen(
                    account = account,
                    onBack = { showNotifications = false },
                    onSignIn = { showNotifications = false; showAccounts = true; add.step = AddStep.Chooser }
                )
            }
            if (ShortsViewer.items != null) {
                ShortsViewerOverlay(
                    account = account,
                    onSearch = { ShortsViewer.close(); searchUi.startNew(); tab = Tab.Search },
                    onSave = { saveVideo = it }
                )
            }
            // Màn xem video dài (kiểu YouTube): phủ trên nội dung, dưới trình phát nhạc / các bảng chọn.
            if (WatchViewer.video != null) WatchScreen(account = account, onSave = { saveVideo = it })
            MusicPlayerHost()
            if (TvScreen.open) TvConnectScreen()
            if (searchUi.voiceOpen) {
                VoiceSearchOverlay(
                    onResult = { searchUi.voiceResult = it; searchUi.voiceOpen = false },
                    onClose = { searchUi.voiceOpen = false }
                )
            }
            saveVideo?.let { video ->
                SaveToPlaylistSheet(video, account, onDismiss = { saveVideo = null })
            }
            DownloadSheet()
            PlaylistDownloadSheet()
            if (showAccounts) {
                AccountPopup(
                    onDismiss = { showAccounts = false },
                    onAdd = { add.step = AddStep.Chooser }
                )
            }
            when (add.step) {
                AddStep.None -> Unit
                AddStep.Chooser -> AddAccountChooser(
                    onDismiss = add::close,
                    onManual = { add.web() },
                    onCode = { add.step = AddStep.Code }
                )
                AddStep.Code -> CodeSignInScreen(onSuccess = add::close, onClose = add::close)
                AddStep.Web -> WebSignInScreen(
                    loginHint = add.hint,
                    keepSelected = add.keepSelected,
                    onSuccess = add::close,
                    onClose = add::close
                )
            }
            // Cửa sổ Short nhỏ nổi (hoặc cả cửa sổ PiP khi rời app): trên cùng mọi lớp.
            if (tab != Tab.Shorts && ShortsViewer.items == null) ShortsMiniWindow(account)
            // Thẻ nổi của video dài (bấm Hình trong hình ở màn xem): ẩn khi đang mở lại màn xem.
            if (tab != Tab.Shorts && ShortsViewer.items == null && WatchViewer.video == null) WatchMiniWindow(account)
        }
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    // Ở tab Short thanh đáy đen hẳn, không đường kẻ (như YouTube); các tab khác giữ nền thường.
    val immersive = selected == Tab.Shorts
    Column(Modifier.fillMaxWidth().background(if (immersive) Color.Black else NanColors.Background)) {
        if (!immersive) HorizontalDivider(color = NanColors.Surface)
        Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Tab.entries.forEach { t ->
                val tint = if (t == selected) Color.White else NanColors.TextSecondary
                Column(
                    Modifier.weight(1f).clickable { onSelect(t) }.padding(top = 8.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(t.icon(t == selected), null, Modifier.size(26.dp), tint = tint)
                    Spacer(Modifier.height(2.dp))
                    Text(t.label, fontSize = 11.5.sp, lineHeight = 14.sp, color = tint)
                }
            }
        }
    }
}
