package com.nan.nantube.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nan.nantube.data.Account
import com.nan.nantube.data.Paged
import kotlinx.coroutines.CancellationException

/**
 * Danh sách vô hạn: [first] lấy trang đầu theo tham số, [more] lấy trang kế theo token.
 * Vuốt gần cuối thì [LoadMoreEffect] gọi [loadMore]. Mỗi lần [load] mới sẽ bỏ kết quả của lần cũ còn đang chạy.
 */
class PagedFeed<T>(
    private val first: suspend (Account?, String) -> Paged<T>,
    private val more: suspend (Account?, String) -> Paged<T>,
    private val keyOf: (T) -> String,
    private val emptyMessage: String = "Không có video nào."
) {
    var items by mutableStateOf<List<T>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loadedKey: String? = null
        private set

    private var token: String? = null
    private var generation = 0

    suspend fun load(account: Account?, arg: String = "") {
        val gen = ++generation
        loading = true
        loadingMore = false
        error = null
        items = emptyList()
        token = null
        try {
            val page = first(account, arg)
            if (gen != generation) return
            items = page.items.distinctBy(keyOf)
            token = page.token
            loadedKey = (account?.id ?: "") + "|" + arg
            if (items.isEmpty()) error = emptyMessage
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (gen == generation) error = e.message ?: "Không tải được dữ liệu."
        } finally {
            if (gen == generation) loading = false
        }
    }

    suspend fun loadMore(account: Account?) {
        val t = token ?: return
        if (loading || loadingMore) return
        val gen = generation
        loadingMore = true
        try {
            val page = more(account, t)
            if (gen != generation) return
            val seen = items.map(keyOf).toHashSet()
            items = items + page.items.filter { seen.add(keyOf(it)) }
            // Trang rỗng thì dừng hẳn, tránh gọi lặp vô tận.
            token = page.token?.takeIf { page.items.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Giữ token: lần vuốt sau sẽ thử lại.
        } finally {
            loadingMore = false
        }
    }
}

/** Gọi [onLoadMore] mỗi khi người dùng vuốt tới gần cuối danh sách (còn 4 mục). */
@Composable
fun LoadMoreEffect(listState: LazyListState, itemCount: Int, onLoadMore: suspend () -> Unit) {
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    val action by rememberUpdatedState(onLoadMore)
    LaunchedEffect(nearEnd, itemCount) {
        if (nearEnd) action()
    }
}

/** Khung chung cho [PagedFeed]: đang tải / lỗi + Thử lại / danh sách. */
@Composable
fun PagedBody(feed: PagedFeed<*>, onRetry: () -> Unit, list: @Composable () -> Unit) =
    StatusBody(feed.loading, feed.items.isNotEmpty(), feed.error, onRetry, list)

/** Vòng xoay nhỏ ở cuối danh sách khi đang tải trang kế. */
@Composable
fun LoadingFooter() {
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
    }
}
