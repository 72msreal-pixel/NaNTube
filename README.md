# NaNTube

Bản test giao diện. Thanh dưới có 5 tab: Main, Short, Music, Search, Info.

## Đăng nhập
Nút "+" trong popup Tài khoản có 2 cách:
- **Tự nhập Gmail**: mở trang đăng nhập Google trong WebView (theo `MobileWebSignInActivity` của meetube), phiên nằm trong cookie.
- **Dùng mã**: OAuth device-code kiểu YouTube TV (theo `YTSignInPresenter` của meetube): lấy mã, nhập ở yt.be/activate, app tự poll token mỗi vài giây.

Video ở Main/Short/Search mới chỉ là danh sách tạm (chưa phát trong app), bấm vào sẽ mở link YouTube.

## Phát Short (theo cách phát của meetube)
Trình phát Short không còn tải cả tệp về rồi mới phát. Cách làm lấy từ meetube + MediaServiceCore:
- **Lấy link** (`ShortsPlayback`): thứ tự VISIONOS → ANDROID_VR → client của tài khoản (như `PhoneSourcePlanner`), body /player dựng như `QueryBuilder`. Kế hoạch phát được nhớ 3 giờ, hỏi trùng chỉ chạy một lần.
- **Phát** (`ShortsMedia`): dựng bản kê DASH (SegmentBase: init + sidx, giống `YouTubeMPDBuilder`) từ các luồng H.264 + AAC (rơi về VP9/AV1 nếu không có, hoặc MP4 muxed), ExoPlayer chỉ tải từng đoạn đang cần nên lên hình gần như ngay. Mọi yêu cầu googlevideo dùng UA media của meetube, đọc quá 4 giây không có dữ liệu thì hỏng nhanh rồi thử lại từ chỗ dừng (tối đa 6 lần, chờ ≤ 1 giây); 403/404/410/416 không thử lại cùng link. Bộ nhớ đệm đĩa 256 MB với khóa ổn định (id + itag + lmt) như `Media3PlayerCache`. Ngưỡng bắt đầu phát 0,5 giây.
- **Phục hồi**: link bị từ chối, ExoPlayer báo lỗi giữa chừng hoặc 9 giây chưa lên hình thì đổi client (client vừa lỗi xếp cuối, như recovery của meetube), lấy link mới và phát tiếp đúng chỗ đang xem; quá 3 lần mới hiện "Thử lại".
- **Tải trước**: khi Short hiện tại đã lên hình, app lấy link, kênh và ~1 MB đầu của Short kế tiếp vào bộ nhớ đệm (như `Media3NextPreloader`), vuốt tới là phát ngay.
- **Nút Đăng ký** ở Short: kênh và trạng thái đăng ký thật đọc từ `next` song song với việc lấy link (không chờ video), nên nút luôn có và đúng ngay từ đầu. Bấm Đăng ký → đổi thành "Đã đăng ký"; bấm "Đã đăng ký" mở bảng **Thông báo** (Nhận / Không nhận thông báo) và **Hủy đăng ký**, cùng bảng với thẻ kênh ở Tìm kiếm.

## Main và Search (kiểu YouTube)
- **Main**: thanh trên có logo NaNTube (ảnh `nantube_logo`, nền trong suốt) + nút kết nối TV + chuông thông báo (số đỏ = chưa xem) + nút tìm kiếm, hàng chip chủ đề (Tất cả, Âm nhạc, Trò chơi, Tin tức, Thể thao, Live), thẻ video lớn 16:9 có avatar kênh, tiêu đề 2 dòng, nút ba chấm. Cuộn gần cuối tự tải trang kế. "Tất cả" = trang chủ YouTube (chưa đăng nhập thì lấy tạm kết quả tìm "thịnh hành"); các chip còn lại là tìm kiếm theo chủ đề (`HOME_CHIPS` trong `Videos.kt`, muốn đổi chủ đề chỉ sửa danh sách này).
- **Ký hiệu nhạc ♪**: `DurationBadge` vẽ ♪ trước thời lượng ở mọi nơi có ảnh bìa (Main, Search, Info, lịch sử). Video được coi là nhạc khi YouTube gắn icon MUSIC trên ảnh bìa, kênh "- Topic"/VEVO, tiêu đề có MV/lyrics/remix/bài hát/ca khúc…, hoặc đang ở chip Âm nhạc (`VideoItem.isMusic`).
- **Search**: bấm kính lúp ở Main hoặc tab Search. Ô trống hiện mục **Nhật ký** (từ đã tìm, lưu trong máy; nhấn giữ một từ sẽ hiện hộp "Xoá khỏi nhật ký tìm kiếm?" như YouTube) rồi mục **Đề xuất** gồm 10 từ hot (`HotSearches`: top tìm kiếm Google Trends Việt Nam, nhớ 3 giờ, thiếu thì bù từ danh sách `FALLBACK` mở đầu bằng mrbeast). Ô trống bấm tìm thì không làm gì. Khi gõ có gợi ý từ máy chủ gợi ý của Google/YouTube (như `MediaServiceCore` search tags), phần chữ gợi thêm được in đậm; mũi tên chéo chép gợi ý vào ô, chạm dòng để tìm ngay. Nút micro mở màn nghe riêng giống YouTube (`VoiceSearchOverlay`: X, công tắc Giọng nói/Bài hát, "Đang nghe…", micro đỏ phập phồng theo tiếng, cần quyền micro); "Bài hát" mới là nút giữ chỗ, chưa nhận diện bài hát.
- **Kết quả**: không còn ép "chỉ video" nên tìm được cả video, **kênh**, **danh sách phát**, trực tiếp; chip lọc Tất cả / Video / Kênh / Danh sách phát / Trực tiếp; cuộn vô hạn. Thẻ kênh có avatar, @handle, số người đăng ký và nút "Xem kênh" (mở trang kênh trên YouTube). Chưa làm nút Đăng ký.

