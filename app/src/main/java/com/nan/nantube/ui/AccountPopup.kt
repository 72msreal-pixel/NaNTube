package com.nan.nantube.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.nan.nantube.data.Account
import com.nan.nantube.data.AccountStore

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import com.nan.nantube.data.LoginKind

private const val HELP_URL = "https://support.google.com/youtube/answer/3802431?&co=GENIE.Platform%3DAndroid#android_signout"

/** Đối tượng đang mở menu giữ-lâu: tài khoản đã đăng nhập hoặc email trên máy chưa đăng nhập. */
private sealed interface MenuTarget {
    data class Signed(val account: Account) : MenuTarget
    data class Unsigned(val email: String) : MenuTarget
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountPopup(onDismiss: () -> Unit, onAdd: () -> Unit, onSignInPending: (String) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AccountStore.refreshDeviceAccounts(context) }

    val current = AccountStore.selected
    val others = AccountStore.accounts.filter { it.id != current?.id }
    val unsigned = AccountStore.pending.toList()

    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }

    Box(Modifier.fillMaxSize().background(Color(0x99000000)).plainClickable(onDismiss)) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp)
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.9f)
                .background(NanColors.Popup)
                .plainClickable { }
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tài khoản", fontSize = 22.sp, modifier = Modifier.weight(1f))
                Icon(
                    Lucide.Plus,
                    null,
                    Modifier.offset(x = 8.dp).size(44.dp).clip(CircleShape).clickable(onClick = onAdd).padding(8.dp)
                )
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (current != null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { }, onLongClick = { menu = MenuTarget.Signed(current) })
                    ) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Text(current.name, fontSize = 18.sp)
                            Text(current.secondary ?: "", fontSize = 18.sp, color = NanColors.TextSecondary)
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = NanColors.Divider)
                        Row(
                            Modifier.fillMaxWidth().padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(current.name.take(1).uppercase(), 48.dp, photoUrl = current.photo)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(current.name, fontSize = 18.sp)
                                if (current.email != null) {
                                    current.handle?.let { Text(it, fontSize = 14.sp, color = NanColors.TextSecondary) }
                                }
                            }
                            Icon(Lucide.Check, null, Modifier.size(24.dp), tint = NanColors.Blue)
                        }
                    }
                    Text(
                        "Chỉnh sửa kênh",
                        color = NanColors.Blue,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )
                } else {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text("Chưa đăng nhập", fontSize = 18.sp)
                        Text(
                            "Bấm dấu + để thêm tài khoản",
                            fontSize = 14.sp,
                            color = NanColors.TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                if (others.isNotEmpty() || unsigned.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().height(6.dp).background(NanColors.Divider))
                    Text(
                        "Tài khoản khác",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(20.dp)
                    )
                }

                // Tài khoản đã đăng nhập: bấm để chuyển, giữ để mở menu, kéo tay nắm bên phải để sắp xếp.
                others.forEach { acc ->
                    key(acc.id) {
                        val dragging = draggingId == acc.id
                        val latestOthers by rememberUpdatedState(others)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .onSizeChanged { heights[acc.id] = it.height }
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                                .background(if (dragging) NanColors.Surface else Color.Transparent)
                                .combinedClickable(
                                    onClick = {
                                        AccountStore.select(acc.id)
                                        onDismiss()
                                    },
                                    onLongClick = { menu = MenuTarget.Signed(acc) }
                                )
                        ) {
                            Text(
                                acc.secondary ?: if (acc.kind == LoginKind.CODE) "Đăng nhập bằng mã" else "Google",
                                fontSize = 18.sp,
                                color = NanColors.TextSoft,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(acc.name.take(1).uppercase(), 44.dp, photoUrl = acc.photo)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(acc.name, fontSize = 18.sp)
                                    if (acc.email != null) {
                                        acc.handle?.let { Text(it, fontSize = 14.sp, color = NanColors.TextSecondary) }
                                    }
                                }
                                DragHandle(
                                    Modifier.pointerInput(acc.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggingId = acc.id
                                                dragOffset = 0f
                                            },
                                            onDragEnd = { draggingId = null; dragOffset = 0f },
                                            onDragCancel = { draggingId = null; dragOffset = 0f },
                                            onDrag = { change, drag: Offset ->
                                                change.consume()
                                                dragOffset += drag.y
                                                val list = latestOthers
                                                val idx = list.indexOfFirst { it.id == acc.id }
                                                if (dragOffset > 0 && idx in 0 until list.lastIndex) {
                                                    val next = list[idx + 1]
                                                    val h = (heights[next.id] ?: 0).toFloat()
                                                    if (h > 0 && dragOffset > h / 2) {
                                                        AccountStore.move(acc.id, next.id)
                                                        dragOffset -= h
                                                    }
                                                } else if (dragOffset < 0 && idx > 0) {
                                                    val prev = list[idx - 1]
                                                    val h = (heights[prev.id] ?: 0).toFloat()
                                                    if (h > 0 && -dragOffset > h / 2) {
                                                        AccountStore.move(acc.id, prev.id)
                                                        dragOffset += h
                                                    }
                                                }
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // Tài khoản trên máy chưa đăng nhập: luôn nằm cuối.
                unsigned.forEach { email ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onSignInPending(email) },
                                onLongClick = { menu = MenuTarget.Unsigned(email) }
                            )
                    ) {
                        Text(
                            email,
                            fontSize = 18.sp,
                            color = NanColors.TextSoft,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(email.take(1).uppercase(), 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Chưa đăng nhập", fontSize = 14.sp, color = NanColors.TextSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            HorizontalDivider(color = NanColors.Divider)
            val accountHint = current?.email ?: unsigned.firstOrNull()
            PopupAction("Quản lý Tài khoản Google của bạn") { openGoogleAccount(context, accountHint) }
            PopupAction("Bật chế độ ẩn danh") { }
            PopupAction("Quản lý tài khoản") { openDeviceAccounts(context) }
            Text(
                "Tìm hiểu thêm về các tùy chọn tài khoản",
                fontSize = 16.sp,
                color = NanColors.Blue,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openInBrowser(context, HELP_URL) }
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            )
        }

        menu?.let { target ->
            AccountMenu(
                target = target,
                isCurrent = target is MenuTarget.Signed && target.account.id == current?.id,
                onDismiss = { menu = null },
                onSignIn = {
                    menu = null
                    when (target) {
                        is MenuTarget.Signed -> {
                            AccountStore.select(target.account.id)
                            onDismiss()
                        }
                        is MenuTarget.Unsigned -> onSignInPending(target.email)
                    }
                },
                onSignOut = {
                    menu = null
                    when (target) {
                        is MenuTarget.Signed -> AccountStore.remove(target.account.id)
                        is MenuTarget.Unsigned -> AccountStore.removePending(target.email)
                    }
                }
            )
        }
    }
}

@Composable
private fun PopupAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 18.sp,
        color = NanColors.TextSoft,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)
    )
}

