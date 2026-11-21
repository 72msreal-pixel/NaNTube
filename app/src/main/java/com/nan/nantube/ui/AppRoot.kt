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
import com.composables.icons.lucide.Clapperboard
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.nan.nantube.data.AccountStore
import com.nan.nantube.data.VideoRepository

private enum class Tab(val label: String) {
    Main("Main"), Shorts("Short"), Search("Search"), Info("Info");

    val icon: ImageVector
        get() = when (this) {
            Main -> LucideHouse
            Shorts -> Lucide.Clapperboard
            Search -> Lucide.Search
            Info -> Lucide.CircleUser
        }
}

private enum class AddStep { None, Chooser, Code, Web }

@Composable
fun AppRoot() {
    var tab by remember { mutableStateOf(Tab.Main) }
    var showSettings by remember { mutableStateOf(false) }
    var showAccounts by remember { mutableStateOf(false) }
    var addStep by remember { mutableStateOf(AddStep.None) }
    var query by remember { mutableStateOf("") }

    val account = AccountStore.selected
    val mainFeed = remember { FeedState { acc, _ -> VideoRepository.home(acc) } }
    val shortsFeed = remember { FeedState { acc, _ -> VideoRepository.shorts(acc) } }
    val searchFeed = remember { FeedState { acc, q -> VideoRepository.search(q, acc) } }

    // Back: đóng lớp trên cùng trước, rồi mới về tab Main.
    BackHandler(enabled = tab != Tab.Main) { tab = Tab.Main }
    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = showAccounts) { showAccounts = false }

    Surface(Modifier.fillMaxSize(), color = NanColors.Background, contentColor = Color.White) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when {
                        tab == Tab.Info && showSettings -> SettingsScreen(onBack = { showSettings = false })
                        tab == Tab.Info -> InfoScreen(
                            account = account,
                            onAccountClick = { showAccounts = true },
                            onSettingsClick = { showSettings = true },
                            onSearchClick = { tab = Tab.Search }
                        )
                        tab == Tab.Main -> MainFeedScreen(mainFeed, account)
                        tab == Tab.Shorts -> ShortsFeedScreen(shortsFeed, account)
                        else -> SearchScreen(searchFeed, account, query) { query = it }
                    }
                }
                BottomBar(selected = tab, onSelect = {
                    tab = it
                    if (it == Tab.Info) showSettings = false
                })
            }
            if (showAccounts) {
                AccountPopup(onDismiss = { showAccounts = false }, onAdd = { addStep = AddStep.Chooser })
            }
            when (addStep) {
                AddStep.None -> Unit
                AddStep.Chooser -> AddAccountChooser(
                    onDismiss = { addStep = AddStep.None },
                    onManual = { addStep = AddStep.Web },
                    onCode = { addStep = AddStep.Code }
                )
                AddStep.Code -> CodeSignInScreen(onSuccess = { addStep = AddStep.None }, onClose = { addStep = AddStep.None })
                AddStep.Web -> WebSignInScreen(onSuccess = { addStep = AddStep.None }, onClose = { addStep = AddStep.None })
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
                    Icon(t.icon, null, Modifier.size(26.dp), tint = tint)
                    Spacer(Modifier.height(2.dp))
                    Text(t.label, fontSize = 11.sp, color = tint)
                }
            }
        }
    }
}
