package com.nan.nantube.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Ba nút điều khiển của cửa sổ PiP, do bộ phát Short đang hiện đăng ký. */
class PipControls(val prev: () -> Unit, val next: () -> Unit, val toggle: () -> Unit)

/**
 * Trạng thái Picture-in-Picture dùng chung giữa [com.nan.nantube.MainActivity] và bộ phát Short (làm theo cách meetube:
 * cửa sổ PiP chỉ còn video, có nút Trước / Phát-Tạm dừng / Tiếp như YouTube, và tự vào PiP khi rời app lúc Short đang phát).
 * Chỉ một bộ phát Short được coi là "đang hiện" (tab Short hoặc màn xem Short mở từ nơi khác).
 */
object PipState {
    /** Máy có hỗ trợ PiP (Android 8+ và có tính năng hệ thống). Activity đặt khi khởi động. */
    var supported by mutableStateOf(false)

    /** Cửa sổ app đang là cửa sổ PiP: giao diện ẩn mọi thứ ngoài video. */
    var inPip by mutableStateOf(false)

    /** Short đang hiện trên màn hình; null = không có Short nào (đang ở tab khác...). */
    var shortId by mutableStateOf<String?>(null)

    /** Short đang hiện có đang phát (người dùng chưa bấm tạm dừng). */
    var playing by mutableStateOf(false)

    /** Video dọc (9:16) hay ngang (16:9): quyết định tỉ lệ cửa sổ PiP. */
    var vertical by mutableStateOf(true)

    /** Rời app lúc này thì vào PiP: có Short đang phát (như YouTube, tạm dừng rồi thì không vào). */
    val armed: Boolean get() = supported && shortId != null && playing

    var controls: PipControls? = null

    /** Bộ phát Short toàn màn hình đăng ký: danh sách + vị trí hiện tại, để cửa sổ nổi tiếp tục đúng video khi rời app. */
    var snapshot: (() -> Pair<List<com.nan.nantube.data.VideoItem>, Int>)? = null

    /** MainActivity gán: vào PiP hệ thống ngay. */
    var enter: (() -> Unit)? = null

    /** Vừa bấm nút / mục menu Hình trong hình ở Short: bộ phát đang hiện thấy cờ này thì mở cửa sổ nhỏ ([ShortsMini]) rồi hạ cờ. */
    var miniRequest by mutableStateOf(false)

    fun request() {
        miniRequest = true
    }
}
