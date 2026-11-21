# NaNTube

Bản test giao diện. Thanh dưới có 5 tab: Main, Short, Music, Search, Info.

## Đăng nhập
Nút "+" trong popup Tài khoản có 2 cách:
- **Tự nhập Gmail**: mở trang đăng nhập Google trong WebView (theo `MobileWebSignInActivity` của meetube), phiên nằm trong cookie.
- **Dùng mã**: OAuth device-code kiểu YouTube TV (theo `YTSignInPresenter` của meetube): lấy mã, nhập ở yt.be/activate, app tự poll token mỗi vài giây.

Video ở Main/Short/Search mới chỉ là danh sách tạm (chưa phát trong app), bấm vào sẽ mở link YouTube.

## Menu ba chấm
Có ở Main, Short, Search, Music, Video đã xem và các danh sách. Các mục:
- **Phát trên thiết bị khác**: nhập mã TV 12 số (YouTube TV > Cài đặt > Liên kết bằng mã TV), app gọi `pairing/get_screen` rồi `bc/bind` của Lounge để TV phát video.
- **Lưu vào danh sách Xem sau**: lấy danh sách từ `playlist/get_add_to_playlist` (mục đầu là Xem sau, đúng cách meetube làm) rồi thêm video.
- **Lưu vào danh sách phát**: bảng chọn danh sách, tạo danh sách mới. Tạo danh sách gọi `playlist/create` bằng mẫu WEB (tài khoản mã vẫn dùng Bearer) như meetube, chỉ cần HTTP thành công là coi như tạo xong (mã danh sách lấy từ phản hồi hoặc tìm lại theo tên); thêm/xóa video không còn bắt buộc phản hồi có `status`.
- **Tải xuống**: `DownloadService` tải video + audio H.264/AAC theo từng đoạn 10 MB, ghép bằng MediaMuxer (`MediaJoiner`, theo `MediaMuxHelper` của meetube) rồi lưu vào thư mục Tải xuống. Link lấy theo thứ tự của meetube (VISIONOS → ANDROID_VR → client của tài khoản), mỗi link được hỏi thử 2 byte đầu; bị googlevideo từ chối (403) thì đổi client và lấy link mới, giống `DownloadJob`.
- **Xóa khỏi danh sách video đã xem**: chỉ hiện ở trang Video đã xem.

## Music
Tab Music dùng InnerTube của YouTube Music (WEB_REMIX, theo `MusicApi` của meetube): trang chủ, tìm bài hát, mở album/danh sách/nghệ sĩ. Đăng nhập bằng "Tự nhập Gmail" thì trang chủ là của tài khoản; đăng nhập bằng mã thì xem như khách. Bấm bài hát sẽ mở music.youtube.com.

## Trang Info
- Hàng "Video đã xem": tối đa 20 video, hiện 4 cái đầu, mỗi lần vuốt tới cuối hàng hiện thêm 4 (logic theo hàng History ở trang You của meetube, `MobileBrowseActivity.showYouHistory`). Bấm tiêu đề "Video đã xem" để mở trang lịch sử đầy đủ (tìm kiếm, chip lọc, nhóm theo ngày, tự tải trang kế khi vuốt xuống).
- Ảnh bìa "Video đã thích", "Xem sau", "Video của bạn" là video đầu tiên của từng danh sách (playlist LL, WL và uploads của kênh).
- Mục "Danh sách Music" nằm ngay dưới "Nội dung đã tải": một danh sách phát riêng tư tên "Music" trên YouTube (`MusicList`), tự tìm theo tên hoặc tạo một lần, nên cũng hiện trong bảng "Lưu vào…". Chip "Âm nhạc" luôn giữ mục này.
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
