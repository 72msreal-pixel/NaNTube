package com.nan.nantube.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings

/**
 * Màn nghe giọng nói kiểu YouTube: nút X, công tắc Giọng nói / Bài hát, bánh răng, dòng "Đang nghe…"
 * (hiện chữ đang nhận được), nút micro đỏ phập phồng theo âm lượng và hình khuôn mặt ở góc dưới.
 * Dùng [SpeechRecognizer] của máy (cần quyền micro); nói xong tự gọi [onResult] với câu nhận được.
 */
@Composable
fun VoiceSearchOverlay(onResult: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var heard by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var level by remember { mutableStateOf(0f) }

    val recognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val intent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

    fun start() {
        heard = ""
        message = null
        level = 0f
        listening = true
        try {
            recognizer.startListening(intent)
        } catch (_: Exception) {
            listening = false
            message = "Không bật được tính năng nhận giọng nói."
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else message = "Hãy cấp quyền micro cho NaNTube để tìm bằng giọng nói."
    }

    DisposableEffect(recognizer) {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                // rmsdB khoảng -2..10: đổi về 0..1 cho hiệu ứng phập phồng.
                level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { level = 0f }
            override fun onError(error: Int) {
                listening = false
                level = 0f
                message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "Không nghe thấy gì. Chạm vào micro để thử lại."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Không có kết nối mạng. Chạm vào micro để thử lại."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Hãy cấp quyền micro cho NaNTube để tìm bằng giọng nói."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Bộ nhận giọng nói đang bận. Chạm vào micro để thử lại."
                    else -> "Chưa nhận được giọng nói. Chạm vào micro để thử lại."
                }
            }
            override fun onResults(results: Bundle?) {
                listening = false
                level = 0f
                val said = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (said.isNullOrBlank()) message = "Không nghe thấy gì. Chạm vào micro để thử lại." else onResult(said)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }?.let { heard = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            try {
                recognizer.cancel()
            } catch (_: Exception) {
            }
            recognizer.destroy()
        }
    }

    fun startWithPermission() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(Unit) { startWithPermission() }
    BackHandler(onBack = onClose)

    val pulse by animateFloatAsState(1f + level * 0.55f, label = "pulse")

    Box(
        Modifier
            .overlayScreen()
            .navigationBarsPadding()
    ) {
        // Hàng trên: X · công tắc Giọng nói/Bài hát · bánh răng.
        Box(Modifier.fillMaxWidth().height(64.dp)) {
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(48.dp).clip(CircleShape).clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) { Icon(IconClose, "Đóng", Modifier.size(26.dp)) }

            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 10.dp).height(44.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(Color(0xFF2B2B2B)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.height(44.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .background(Color(0xFFF1F1F1)).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(IconMic, null, Modifier.size(20.dp), tint = Color.Black)
                    Spacer(Modifier.width(6.dp))
                    Text("Giọng nói", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                Row(
                    Modifier.height(44.dp).clickable {
                        Toast.makeText(context, "NaNTube chưa hỗ trợ nhận diện bài hát", Toast.LENGTH_SHORT).show()
                    }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SoundBars(Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Bài hát", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }

            Box(
                Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(48.dp).clip(CircleShape).clickable {
                    try {
                        context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                        Toast.makeText(context, "Máy không mở được cài đặt giọng nói", Toast.LENGTH_SHORT).show()
                    }
                },
                contentAlignment = Alignment.Center
            ) { Icon(Lucide.Settings, "Cài đặt giọng nói", Modifier.size(26.dp)) }
        }

        val shown = heard.ifBlank { message ?: if (listening) "Đang nghe…" else "" }
        Text(
            shown,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            color = if (heard.isBlank() && message != null) NanColors.TextSoft else Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, end = 20.dp, top = 150.dp)
        )

        // Micro: vòng mờ phập phồng theo âm lượng, đỏ khi đang nghe, xám khi dừng (chạm để nghe lại).
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp).size(130.dp),
            contentAlignment = Alignment.Center
        ) {
            if (listening) {
                Box(Modifier.size((66 * pulse).dp).clip(CircleShape).background(Color(0x44E5003A)))
            }
            Box(
                Modifier.size(66.dp).clip(CircleShape)
                    .background(if (listening) Color(0xFFE5003A) else NanColors.Surface)
                    .clickable { if (listening) recognizer.stopListening() else startWithPermission() },
                contentAlignment = Alignment.Center
            ) { Icon(IconMic, "Micro", Modifier.size(30.dp), tint = Color.White) }
        }

        VoiceFace(Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 60.dp))
    }
}

/** Ký hiệu sóng âm cho nhãn "Bài hát": vài vạch đứng cao thấp khác nhau. */
@Composable
private fun SoundBars(modifier: Modifier) {
    Canvas(modifier) {
        val heights = listOf(0.3f, 0.65f, 1f, 0.55f, 0.25f)
        val gap = size.width / heights.size
        val stroke = size.width * 0.11f
        heights.forEachIndexed { i, h ->
            val x = gap * (i + 0.5f)
            val half = size.height * h / 2f
            drawLine(Color.White, Offset(x, size.height / 2f - half), Offset(x, size.height / 2f + half), stroke, StrokeCap.Round)
        }
    }
}

/** Khuôn mặt nét mảnh màu xám ở góc dưới bên phải của màn nghe (như YouTube): hàm, hai mắt, lông mày, mũi, miệng. */
@Composable
private fun VoiceFace(modifier: Modifier) {
    Canvas(modifier.size(width = 76.dp, height = 88.dp)) {
        val gray = Color(0xFF8E8E8E)
        val w = size.width
        val h = size.height
        val line = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        // Hàm / má: từ trên xuống rồi cong vào đáy.
        drawPath(
            Path().apply {
                moveTo(w * 0.02f, h * 0.18f)
                lineTo(w * 0.02f, h * 0.45f)
                cubicTo(w * 0.02f, h * 0.80f, w * 0.30f, h * 0.99f, w * 0.64f, h * 0.99f)
            },
            gray, style = line
        )
        // Mắt.
        drawCircle(gray, 3.6.dp.toPx(), Offset(w * 0.22f, h * 0.32f))
        drawCircle(gray, 3.6.dp.toPx(), Offset(w * 0.88f, h * 0.24f))
        // Lông mày.
        drawArc(gray, 200f, 140f, false, Offset(w * 0.08f, h * 0.06f), Size(w * 0.28f, h * 0.2f), style = line)
        drawArc(gray, 200f, 140f, false, Offset(w * 0.72f, h * 0.0f), Size(w * 0.28f, h * 0.17f), style = line)
        // Mũi.
        drawPath(
            Path().apply {
                moveTo(w * 0.50f, h * 0.38f)
                lineTo(w * 0.44f, h * 0.56f)
                lineTo(w * 0.52f, h * 0.56f)
            },
            gray, style = line
        )
        // Miệng cười.
        drawArc(gray, 20f, 120f, false, Offset(w * 0.40f, h * 0.60f), Size(w * 0.34f, h * 0.14f), style = line)
    }
}
