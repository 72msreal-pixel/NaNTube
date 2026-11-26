package com.nan.nantube.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nan.nantube.data.PipPrefs
import com.nan.nantube.data.VideoItem

/** Ba nút điều khiển của cửa sổ PiP, do bộ phát Short đang hiện đăng ký. */
class PipControls(val prev: () -> Unit, val next: () -> Unit, val toggle: () -> Unit)

/**
 * Trạng thái "Short đang hiện trên màn hình" dùng chung giữa [com.nan.nantube.MainActivity], bộ phát Short và [ShortsBackground]
 * (làm theo MobilePlaybackActivity của meetube). Chỉ một bộ phát Short được coi là "đang hiện" (tab Short, màn xem Short
 * mở từ nơi khác, hoặc thẻ mini). Việc quyết định khi rời app (PiP / phát nền / dừng) nằm hết ở [ShortsBackground].
 */
object PipState {
    /** Máy có hỗ trợ PiP (Android 8+ và có tính năng hệ thống). Activity đặt khi khởi động. */
    var supported by mutableStateOf(false)

    /** Cửa sổ app đang là cửa sổ PiP: giao diện ẩn mọi thứ ngoài video. */
    var inPip by mutableStateOf(false)

    /** Short đang hiện trên màn hình; null = không có Short nào (đang ở tab khác...). */
    var shortId by mutableStateOf<String?>(null)

    /** Thông tin Short đang hiện (cho thông báo media / màn khóa). */
    var video: VideoItem? = null

    /** Short đang hiện có đang phát (người dùng chưa bấm tạm dừng). */
    var playing by mutableStateOf(false)

    /** Video dọc (9:16) hay ngang (16:9): quyết định tỉ lệ cửa sổ PiP. */
    var vertical by mutableStateOf(true)

    /** Có Short đang phát trên màn hình (dù máy có PiP hay không): rời app thì phải phát tiếp ở nền. */
    val playingShort: Boolean get() = shortId != null && playing

    /** Rời app lúc này thì vào PiP: có Short đang phát (như YouTube, tạm dừng rồi thì không vào). */
    val armed: Boolean get() = supported && playingShort

    var controls: PipControls? = null

    /** Bộ phát Short toàn màn hình đăng ký: danh sách + vị trí hiện tại, để phiên nền tiếp tục đúng video khi app ẩn. */
    var snapshot: (() -> Pair<List<VideoItem>, Int>)? = null

    /** Vừa bấm nút / mục menu Hình trong hình ở Short: bộ phát đang hiện thấy cờ này thì mở thẻ mini trong app ([ShortsMini]) rồi hạ cờ. */
    var miniRequest by mutableStateOf(false)

    /** Bấm Hình trong hình khi cài đặt popup đang tắt: AppRoot mở trang "Thử nghiệm các tính năng mới" (SettingsScreen đọc rồi hạ cờ). */
    var openExperiments by mutableStateOf(false)

    fun request() {
        if (PipPrefs.popup) miniRequest = true else openExperiments = true
    }

    /**
     * App hiện lại sau khi phiên nền đã chuyển sang Short khác (Tiếp / Trước / tự vuốt lúc app ẩn): bộ phát toàn màn hình
     * thấy mã này thì cuộn tới đúng video rồi hạ về null.
     */
    var returnId by mutableStateOf<String?>(null)
}
