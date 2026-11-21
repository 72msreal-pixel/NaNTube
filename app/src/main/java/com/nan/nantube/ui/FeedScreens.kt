package com.nan.nantube.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.nan.nantube.data.Account
import com.nan.nantube.data.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Giữ kết quả của một tab để đổi tab qua lại không phải tải lại. */
class FeedState(private val loader: suspend (Account?, String) -> List<VideoItem>) {
    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loadedKey: String? = null
        private set

    suspend fun load(account: Account?, arg: String = "") {
        loading = true
        error = null
        try {
            val result = loader(account, arg)
            items = result
            loadedKey = (account?.id ?: "") + "|" + arg
            if (result.isEmpty()) error = "Không có video nào."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Không tải được video."
        } finally {
            loading = false
        }
    }
}

fun openVideo(context: android.content.Context, video: VideoItem) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.watchUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        // Máy không có app mở link thì bỏ qua.
    }
}

@Composable
fun MainFeedScreen(state: FeedState, account: Account?) {
    val scope = rememberCoroutineScope()
    val key = (account?.id ?: "") + "|"
    LaunchedEffect(key) { if (state.loadedKey != key) state.load(account) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        FeedBody(state, onRetry = { scope.launch { state.load(account) } }) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
                items(state.items, key = { it.id }) { VideoCard(it) }
            }
        }
    }
}

@Composable
fun ShortsFeedScreen(state: FeedState, account: Account?) {
    val scope = rememberCoroutineScope()
    val key = (account?.id ?: "") + "|"
    LaunchedEffect(key) { if (state.loadedKey != key) state.load(account) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        FeedBody(state, onRetry = { scope.launch { state.load(account) } }) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
            ) {
                items(state.items, key = { it.id }) { ShortCard(it) }
            }
        }
    }
}

@Composable
fun SearchScreen(state: FeedState, account: Account?, query: String, onQueryChange: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    fun search() {
        val q = query.trim()
        if (q.isNotEmpty()) {
            focus.clearFocus()
            scope.launch { state.load(account, q) }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        val pill = RoundedCornerShape(50)
        Row(
            Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .height(44.dp)
                .clip(pill)
                .border(1.dp, NanColors.Divider, pill)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Lucide.Search, null, Modifier.size(20.dp), tint = NanColors.TextSecondary)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Tìm kiếm", fontSize = 16.sp, color = NanColors.TextSecondary)
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (state.loadedKey == null && !state.loading && state.error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nhập từ khóa để tìm video", color = NanColors.TextSecondary, fontSize = 14.sp)
            }
        } else {
            FeedBody(state, onRetry = { search() }) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
                    items(state.items, key = { it.id }) { VideoCard(it) }
                }
            }
        }
    }
}

/** Khung chung: đang tải / lỗi + Thử lại / danh sách. */
@Composable
private fun FeedBody(state: FeedState, onRetry: () -> Unit, list: @Composable () -> Unit) {
    when {
        state.loading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
        }
        state.error != null && state.items.isEmpty() -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(state.error.orEmpty(), color = NanColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFF1F1F1))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text("Thử lại", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        else -> list()
    }
}

private fun subtitle(video: VideoItem): String =
    listOfNotNull(video.channel, video.views).joinToString(" · ")

@Composable
private fun VideoCard(video: VideoItem) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { openVideo(context, video) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(NanColors.Surface)
        ) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (video.duration != null) {
                Text(
                    video.duration,
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
        }
        Row(Modifier.padding(top = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = subtitle(video)
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        fontSize = 12.sp,
                        color = NanColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            MoreButton()
        }
    }
}

@Composable
private fun ShortCard(video: VideoItem) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().clickable { openVideo(context, video) }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(12.dp))
                .background(NanColors.Surface)
        ) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            video.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        video.views?.let {
            Text(it, fontSize = 12.sp, color = NanColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
