package com.nan.nantube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import com.nan.nantube.data.ShortsStream
import androidx.compose.ui.platform.LocalContext
import com.nan.nantube.data.WatchHistory
import com.nan.nantube.data.VideoPage
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.CircleUser
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Search
import com.nan.nantube.data.AccountStore
import com.nan.nantube.data.VideoItem
import com.nan.nantube.data.VideoRepository

private enum class Tab(val label: String) {
    Main("Main"), Shorts("Short"), Music("Music"), Search("Search"), Info("Info");

    fun icon(selected: Boolean): ImageVector = when (this) {
        Main -> if (selected) HouseFilled else LucideHouse
        Shorts -> if (selected) ShortsFilled else ShortsOutline
        Music -> Lucide.Music
        Search -> Lucide.Search
        Info -> Lucide.CircleUser
    }
}

private enum class AddStep { None, Chooser, Code, Web }

@Composable
fun AppRoot() {
    var tab by remember { mutableStateOf(Tab.Main) }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var openList by remember { mutableStateOf<OpenList?>(null) }
    var saveVideo by remember { mutableStateOf<VideoItem?>(null) }
    var showAccounts by remember { mutableStateOf(false) }
    var addStep by remember { mutableStateOf(AddStep.None) }
    var query by remember { mutableStateOf("") }
    var musicQuery by remember { mutableStateOf("") }
    var webHint by remember { mutableStateOf<String?>(null) }


    val account = AccountStore.selected
    val context = LocalContext.current
    val watchHistory = remember(context) { WatchHistory(context) }
    var watch by remember(account?.id) { mutableStateOf<WatchRequest?>(null) }
    val mainFeed = remember { FeedState { acc, _ -> VideoRepository.home(acc) } }
    val shortsStream = remember(account?.id) { ShortsStream() }
    val shortsFeed = remember(shortsStream) { FeedState { acc, _ -> shortsStream.start(acc) } }
    val searchFeed = remember { FeedState { acc, q -> VideoRepository.search(q, acc) } }
    val musicState = remember { musicFeed() }
    // Lịch sử dùng chung: hàng "Video đã xem" ở Info và trang Video đã xem đầy đủ cùng một danh sách.
    val historyFeed = remember(watchHistory, account?.id) { HistoryFeed(forHistory = true) { acc ->
        val remote = try { VideoRepository.historyPage(acc) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (watchHistory.page(acc).items.isEmpty()) throw e
            VideoPage(emptyList(), null)
        }
        watchHistory.page(acc, remote = remote)
    } }
    val libraryCovers = remember { LibraryCovers() }
    LaunchedEffect(watch == null, historyFeed) {
        if (watch == null) historyFeed.overlayLocal(account, watchHistory)
    }

    // Back: đóng lớp trên cùng trước, rồi mới về tab Main.
    BackHandler(enabled = tab != Tab.Main) { tab = Tab.Main }
    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = showHistory && tab == Tab.Info) { showHistory = false }
    BackHandler(enabled = openList != null && tab == Tab.Info) { openList = null }
    BackHandler(enabled = showAccounts) { showAccounts = false }

    CompositionLocalProvider(LocalWatchOpen provides { video, queue -> watch = WatchRequest(video, queue, fromShorts = tab == Tab.Shorts) },
        LocalWatchHistory provides watchHistory) {
    Surface(Modifier.fillMaxSize(), color = NanColors.Background, contentColor = Color.White) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when {
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
                            onSearchClick = { tab = Tab.Search },
                            onSignIn = { showAccounts = true; addStep = AddStep.Chooser }
                        )
                        tab == Tab.Main -> MainFeedScreen(mainFeed, account) { saveVideo = it }
                        tab == Tab.Shorts -> ShortsFeedScreen(shortsFeed, account) { saveVideo = it }
                        tab == Tab.Music -> MusicScreen(musicState, account, musicQuery, { saveVideo = it }) { musicQuery = it }
                        else -> SearchScreen(searchFeed, account, query, { saveVideo = it }) { query = it }
                    }
                }
                BottomBar(selected = tab, onSelect = {
                    tab = it
                    showHistory = false
                    openList = null
                    if (it == Tab.Info) showSettings = false
                })
            }
            watch?.let { request ->
                WatchScreen(request, account, onBack = { watch = null }, onSave = { saveVideo = it },
                    loadMoreShorts = if (request.fromShorts) ({ shortsStream.more(account).also(shortsFeed::append) }) else null)
            }
            saveVideo?.let { video ->
                SaveToPlaylistSheet(video, account, onDismiss = { saveVideo = null })
            }
            if (showAccounts) {
                AccountPopup(
                    onDismiss = { showAccounts = false },
                    onAdd = { addStep = AddStep.Chooser }
                )
            }
            when (addStep) {
                AddStep.None -> Unit
                AddStep.Chooser -> AddAccountChooser(
                    onDismiss = { addStep = AddStep.None },
                    onManual = { webHint = null; addStep = AddStep.Web },
                    onCode = { addStep = AddStep.Code }
                )
                AddStep.Code -> CodeSignInScreen(onSuccess = { addStep = AddStep.None }, onClose = { addStep = AddStep.None })
                AddStep.Web -> WebSignInScreen(
                    loginHint = webHint,
                    onSuccess = { addStep = AddStep.None; webHint = null },
                    onClose = { addStep = AddStep.None; webHint = null }
                )
            }
        }
    }
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(NanColors.Background)) {
        HorizontalDivider(color = NanColors.Surface)
        Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Tab.entries.forEach { t ->
                val tint = if (t == selected) Color.White else NanColors.TextSecondary
                Column(
                    Modifier.weight(1f).clickable { onSelect(t) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(t.icon(t == selected), null, Modifier.size(26.dp), tint = tint)
                    Spacer(Modifier.height(2.dp))
                    Text(t.label, fontSize = 11.sp, color = tint)
                }
            }
        }
    }
}
