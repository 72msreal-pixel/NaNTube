package com.nan.nantube.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Công tắc kiểu YouTube (đo từ app thật): ray 25x14dp, núm 20dp nhô ra hai đầu ray, tổng rộng 40dp (tắt: xám, bật: xanh trên ray xanh đậm). */
@Composable
fun NanSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val t by animateFloatAsState(if (checked) 1f else 0f, tween(150), label = "nanSwitch")
    Box(
        modifier.size(40.dp, 24.dp).toggleable(
            value = checked,
            role = Role.Switch,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onValueChange = onCheckedChange
        ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier.align(Alignment.Center).size(25.dp, 14.dp).clip(RoundedCornerShape(7.dp))
                .background(lerp(Color(0xFF565656), Color(0xFF1D3B55), t))
        )
        Box(
            Modifier.offset(x = 20.dp * t).size(20.dp).clip(CircleShape)
                .background(lerp(Color(0xFFB9B9B9), NanColors.Blue, t))
        )
    }
}

/** Một dòng cài đặt có công tắc, bố cục như trang Chung của YouTube: tên + mô tả bên trái, công tắc bên phải. */
@Composable
fun NanSwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = NanColors.TextSecondary)
        }
        Spacer(Modifier.width(16.dp))
        NanSwitch(checked, onCheckedChange)
    }
}
