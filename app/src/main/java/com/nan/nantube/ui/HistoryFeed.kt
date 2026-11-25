package com.nan.nantube.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nan.nantube.data.Account
import com.nan.nantube.data.PlaylistRepository
import com.nan.nantube.data.MusicDetector
import com.nan.nantube.data.ShortDetector
import com.nan.nantube.data.ChannelAvatars
import com.nan.nantube.data.HistoryChip
import com.nan.nantube.data.VideoItem
import com.nan.nantube.data.VideoPage
import com.nan.nantube.data.VideoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Số video kiểm tra Short mỗi đợt (xem [HistoryFeed.classifyShorts]). */
private const val ROUND = 12

/** Số trang liền nhau không thêm được video nào thì mới dừng hẳn (như `ShelfTail.MAX_EMPTY_PAGES` của meetube). */
private const val MAX_EMPTY_PAGES = 10

/** Chờ bao lâu rồi tự tải lại trang kế sau một lần lỗi. */
const val MORE_RETRY_MS = 3_000L

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
    /** Lần tải trang kế gần nhất bị lỗi (mạng...): UI chờ một lúc rồi tự thử lại, không cần vuốt lên xuống. */
    var moreFailed by mutableStateOf(false)
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

    /** Đang kiểm tra xem video nào thực ra là Short (xem [ShortDetector]); UI dùng để không báo "không có" quá sớm. */
    var classifying by mutableStateOf(false)
        private set

    /**
     * Đánh dấu các Short mà YouTube không gắn nhãn trong danh sách (Short dài, thẻ TV của danh sách phát...). Chạy tuần tự
     * từng đợt nhỏ và cập nhật ngay sau mỗi đợt nên Short hiện dần trong chip "Shorts". Gọi lại khi danh sách thêm video.
     */
    suspend fun classifyShorts() {
        if (items.none { ShortDetector.needsCheck(it) }) return
        classifying = true
        try {
            val tried = HashSet<String>() // id không xác định được (lỗi mạng) chỉ thử một lần mỗi lượt gọi, lần sau mới thử lại
            while (true) {
                val todo = items.filter { it.id !in tried && ShortDetector.needsCheck(it) }.take(ROUND)
                if (todo.isEmpty()) break
                tried.addAll(todo.map { it.id })
                val found = ShortDetector.check(todo.map { it.id })
                if (found.values.any { it }) {
                    items = items.map { v -> if (found[v.id] == true && !v.isShort) v.copy(isShort = true) else v }
                }
            }
        } finally {
            classifying = false
        }
    }

    /**
     * Đánh dấu nhạc mà thẻ danh sách không gắn ♪ (xem [MusicDetector]), từng đợt nhỏ, cập nhật ngay sau mỗi đợt nên bài hát
     * hiện dần trong chip "Âm nhạc". Gọi lại khi danh sách thêm video.
     */
    suspend fun classifyMusic() {
        if (items.none { MusicDetector.needsCheck(it) }) return
        val tried = HashSet<String>()
        while (true) {
            val todo = items.filter { it.id !in tried && MusicDetector.needsCheck(it) }.take(ROUND)
            if (todo.isEmpty()) break
            tried.addAll(todo.map { it.id })
            val found = MusicDetector.check(todo.map { it.id })
            if (found.values.any { it }) {
                items = items.map { v -> if (found[v.id] == true && !v.music) v.copy(music = true) else v }
            }
        }
    }

    /**
     * Tải lại trang đầu. Danh sách cũ vẫn hiện cho tới khi trang mới về (không chớp); đổi tài khoản thì xoá ngay.
     */
    suspend fun refresh(account: Account?, silent: Boolean = false) {
        val key = account?.id ?: ""
        if (loadedKey != null && loadedKey != key) {
            items = emptyList()
            removed = emptySet()
            chips = emptyList()
            continuation = null
        }
        // silent: tải nền định kỳ, không bật "Đang tải…", không báo lỗi, và chỉ đổi danh sách khi có video mới/khác.
        if (!silent) {
            loading = true
            error = null
        }
        try {
            var attempt = 0
            while (true) {
                try {
                    val page = firstPage(account)
                    val fresh = page.items.filterNot { it.id in removed }.map(ShortDetector::applyCached).map(MusicDetector::applyCached)
                    val freshIds = fresh.map { it.id }
                    val loadedMore = items.size > fresh.size // đã kéo xuống tải thêm các trang sau
                    if (!silent || !loadedMore) {
                        if (!silent || freshIds != items.map { it.id }) {
                            ChannelAvatars.prefetchFor(page.items)
                            items = fresh
                            continuation = page.continuation
                            total = page.total
                        }
                    } else if (freshIds != items.take(fresh.size).map { it.id }) {
                        // Tải nền định kỳ: có video mới ở đầu thì đặt lên đầu, GIỮ các trang đã tải thêm và token trang kế
                        // (trước đây ghi đè bằng trang đầu nên danh sách cứ bị cắt về vài video).
                        ChannelAvatars.prefetchFor(page.items)
                        val head = freshIds.toHashSet()
                        items = fresh + items.filter { it.id !in head }
                        total = page.total
                    }
                    if (page.chips.isNotEmpty() || loadedKey != key) chips = page.chips
                    loadedKey = key
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Không báo lỗi: tải nền (silent) bỏ qua, tải thường thì cứ thử lại liên tục như bên Short.
                    if (silent) break
                    attempt++
                    kotlinx.coroutines.delay((800L * attempt).coerceAtMost(4_000L))
                }
            }
        } finally {
            if (!silent) loading = false
        }
    }

    /**
     * Nối trang kế vào cuối danh sách; bỏ video đã có để khóa trong LazyColumn không trùng.
     *
     * Theo `ShelfTail` của meetube (danh sách dạng lưới như Lịch sử): trang không thêm được video nào (toàn Short bị lọc,
     * toàn video đã có, trang rỗng) KHÔNG làm danh sách dừng hẳn mà đi tiếp trang sau, tối đa [MAX_EMPTY_PAGES] trang
     * liền nhau. Trước đây một trang như vậy xoá luôn token nên lịch sử chỉ còn vài video: không có thẻ mới ở cuối thì
     * danh sách không cuộn được, không cuộn thì không ai hỏi trang kế nữa.
     */
    suspend fun loadMore(account: Account?) {
        var token = continuation ?: return
        if (loading || loadingMore) return
        loadingMore = true
        try {
            var emptyPages = 0
            while (true) {
                val page = VideoRepository.continuePage(account, token, history = forHistory)
                ChannelAvatars.prefetchFor(page.items, 2_000L)
                val seen = items.mapTo(HashSet()) { it.id }
                val added = page.items.filter { it.id !in removed && seen.add(it.id) }.map(ShortDetector::applyCached).map(MusicDetector::applyCached)
                // Token lặp lại đúng cái vừa dùng = YouTube không còn trang nào, tránh tải lặp mãi.
                val next = page.continuation?.takeIf { it != token }
                moreFailed = false
                if (added.isNotEmpty()) {
                    items = items + added
                    continuation = next
                    return
                }
                emptyPages++
                if (next == null || emptyPages >= MAX_EMPTY_PAGES) {
                    continuation = null
                    return
                }
                token = next
                continuation = next // bị huỷ giữa chừng thì lần sau đi tiếp từ trang này
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Lỗi mạng giữa chừng: giữ nguyên những gì đã có; UI tự thử lại sau [MORE_RETRY_MS] (xem moreFailed).
            moreFailed = true
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
        if (list == LIST_SOUNDS || items.map(MusicDetector::applyCached).any { it.isMusic }) music = music + list
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
            else -> if (list.startsWith(OpenList.PUBLIC_PREFIX)) VideoRepository.playlistPage(account, list.removePrefix(OpenList.PUBLIC_PREFIX).removePrefix("VL"), guestOk = true)
            else VideoRepository.uploadsPage(account)
        }
    }
}
