# NaNTube

Bản test giao diện. Thanh dưới có 4 tab: Main, Short, Search, Info.

## Đăng nhập
Nút "+" trong popup Tài khoản có 2 cách:
- **Tự nhập Gmail**: mở trang đăng nhập Google trong WebView (theo `MobileWebSignInActivity` của meetube), phiên nằm trong cookie.
- **Dùng mã**: OAuth device-code kiểu YouTube TV (theo `YTSignInPresenter` của meetube): lấy mã, nhập ở yt.be/activate, app tự poll token mỗi vài giây.

Video ở Main/Short/Search mới chỉ là danh sách tạm (chưa phát trong app), bấm vào sẽ mở link YouTube.

## Trang Info
- Hàng "Video đã xem": tối đa 20 video, hiện 4 cái đầu, mỗi lần vuốt tới cuối hàng hiện thêm 4 (logic theo hàng History ở trang You của meetube, `MobileBrowseActivity.showYouHistory`). Bấm tiêu đề "Video đã xem" để mở trang lịch sử đầy đủ (tìm kiếm, chip lọc, nhóm theo ngày, tự tải trang kế khi vuốt xuống).
- Ảnh bìa "Video đã thích", "Xem sau", "Video của bạn" là video đầu tiên của từng danh sách (playlist LL, WL và uploads của kênh).
- Mục "Nội dung đã tải" (video tải về xem khi không có mạng) mới có giao diện, chưa có chức năng tải; sẽ làm theo bộ tải của meetube (`DownloadRegistry`, `DownloadStorage`, `DownloadJob`).
- Chip "Gần đây" mở bảng sắp xếp Gần đây / A-Z / Z-A. Chip Đã tải xuống / Danh sách phát (Video đã thích + Xem sau) / Âm nhạc lọc các mục thư viện, chạm lại để bỏ lọc. "Âm nhạc" lấy các danh sách có video nhạc, đoán qua kênh "- Topic" hoặc tiêu đề MV / lyrics / remix (`VideoItem.isMusic`).

## Version
Sửa duy nhất file `version.txt` (dạng `00.00.01`). Tên version và versionCode đều lấy từ đây.
Mỗi lần ra bản mới nhớ tăng số, đừng giảm, nếu không máy sẽ không cho cập nhật đè.

## Build
Push lên nhánh `main`, GitHub Actions tự build và đưa APK vào mục Releases (và Actions > Artifacts).

## Ký app
APK ký v1 + v2 + v3 bằng `keystore/nantube.p12`.
Giữ nguyên file này mãi mãi, đổi key là phải gỡ app cài lại.
