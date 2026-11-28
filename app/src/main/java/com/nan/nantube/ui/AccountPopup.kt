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
import com.composables.icons.lucide.User
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


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountPopup(onDismiss: () -> Unit, onAdd: () -> Unit) {
    val context = LocalContext.current
    // Làm mới tên Google / email / số người đăng ký của các tài khoản đã đăng nhập.
    LaunchedEffect(Unit) { com.nan.nantube.data.InnerTube.refreshProfiles() }

    val current = AccountStore.selected
    val others = AccountStore.accounts.filter { it.id != current?.id }

    var menu by remember { mutableStateOf<Account?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }

    Box(Modifier.fillMaxSize().background(Color(0x99000000)).plainClickable(onDismiss)) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp)
                .fillMaxWidth(0.62f)
                .fillMaxHeight(0.9f)
                .background(NanColors.Popup)
                .plainClickable { }
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tài khoản", fontSize = 18.sp, modifier = Modifier.weight(1f))
                Icon(
                    Lucide.Plus,
                    null,
                    Modifier.offset(x = 8.dp).size(40.dp).clip(CircleShape).clickable(onClick = onAdd).padding(8.dp)
                )
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (current != null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { }, onLongClick = { menu = current })
                    ) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                            Text(current.googleName ?: current.name, fontSize = 14.sp)
                            (current.email ?: current.handle)?.let {
                                Text(it, fontSize = 12.sp, color = NanColors.TextSecondary)
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = NanColors.Divider)
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Avatar(current.name.take(1).uppercase(), 44.dp, photoUrl = current.photo)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                NameWithTag(current)
                                current.handle?.let { Text(it, fontSize = 12.sp, color = NanColors.TextSecondary) }
                                current.subs?.let { Text(it, fontSize = 12.sp, color = NanColors.TextSecondary) }
                                Text(
                                    "Chỉnh sửa kênh",
                                    color = NanColors.Blue,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                            Icon(Lucide.Check, null, Modifier.size(20.dp), tint = NanColors.Blue)
                        }
                    }
                } else {
                    // Đang dùng tài khoản khách.
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                        Text("Tài khoản khách", fontSize = 14.sp)
                        Text("Chưa đăng nhập", fontSize = 12.sp, color = NanColors.TextSecondary)
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = NanColors.Divider)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GuestAvatar(36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Khách", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Icon(Lucide.Check, null, Modifier.size(20.dp), tint = NanColors.Blue)
                    }
                }

                if (current != null || others.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().height(6.dp).background(NanColors.Divider))
                    Text(
                        "Tài khoản khác",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
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
                                    onLongClick = { menu = acc }
                                )
                        ) {
                            Text(
                                acc.email ?: acc.handle ?: if (acc.kind == LoginKind.CODE) "Đăng nhập bằng mã" else "Google",
                                fontSize = 14.sp,
                                color = NanColors.TextSoft,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 2.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(acc.name.take(1).uppercase(), 36.dp, photoUrl = acc.photo)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    NameWithTag(acc)
                                    Text(
                                        acc.subs ?: acc.handle ?: "",
                                        fontSize = 12.sp,
                                        color = NanColors.TextSecondary
                                    )
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

                // Tài khoản khách (mặc định, chưa đăng nhập): luôn nằm cuối danh sách.
                if (current != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                AccountStore.useGuest()
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GuestAvatar(36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Tài khoản khách", fontSize = 14.sp)
                            Text("Chưa đăng nhập", fontSize = 12.sp, color = NanColors.TextSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            HorizontalDivider(color = NanColors.Divider)
            val accountHint = current?.email
            PopupAction("Quản lý Tài khoản Google của bạn") { openGoogleAccount(context, accountHint) }
            PopupAction("Bật chế độ ẩn danh") { }
            PopupAction("Quản lý tài khoản") { openDeviceAccounts(context) }
            Text(
                "Tìm hiểu thêm về các tùy chọn tài khoản",
                fontSize = 14.sp,
                color = NanColors.Blue,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openInBrowser(context, HELP_URL) }
                    .padding(horizontal = 20.dp, vertical = 9.dp)
            )
        }

        menu?.let { target ->
            AccountMenu(
                account = target,
                isCurrent = target.id == current?.id,
                onDismiss = { menu = null },
                onSignIn = {
                    menu = null
                    AccountStore.select(target.id)
                    onDismiss()
                },
                onSignOut = {
                    menu = null
                    AccountStore.remove(target.id)
                }
            )
        }
    }
}

/** Tên kênh kèm nhãn code / web, để phân biệt hai tài khoản cùng Gmail. */
@Composable
private fun NameWithTag(account: Account) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(account.name, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(6.dp))
        Text(
            account.tag,
            fontSize = 11.sp,
            color = NanColors.TextSoft,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(NanColors.Surface)
                .padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun PopupAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 14.sp,
        color = NanColors.TextSoft,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 9.dp)
    )
}

/** Avatar tròn xám với icon người, dùng cho tài khoản khách. */
@Composable
private fun GuestAvatar(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(0xFF3F3F3F)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Lucide.User, null, Modifier.size(size * 0.55f), tint = NanColors.TextSoft)
    }
}

/** Tay nắm kéo thả: ba gạch ngang. */
@Composable
private fun DragHandle(modifier: Modifier) {
    Box(modifier.size(40.dp), contentAlignment = Alignment.Center) {
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
    account: Account,
    isCurrent: Boolean,
    onDismiss: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    val title = (account.email ?: account.handle ?: account.name) + " · " + account.tag
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
