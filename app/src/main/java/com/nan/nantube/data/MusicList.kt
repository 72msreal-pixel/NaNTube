package com.nan.nantube.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Danh sách "Music" của tài khoản: một danh sách phát thật trên YouTube tên [TITLE] (riêng tư), nằm trong thư viện
 * ngay dưới "Nội dung đã tải". Nhớ mã theo từng tài khoản; chưa có thì tìm theo tên, vẫn chưa có thì tạo một lần.
 * Vì là danh sách phát thật nên cũng hiện trong bảng "Lưu vào…" để lưu bài hát vào.
 */
object MusicList {
    const val TITLE = "Music"

    /** Video bất kỳ, chỉ để hỏi YouTube danh sách phát của tài khoản (get_add_to_playlist bắt buộc có videoId). */
    const val PROBE_VIDEO = "dQw4w9WgXcQ"

    private val gate = Mutex()
    private val attempted = HashSet<String>()

    private fun key(account: Account) = "musicList:" + account.id

    /**
     * Mã danh sách Music của [account]. [create] = true thì tạo nếu chưa có (mỗi tài khoản chỉ thử tạo một lần mỗi
     * lần mở app để lỗi mạng không sinh ra nhiều danh sách trùng; [force] = người dùng chủ động mở thì cho thử lại).
     * Trả null khi chưa có và không tạo được.
     */
    suspend fun id(account: Account?, create: Boolean, force: Boolean = false): String? {
        if (account == null) return null
        return gate.withLock {
            val prefs = AccountStore.libraryPrefs()
            prefs?.getString(key(account), null)?.takeIf { it.isNotBlank() }?.let { return@withLock it }

            val found = try {
                PlaylistRepository.options(account, PROBE_VIDEO)
                    .firstOrNull { it.id != PlaylistRepository.WATCH_LATER && it.title.trim().equals(TITLE, ignoreCase = true) }
                    ?.id
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (found != null) {
                prefs?.edit()?.putString(key(account), found)?.apply()
                return@withLock found
            }
            if (!create || (!force && !attempted.add(account.id))) return@withLock null

            val made = try {
                PlaylistRepository.create(account, TITLE, null).ifBlank { null }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (made != null) prefs?.edit()?.putString(key(account), made)?.apply()
            made
        }
    }
}
