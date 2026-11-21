package com.nan.nantube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.CircleUser
import com.composables.icons.lucide.Lucide

private enum class Screen { Info, Settings }

@Composable
fun AppRoot() {
    var screen by remember { mutableStateOf(Screen.Info) }
    var showAccounts by remember { mutableStateOf(false) }

    BackHandler(enabled = showAccounts) { showAccounts = false }
    BackHandler(enabled = !showAccounts && screen == Screen.Settings) { screen = Screen.Info }

    Surface(Modifier.fillMaxSize(), color = NanColors.Background, contentColor = Color.White) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (screen) {
                        Screen.Info -> InfoScreen(
                            onAccountClick = { showAccounts = true },
                            onSettingsClick = { screen = Screen.Settings }
                        )
                        Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Info })
                    }
                }
                BottomBar(onInfoClick = { screen = Screen.Info })
            }
            if (showAccounts) {
                AccountPopup(onDismiss = { showAccounts = false })
            }
        }
    }
}

@Composable
private fun BottomBar(onInfoClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(NanColors.Background)) {
        HorizontalDivider(color = NanColors.Surface)
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Column(
                Modifier.fillMaxWidth().clickable(onClick = onInfoClick).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Lucide.CircleUser, null, Modifier.size(26.dp))
                Spacer(Modifier.height(2.dp))
                Text("Info", fontSize = 11.sp)
            }
        }
    }
}