/** Tay nắm kéo thả: ba gạch ngang. */
@Composable
private fun DragHandle(modifier: Modifier) {
    Box(modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(width = 20.dp, height = 14.dp)) {
            val stroke = 2.dp.toPx()
            for (i in 0..2) {
                val y = stroke / 2 + i * (size.height - stroke) / 2
                drawLine(
                    Color(0xFFAAAAAA),
                    Offset(0f, y),
                    Offset(size.width, y),
                    strokeWidth = stroke
                )
            }
        }
    }
}

@Composable
private fun AccountMenu(
    target: MenuTarget,
    isCurrent: Boolean,
    onDismiss: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    val title = when (target) {
        is MenuTarget.Signed -> target.account.secondary ?: target.account.name
        is MenuTarget.Unsigned -> target.email
    }
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000)).plainClickable(onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.62f)
                .clip(RoundedCornerShape(12.dp))
                .background(NanColors.Surface)
                .plainClickable { }
                .padding(vertical = 8.dp)
        ) {
            Text(
                title,
                fontSize = 14.sp,
                color = NanColors.TextSecondary,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
            if (!isCurrent) PopupAction("Đăng nhập", onSignIn)
            PopupAction("Đăng xuất", onSignOut)
        }
    }
}

private fun openGoogleAccount(context: Context, email: String?) {
    try {
        val i = Intent("com.google.android.gms.accountsettings.ACCOUNT_PREFERENCES_SETTINGS")
            .setPackage("com.google.android.gms")
        if (email != null) i.putExtra("extra.accountName", email)
        context.startActivity(i)
    } catch (_: Exception) {
        openInBrowser(context, "https://myaccount.google.com/")
    }
}

private fun openDeviceAccounts(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS))
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
        }
    }
}
