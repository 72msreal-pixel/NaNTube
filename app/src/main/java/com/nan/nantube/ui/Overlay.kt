package com.nan.nantube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** Chặn cú chạm xuyên xuống màn phía dưới (các màn phủ lên feed mà không chặn sẽ bấm trúng video bên dưới). */
fun Modifier.blockTouchThrough(): Modifier = pointerInput(Unit) { detectTapGestures { } }

/** Nền chung cho màn phủ toàn màn hình: tràn khung, nền app, chặn chạm xuyên, chừa thanh trạng thái. */
fun Modifier.overlayScreen(): Modifier =
    fillMaxSize().background(NanColors.Background).blockTouchThrough().statusBarsPadding()
