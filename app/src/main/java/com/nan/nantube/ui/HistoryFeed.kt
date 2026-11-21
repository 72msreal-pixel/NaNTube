package com.nan.nantube.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nan.nantube.data.Account
import com.nan.nantube.data.HistoryChip
import com.nan.nantube.data.VideoItem
import com.nan.nantube.data.VideoPage
import com.nan.nantube.data.VideoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Một danh sách video phân trang (trang đầu + các trang "continuation").
 * Dùng chung cho hàng "Video đã xem" ở Info (chỉ lấy tối đa 20 cái đầu) và cho trang Video đã xem đầy đủ,
 * nên mở trang đầy đủ là thấy ngay những gì hàng đã tải.
 */
class HistoryFeed(private val firstPage: suspend (Account?) -> VideoPage) {
    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var chips by mutableStateOf<List<HistoryChip>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var continuation by mutableStateOf<String?>(null)
    var loadedKey: String? = null
        private set

    val hasMore: Boolean get() = continuation != null

    /**
     * Tải lại trang đầu. Danh sách cũ vẫn hiện cho tới khi trang mới về (không chớp); đổi tài khoản thì xoá ngay.
     */
    suspend fun refresh(account: Account?) {
        val key = account?.id ?: ""
        if (loadedKey != null && loadedKey != key) {
            items = emptyList()
            chips = emptyList()
            continuation = null
        }
        loading = true
        error = null
        try {
            val page = firstPage(account)
            items = page.items
            continuation = page.continuation
            if (page.chips.isNotEmpty() || loadedKey != key) chips = page.chips
            loadedKey = key
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Không tải được video."
        } finally {
            loading = false
        }
    }

    /** Nối trang kế vào cuối danh sách; bỏ video đã có để khóa trong LazyColumn không trùng. */
    suspend fun loadMore(account: Account?) {
        val token = continuation ?: return
        if (loading || loadingMore) return
        loadingMore = true
        try {
            val page = VideoRepository.continuePage(account, token)
            val seen = items.mapTo(HashSet()) { it.id }
            items = items + page.items.filter { seen.add(it.id) }
            continuation = page.continuation
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Lỗi mạng giữa chừng: giữ nguyên những gì đã có, người dùng vuốt tiếp sẽ thử lại.
        } finally {
            loadingMore = false
        }
    }
}

/**
 * Ảnh bìa các mục thư viện (Xem sau, Video đã thích, Video của bạn): lấy video đầu tiên của từng danh sách,
 * giống YouTube. Danh sách nào lỗi hoặc trống thì giữ nền màu cũ.
 */
class LibraryCovers {
    var covers by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    private var key: String? = null

    suspend fun load(account: Account?) {
        val k = account?.id ?: ""
        if (key != k) {
            covers = emptyMap()
            key = k
        }
        if (account == null) return
        coroutineScope {
            launch { fetch(LIST_LIKED) { VideoRepository.playlistFirst(account, "LL") } }
            launch { fetch(LIST_LATER) { VideoRepository.playlistFirst(account, "WL") } }
            launch { fetch(LIST_UPLOADS) { VideoRepository.uploadsFirst(account) } }
        }
    }

    private suspend fun fetch(id: String, block: suspend () -> VideoItem?) {
        val video = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (video != null) covers = covers + (id to video.coverThumbnail)
    }

    companion object {
        const val LIST_LIKED = "liked"
        const val LIST_LATER = "later"
        const val LIST_UPLOADS = "uploads"
    }
}
