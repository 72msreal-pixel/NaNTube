package com.nan.nantube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.nan.nantube.data.AccountStore
import com.nan.nantube.data.QualityPrefs
import com.nan.nantube.data.VideoQualityMode

/**
 * Trang con của Cài đặt → "Tiết kiệm dữ liệu", bố cục như YouTube nhưng chỉ giữ những mục app có logic thật.
 * Dùng chung dữ liệu với trang "Chất lượng" ([QualityPrefs]) nên hai trang luôn khớp nhau:
 * - Chế độ tiết kiệm dữ liệu: bật = video trên mạng di động ở mức "Tiết kiệm dữ liệu", tắt = "Tự động";
 * - Giảm chất lượng video: bật = video trên Wi-Fi cũng ở mức "Tiết kiệm dữ liệu", tắt = "Tự động".
 * Các mục tải xuống / tải lên / không tiếng / nhắc dữ liệu di động của YouTube bị bỏ vì app chưa có các tính năng đó.
 */
@Composable
fun DataSaverSettingsScreen(onBack: () -> Unit) {
    val account = AccountStore.selected
    LaunchedEffect(account?.id) { QualityPrefs.bind(account) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        YtSettingsTopBar("Tiết kiệm dữ liệu", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            YtSwitchRow(
                title = "Chế độ tiết kiệm dữ liệu",
                subtitle = "Tự động điều chỉnh chế độ cài đặt để tiết kiệm dữ liệu di động",
                checked = QualityPrefs.mobile == VideoQualityMode.SAVER,
                onCheckedChange = { QualityPrefs.updateMobile(if (it) VideoQualityMode.SAVER else VideoQualityMode.AUTO) }
            )
            YtSectionHeader("Chế độ cài đặt mặc định")
            YtSwitchRow(
                title = "Giảm chất lượng video",
                subtitle = "Áp dụng khi xem bằng Wi-Fi",
                checked = QualityPrefs.wifi == VideoQualityMode.SAVER,
                onCheckedChange = { QualityPrefs.updateWifi(if (it) VideoQualityMode.SAVER else VideoQualityMode.AUTO) }
            )
        }
    }
}
