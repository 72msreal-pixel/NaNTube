package com.nan.nantube.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

// Phần dùng chung của ShortChat và VideoChat: cỡ chữ, tô @tên / link, sao chép bình luận.

val ChatMetaSize = 11.5.sp
val ChatMetaLine = 14.sp
val ChatBodyLine = 21.sp

/** Tô xanh @tên được nhắc tới và đường link, như YouTube. */
fun mentioned(text: String) = buildAnnotatedString {
    var last = 0
    Regex("@[^\\s@]+|https?://\\S+").findAll(text).forEach { m ->
        append(text.substring(last, m.range.first))
        if (m.value.startsWith("@")) {
            withStyle(SpanStyle(color = NanColors.Blue)) { append(m.value) }
            last = m.range.last + 1
        } else {
            // Link bấm được (mở bằng trình duyệt); dấu câu dính ở cuối link không tính vào link.
            val url = m.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '"', '\'')
            withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = NanColors.Blue)))) { append(url) }
            last = m.range.first + url.length
        }
    }
    append(text.substring(last))
}

/**
 * Có vẽ dấu tích xác minh không. Bình luận của chủ video (MrBeast...) thường không kèm cờ isVerified nên chỉ hiện viên sáng:
 * khi đó tra thẳng kênh (InnerTube browse) xem kênh có tích không, nhớ theo mã kênh.
 */
@Composable
fun rememberVerified(c: com.nan.nantube.data.CommentItem, byCreator: Boolean, ownerId: String?): Boolean =
    rememberVerified(c.id, c.verified, c.authorChannelId ?: ownerId.takeIf { byCreator })

/** Như trên nhưng không phụ thuộc kiểu bình luận: dùng chung cho chat trực tiếp (tin nhắn live cũng có mã kênh). */
@Composable
fun rememberVerified(key: String, flagged: Boolean, channelId: String?): Boolean {
    var verified by remember(key, flagged, channelId) {
        mutableStateOf(flagged || com.nan.nantube.data.ChannelVerified.cached(channelId) == true)
    }
    LaunchedEffect(key, flagged, channelId) {
        // Mọi người viết có mã kênh đều được tra (nhớ theo mã kênh, tối đa 3 lượt song song nên cuộn nhanh không dồn mạng).
        if (!verified && channelId != null) {
            // Chờ dòng đứng yên một chút mới tra: cuộn nhanh qua thì LaunchedEffect bị hủy, không tốn mạng cho dòng chưa ai đọc.
            // Kênh đã có trong bộ nhớ thì ra ngay, không phải chờ.
            if (com.nan.nantube.data.ChannelVerified.cached(channelId) == null) kotlinx.coroutines.delay(700)
            verified = com.nan.nantube.data.ChannelVerified.check(channelId)
        }
    }
    return verified
}

/**
 * Tên người viết + dấu tích, dùng chung cho VideoChat, ShortChat và LiveChat để ba nơi hiện giống hệt nhau:
 * chủ video nằm trong viên sáng (chữ đen thường, tick đen tròn, cao 17dp); người thường chữ [color] kèm tích xám khi đã xác minh.
 * Chủ video không xác minh thì chỉ có viên sáng, không có tích.
 */
@Composable
fun ChatNameTag(
    name: String,
    byCreator: Boolean,
    verified: Boolean,
    modifier: Modifier = Modifier,
    color: Color = NanColors.TextSecondary,
    fontSize: TextUnit = ChatMetaSize,
    lineHeight: TextUnit = ChatMetaLine,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null
) {
    Row(
        (if (byCreator) Modifier.height(17.dp).clip(RoundedCornerShape(50)).background(Color(0xFFF1F1F1)).padding(start = 5.dp, end = 4.dp) else Modifier)
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name, fontSize = fontSize, lineHeight = lineHeight, fontWeight = fontWeight, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (byCreator) Color(0xFF0F0F0F) else color,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (verified) Box(
            Modifier.padding(start = 4.dp).size(12.dp).clip(CircleShape).background(if (byCreator) Color(0xFF0F0F0F) else NanColors.TextSecondary),
            contentAlignment = Alignment.Center
        ) { Icon(Lucide.Check, null, Modifier.size(8.dp), tint = if (byCreator) Color.White else Color(0xFF0F0F0F)) }
    }
}

/** Chép [value] vào clipboard. Android 13+ tự báo đã chép; bản cũ hơn thì báo bằng Toast (như meetube). */
@Composable
fun rememberChatCopier(): (value: String, done: String) -> Unit {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    return { value, done ->
        clipboard.setText(AnnotatedString(value))
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
    }
}

/** Giữ vào bình luận là sao chép nội dung của nó (như meetube CommentsPanel.onCopy). */
@Composable
fun Modifier.copyOnLongPress(text: String, onCopy: () -> Unit): Modifier {
    val haptic = LocalHapticFeedback.current
    val copy by rememberUpdatedState(onCopy)
    return pointerInput(text) {
        detectTapGestures(onLongPress = {
            if (text.isNotEmpty()) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                copy()
            }
        })
    }
}
