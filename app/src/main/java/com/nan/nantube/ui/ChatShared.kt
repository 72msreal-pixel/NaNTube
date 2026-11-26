package com.nan.nantube.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