## Avatar kênh, thông báo, kết nối TV
- **Avatar kênh** (`ChannelAvatar` ở `Components.kt`, dùng chung cho mọi thẻ video): ưu tiên ảnh YouTube kèm sẵn; feed của client TV (tài khoản đăng nhập bằng mã) không kèm ảnh nên `ChannelAvatars` tra theo cách `ChannelAvatars`/`getChannelAvatarUrl` của meetube + MediaServiceCore: mã kênh (lấy ở dòng tên kênh hoặc menu ngữ cảnh `get_panel`) → đầu trang YouTube Music của kênh; không được thì tìm kênh theo mã/tên. Kết quả nhớ trong máy.
- **Chuông thông báo**: `NotificationsScreen` dựng giống màn Thông báo của app YouTube (chip Tất cả / Bình luận / Lượt đề cập / Kênh của bạn, nhóm Quan trọng · Hôm nay · Tuần này, chấm xanh chưa xem, ảnh bìa bên phải, menu ⋯ gồm Xem sau / Ẩn thông báo này / Tắt thông báo từ kênh, tìm kiếm và ⋮ ở đầu màn). `NotificationRepository` thử hộp thư thật `notification/get_notification_menu` trước; client TV (tài khoản đăng nhập bằng mã) bị YouTube từ chối với HTTP 400 "Precondition check failed" (meetube và MediaServiceCore cũng gặp), nên khi đó app dựng thông báo từ `FEsubscriptions` (video mới và buổi phát trực tiếp của các kênh đã đăng ký). Chế độ này, trạng thái đã xem / ẩn / tắt kênh do app nhớ trong máy; hai tab Bình luận và Lượt đề cập chỉ có dữ liệu khi hộp thư thật đọc được. Số đỏ trên chuông là số thông báo chưa xem của danh sách này. Chuông ở Main và Info cùng mở màn này.
- **Nút cast** ở Main: nhập mã TV một lần (`TvConnectDialog`), TV được nhớ (`TvStore`); "Phát trên thiết bị khác" ở menu video sẽ phát thẳng lên TV đó, có "Dùng mã TV khác".

## Chuông từng kênh và thông báo video mới
Bấm "Đã đăng ký" ở thẻ kênh: bảng Thông báo chỉ còn **Nhận thông báo / Không nhận thông báo** (mặc định nhận), lưu trên máy (`ChannelNotify`), không gọi API chuông của YouTube. `NewUploadsWorker` (WorkManager, mỗi giờ, theo NewUploadsWorker của meetube) đọc feed kênh đăng ký, video mới của kênh đang bật chuông thì đăng thông báo hệ thống; lần chạy đầu chỉ ghi nhớ, không đăng. "Tắt thông báo từ kênh" trong màn Thông báo cũng tắt chuông kênh đó.

## Menu ba chấm
Có ở Main, Short, Search, Music, Video đã xem và các danh sách. Các mục:
- **Phát trên thiết bị khác**: nhập mã TV 12 số (YouTube TV > Cài đặt > Liên kết bằng mã TV), app gọi `pairing/get_screen` rồi `bc/bind` của Lounge để TV phát video.
- **Lưu vào danh sách Xem sau**: lấy danh sách từ `playlist/get_add_to_playlist` (mục đầu là Xem sau, đúng cách meetube làm) rồi thêm video.
- **Lưu vào danh sách phát**: bảng chọn danh sách, tạo danh sách mới. Hộp "Danh sách phát mới" chỉ có ô tiêu đề (meetube không có chế độ hiển thị; YouTube tự đặt riêng tư). Tạo danh sách gọi `playlist/create` đúng như meetube (`InnerTube.createPlaylist`): thân mẫu WEB chỉ thêm `title` (+ `videoIds`), tài khoản mã đi kèm Bearer với bộ header của interceptor meetube (UA app, referer TV, không `key`, tham số `t`), tài khoản web đi theo `WebSession.post` (cookie + SAPISIDHASH). Mục "Âm thanh" của YouTube được ẩn; chỗ đó (ngay sau "Xem sau") là danh sách Music của app. Thêm/xóa video không bắt buộc phản hồi có `status`.
- **Tải xuống**: `DownloadService` tải video + audio H.264/AAC theo từng đoạn 10 MB, ghép bằng MediaMuxer (`MediaJoiner`, theo `MediaMuxHelper` của meetube) rồi lưu vào thư mục Tải xuống. Link lấy theo thứ tự của meetube (VISIONOS → ANDROID_VR → client của tài khoản). Body /player dựng như `QueryBuilder` của meetube (clientScreen, userAgent, browser, vùng, `visitorData` lấy từ `youtubei/v1/visitor_id` như `VisitorService`, cpn, racyCheckOk/contentCheckOk, URL không `key`); tải googlevideo bằng đúng UA media của meetube (`MEDIA_USER_AGENT`). Mỗi link được hỏi thử 2 byte đầu; bị googlevideo từ chối (403) thì đổi client và lấy link mới, giống `DownloadJob`. Khi không client nào dùng được, thông báo lỗi kèm lý do của YouTube (vd. LOGIN_REQUIRED) để dễ chẩn đoán.

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
