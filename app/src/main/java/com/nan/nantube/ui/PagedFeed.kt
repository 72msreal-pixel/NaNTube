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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val MAX_CACHED = 8
private const val MAX_AGE_MS = 30 * 60 * 1000L

/** Kết quả đã tải của một khóa (tài khoản | tham số) cùng vị trí cuộn lúc rời đi. */
private class Snapshot<T>(val items: List<T>, val token: String?, val loadedAt: Long, val index: Int, val offset: Int)

/**
 * Danh sách vô hạn: [first] lấy trang đầu theo tham số, [more] lấy trang kế theo token.
 * Vuốt gần cuối thì [LoadMoreEffect] gọi [loadMore].
 *
 * Việc tải chạy trong scope riêng của feed nên rời tab / xoay màn hình không làm mất lượt đang tải.
 * Đổi khóa (chip, từ khóa, tài khoản) thì kết quả cũ được nhớ lại [MAX_CACHED] khóa gần nhất trong [MAX_AGE_MS],
 * quay lại khóa đó là hiện ngay đúng chỗ đang cuộn, không tải lại.
 */
class PagedFeed<T : Any>(
    private val first: suspend (Account?, String) -> Paged<T>,
    private val more: (suspend (Account?, String) -> Paged<T>)? = null,
    private val keyOf: (T) -> Any = { it },
    private val emptyMessage: String = "Không có video nào.",
    /** Chạy sau khi tải mỗi trang, TRƯỚC khi trang hiện ra (vd chờ avatar kênh sẵn sàng để thẻ không nhấp nháy). */
    private val prepare: (suspend (List<T>) -> Unit)? = null
) {
    var items by mutableStateOf<List<T>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Trạng thái cuộn của khóa đang hiện; giữ ở đây để đổi tab không mất chỗ đang xem. */
    var listState by mutableStateOf(LazyListState())
        private set

    private var currentKey: String? = null
    private var loadedAt = 0L
    private var token: String? = null
    private var job: Job? = null
    private var moreJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cache = object : LinkedHashMap<String, Snapshot<T>>(MAX_CACHED, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Snapshot<T>>) = size > MAX_CACHED
    }

    /** Hiện danh sách của (account, arg): đang hiện / đang tải đúng khóa đó thì thôi, có trong bộ nhớ thì lấy lại, không thì tải. */
    /** [keep]: làm mới (kéo xuống) thì giữ danh sách cũ trên màn đến khi có danh sách mới. */
    fun load(account: Account?, arg: String = "", force: Boolean = false, keep: Boolean = false) {
        val key = (account?.id ?: "") + "|" + arg
        if (!force && key == currentKey) return
        stash()
        job?.cancel()
        moreJob?.cancel()
        currentKey = key
        token = null
        loadingMore = false
        error = null

        val saved = cache[key]
        if (!force && saved != null && System.currentTimeMillis() - saved.loadedAt < MAX_AGE_MS) {
            items = saved.items
            token = saved.token
            loadedAt = saved.loadedAt
            listState = LazyListState(saved.index, saved.offset)
            loading = false
            return
        }

        val keeping = keep && items.isNotEmpty()
        if (!keeping) {
            items = emptyList()
            listState = LazyListState()
        }
        loading = true
        job = scope.launch {
            try {
                val page = first(account, arg)
                prepare?.invoke(page.items)
                if (keeping) listState = LazyListState()
                items = page.items.distinctBy(keyOf)
                token = page.token
                loadedAt = System.currentTimeMillis()
                if (items.isEmpty()) error = emptyMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentKey = null // lần hiện lại sau sẽ tải lại
                error = e.message ?: "Không tải được dữ liệu."
            } finally {
                if (isActive) loading = false
            }
        }
    }

    fun loadMore(account: Account?) {
        val next = more ?: return
        val t = token ?: return
        if (loading || loadingMore) return
        loadingMore = true
        moreJob = scope.launch {
            try {
                val page = next(account, t)
                prepare?.invoke(page.items)
                val seen = items.map(keyOf).toHashSet()
                items = items + page.items.filter { seen.add(keyOf(it)) }
                // Trang rỗng thì dừng hẳn, tránh gọi lặp vô tận.
                token = page.token?.takeIf { page.items.isNotEmpty() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Giữ token: lần vuốt sau sẽ thử lại.
            } finally {
                if (isActive) loadingMore = false
            }
        }
    }

    /** Nhớ lại khóa đang hiện (nếu đã tải xong) trước khi chuyển sang khóa khác. */
    private fun stash() {
        val key = currentKey ?: return
        if (loading || items.isEmpty()) return
        cache[key] = Snapshot(items, token, loadedAt, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
    }
}

/** Feed chỉ có một trang (Shorts, Music): không có trang kế. */
fun <T : Any> singlePageFeed(
    keyOf: (T) -> Any = { it },
    loader: suspend (Account?, String) -> List<T>
) = PagedFeed(first = { account, arg -> Paged(loader(account, arg), null) }, keyOf = keyOf)

/** Gọi [onLoadMore] mỗi khi người dùng vuốt tới gần cuối danh sách (còn 4 mục). */
@Composable
fun LoadMoreEffect(listState: LazyListState, itemCount: Int, onLoadMore: () -> Unit) {
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
