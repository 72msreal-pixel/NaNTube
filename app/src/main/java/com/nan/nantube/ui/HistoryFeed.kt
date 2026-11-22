package com.nan.nantube.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nan.nantube.data.Account
import com.nan.nantube.data.PlaylistRepository
import com.nan.nantube.data.ChannelAvatars
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
class HistoryFeed(private val forHistory: Boolean = false, private val firstPage: suspend (Account?) -> VideoPage) {
    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var chips by mutableStateOf<List<HistoryChip>>(emptyList())
        private set
    var total by mutableStateOf<String?>(null)
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

    private var removed = emptySet<String>()

    fun remove(account: Account?, id: String) {
        if (loadedKey != account?.id) return
        removed = removed + id
        items = items.filterNot { it.id == id }
    }

    val hasMore: Boolean get() = continuation != null

    /**
     * Tải lại trang đầu. Danh sách cũ vẫn hiện cho tới khi trang mới về (không chớp); đổi tài khoản thì xoá ngay.
     */
    suspend fun refresh(account: Account?) {
        val key = account?.id ?: ""
        if (loadedKey != null && loadedKey != key) {
            items = emptyList()
            removed = emptySet()
            chips = emptyList()
            continuation = null
        }
        loading = true
        error = null
        try {
            val page = firstPage(account)
            ChannelAvatars.prefetchFor(page.items)
            items = page.items.filterNot { it.id in removed }
            continuation = page.continuation
            total = page.total
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
            val page = VideoRepository.continuePage(account, token, history = forHistory)
            ChannelAvatars.prefetchFor(page.items, 2_000L)
            val seen = items.mapTo(HashSet()) { it.id }
            items = items + page.items.filter { it.id !in removed && seen.add(it.id) }
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
 * Các danh sách thư viện lấy từ YouTube (Video đã thích, Xem sau, Video của bạn). Mỗi danh sách chỉ tải trang đầu để:
 * lấy ảnh bìa (video đầu tiên, giống YouTube) và biết danh sách có chứa nhạc không (chip Âm nhạc).
 * Danh sách nào lỗi hoặc trống thì giữ nền màu cũ.
 */
class LibraryCovers {
    var covers by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var music by mutableStateOf<Set<String>>(emptySet())
        private set
    private var key: String? = null

    suspend fun load(account: Account?) {
        val k = account?.id ?: ""
        if (key != k) {
            covers = emptyMap()
            music = emptySet()
            key = k
        }
        if (account == null) return
        coroutineScope {
            REMOTE.forEach { list -> launch { fetch(list, account) } }
        }
    }

    private suspend fun fetch(list: String, account: Account) {
        val items = try {
            page(list, account).items
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        items.firstOrNull()?.let { covers = covers + (list to it.coverThumbnail) }
        if (list == LIST_SOUNDS || items.any { it.isMusic }) music = music + list
    }

    companion object {
        const val LIST_LIKED = "liked"
        const val LIST_LATER = "later"
        const val LIST_UPLOADS = "uploads"
        const val LIST_DOWNLOADS = "downloads"
        const val LIST_SOUNDS = "sounds"

        /** Các danh sách có trên YouTube; "downloads" nằm trong máy nên không có ở đây. */
        private val REMOTE = listOf(LIST_LIKED, LIST_LATER, LIST_UPLOADS, LIST_SOUNDS)

        fun isRemote(list: String) = list in REMOTE

        suspend fun page(list: String, account: Account?): VideoPage = when (list) {
            LIST_LIKED -> VideoRepository.playlistPage(account, "LL")
            LIST_LATER -> VideoRepository.playlistPage(account, "WL")
            LIST_SOUNDS -> VideoRepository.playlistPage(account, PlaylistRepository.SOUNDS)
            else -> VideoRepository.uploadsPage(account)
        }
    }
}
