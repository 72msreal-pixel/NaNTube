package com.nan.nantube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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

private data class SampleAccount(val email: String, val channel: String, val subs: String)

private val otherAccounts = listOf(
    SampleAccount("taikhoan2@example.com", "Kênh mẫu 2", "12 người đăng ký"),
    SampleAccount("taikhoan3@example.com", "Kênh mẫu 3", "Không có người đăng ký")
)

private val bottomActions = listOf(
    "Thêm tài khoản trẻ em",
    "Quản lý Tài khoản Google của bạn",
    "Bật chế độ ẩn danh",
    "Quản lý tài khoản"
)

@Composable
fun AccountPopup(onDismiss: () -> Unit) {
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
                Icon(Lucide.Plus, null, Modifier.size(28.dp))
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("NaN User", fontSize = 18.sp)
                    Text("nan@example.com", fontSize = 18.sp, color = NanColors.TextSecondary)
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = NanColors.Divider)
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar("N", 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("NaN User", fontSize = 18.sp)
                        Text("@nantube", fontSize = 14.sp, color = NanColors.TextSecondary)
                        Text("0 người đăng ký", fontSize = 14.sp, color = NanColors.TextSecondary)
                    }
                    Icon(Lucide.Check, null, Modifier.size(24.dp), tint = NanColors.Blue)
                }
                Text(
                    "Chỉnh sửa kênh",
                    color = NanColors.Blue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )
                Box(Modifier.fillMaxWidth().height(6.dp).background(NanColors.Divider))
                Text(
                    "Tài khoản khác",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(20.dp)
                )
                otherAccounts.forEach { acc ->
                    Text(
                        acc.email,
                        fontSize = 18.sp,
                        color = NanColors.TextSoft,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(acc.channel.take(1), 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(acc.channel, fontSize = 18.sp)
                            Text(acc.subs, fontSize = 14.sp, color = NanColors.TextSecondary)
                        }
                    }
                }
            }

            HorizontalDivider(color = NanColors.Divider)
            bottomActions.forEach { label ->
                Text(
                    label,
                    fontSize = 18.sp,
                    color = NanColors.TextSoft,
                    modifier = Modifier.fillMaxWidth().clickable { }.padding(horizontal = 20.dp, vertical = 14.dp)
                )
            }
            Text(
                "Tìm hiểu thêm về các tùy chọn tài khoản",
                fontSize = 16.sp,
                color = NanColors.Blue,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)
            )
        }
    }
}
