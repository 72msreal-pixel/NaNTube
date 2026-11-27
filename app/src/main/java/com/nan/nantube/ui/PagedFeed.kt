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
import androidx.compose.runtime.mutableIntStateOf
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
private const val MIN_NEW = 6
private const val MAX_HOPS = 6

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
    /** Chỉ còn dùng cho "danh sách rỗng" (không phải lỗi mạng: lỗi thì tự tải lại, không báo). */
    var error by mutableStateOf<String?>(null)
        private set

    /** Số lần tải trang đầu thất bại tới giờ (UI không hiện lỗi, chỉ để biết đã thử, vd mở màn chờ). */
    var failures by mutableIntStateOf(0)
        private set

    /** Tăng mỗi khi danh sách được thay bằng bộ mới (tải lại / làm mới), để màn Short biết mà quay về video đầu. */
    var version by mutableIntStateOf(0)
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
        // Làm mới: bỏ hết bản nhớ cũ (mọi chip / từ khóa) để không bao giờ hiện lại danh sách cũ sau khi đã kéo làm mới.
        if (force) cache.clear()
        job?.cancel()
        moreJob?.cancel()
        currentKey = key
        token = null
        loadingMore = false
        error = null

        val saved = cache[key]
        if (!force && saved != null && System.currentTimeMillis() - saved.loadedAt < MAX_AGE_MS) {
            items = saved.items
            version++
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
                // Lỗi mạng / YouTube: không báo lỗi, không nút Thử lại; cứ thử lại liên tục (chờ lâu dần tới 4 giây).
                var attempt = 0
                while (true) {
                    try {
                        val page = first(account, arg)
                        prepare?.invoke(page.items)
                        if (keeping) listState = LazyListState()
                        items = page.items.distinctBy(keyOf)
                        version++
                        token = page.token
                        loadedAt = System.currentTimeMillis()
                        if (items.isEmpty()) error = emptyMessage
                        break
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failures++
                        attempt++
                        kotlinx.coroutines.delay((800L * attempt).coerceAtMost(4_000L))
                    }
                }
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
                // Trang kế của YouTube có thể toàn Short / video trùng nên sau khi lọc không còn mục nào: không được coi đó là hết danh sách,
                // mà đi tiếp sang trang sau (tối đa [MAX_HOPS] trang) cho tới khi có đủ mục mới.
                val seen = items.map(keyOf).toHashSet()
                val fresh = ArrayList<T>()
                var cursor: String? = t
                var hops = 0
                while (cursor != null && fresh.size < MIN_NEW && hops < MAX_HOPS) {
                    val page = next(account, cursor)
                    prepare?.invoke(page.items)
                    page.items.filterTo(fresh) { seen.add(keyOf(it)) }
                    cursor = page.token
                    hops++
                }
                if (fresh.isNotEmpty()) items = items + fresh
                token = cursor
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Giữ token: lần vuốt sau sẽ thử lại.
            } finally {
                if (isActive) loadingMore = false
            }
        }
    }

    /** Tải trang kế và đợi xong (hoặc đợi lượt đang tải); trả về danh sách hiện có. Dùng cho nơi không có giao diện cuộn (popup Short). */
    suspend fun loadMoreAndWait(account: Account?): List<T> {
        loadMore(account)
        moreJob?.join()
        return items
    }

    /** Sửa tại chỗ các mục đang hiện (vd đánh dấu ♪ sau khi nhận diện), giữ nguyên vị trí cuộn. */
    fun update(transform: (List<T>) -> List<T>) {
        items = transform(items)
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
    // Gắn thêm trạng thái cuộn: trang vừa tải mà chưa thêm được mục nào thì lần cuộn kế vẫn thử lại, không đứng im ở đáy.
    val scrolling = listState.isScrollInProgress
    LaunchedEffect(nearEnd, itemCount, scrolling) {
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
