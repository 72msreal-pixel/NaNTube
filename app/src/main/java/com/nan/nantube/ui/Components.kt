package com.nan.nantube.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play

fun Modifier.plainClickable(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

@Composable
fun MoreButton(modifier: Modifier = Modifier) {
    Box(
        modifier.size(36.dp).clip(CircleShape).clickable { },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(width = 16.dp, height = 4.dp)) {
            val r = size.height / 2f
            for (x in listOf(r, size.width / 2f, size.width - r)) {
                drawCircle(Color.White, r, Offset(x, r))
            }
        }
    }
}

@Composable
fun Avatar(letter: String, size: Dp, modifier: Modifier = Modifier, photoUrl: String? = null) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFFFF4D4D), Color(0xFF8A0F1B)))),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
        if (!photoUrl.isNullOrBlank()) {
            // Ảnh tải xong sẽ phủ lên chữ cái; lỗi mạng thì chữ cái vẫn còn đó.
            AsyncImage(
                model = if (photoUrl.startsWith("//")) "https:$photoUrl" else photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ThumbPlaceholder(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    duration: String? = null,
    cornerIcon: ImageVector? = null,
    progress: Float = 0f
) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(colors))) {
        Icon(
            Lucide.Play,
            null,
            Modifier.align(Alignment.Center).size(26.dp),
            tint = Color.White.copy(alpha = 0.55f)
        )
        if (cornerIcon != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(cornerIcon, null, Modifier.size(16.dp))
            }
        } else if (duration != null) {
            Text(
                duration,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
        if (progress > 0f) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(Color.Red)
            )
        }
    }
}
