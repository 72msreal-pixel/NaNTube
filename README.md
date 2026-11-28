# NaNTube

Bản test giao diện. Thanh dưới có 5 tab: Main, Short, Music, Search, Info.

## Đăng nhập
Nút "+" trong popup Tài khoản có 2 cách:
- **Tự nhập Gmail**: mở trang đăng nhập Google trong WebView (theo `MobileWebSignInActivity` của meetube), phiên nằm trong cookie.
- **Dùng mã**: OAuth device-code kiểu YouTube TV (theo `YTSignInPresenter` của meetube): lấy mã, nhập ở yt.be/activate, app tự poll token mỗi vài giây.

Video ở Main/Short/Search mới chỉ là danh sách tạm (chưa phát trong app), bấm vào sẽ mở link YouTube.

## Phát Short (theo cách phát của meetube)
Trình phát Short không còn tải cả tệp về rồi mới phát. Cách làm lấy từ meetube + MediaServiceCore:
- **Lấy link** (`PlaybackSource`, dùng chung cho Short, Tải xuống và `ShortDetector`): đúng `PhoneSourcePlanner` + `PlayerSourceCatalog` của meetube, thứ tự VISIONOS → TV_TIZEN → WEB_EMBED → ANDROID_VR, body /player dựng như `QueryBuilder` (user, racyCheckOk/contentCheckOk, playbackContext + signatureTimestamp thật, cpn, visitorData; URL không `key`, không Referer). Chỉ **tài khoản đăng nhập bằng mã** đi cùng một nguồn: TV_TIZEN kèm Bearer, luôn là nguồn thứ hai (client TV 7.x/5.x kiểu cũ đã bỏ vì link chết, nguyên nhân lỗi "UNPLAYABLE"). Chưa đăng nhập hoặc tài khoản web thì TV_TIZEN chạy ẩn danh, chỉ được hỏi sau khi một nguồn không-web từ chối video vì nội dung (`admitsAccountRouteAfter`). Mỗi nguồn có hạn chờ riêng (7/15/20 giây) và thông báo lỗi liệt kê lý do từng nguồn. Kế hoạch phát được nhớ 3 giờ, hỏi trùng chỉ chạy một lần.
- **Phát** (`ShortsMedia`): dựng bản kê DASH (SegmentBase: init + sidx, giống `YouTubeMPDBuilder`) từ các luồng H.264 + AAC (rơi về VP9/AV1 nếu không có, hoặc MP4 muxed), ExoPlayer chỉ tải từng đoạn đang cần nên lên hình gần như ngay. Mọi yêu cầu googlevideo dùng UA media của meetube, đọc quá 4 giây không có dữ liệu thì hỏng nhanh rồi thử lại từ chỗ dừng (tối đa 6 lần, chờ ≤ 1 giây); 403/404/410/416 không thử lại cùng link. Bộ nhớ đệm đĩa 256 MB với khóa ổn định (id + itag + lmt) như `Media3PlayerCache`. Ngưỡng bắt đầu phát 0,5 giây.
- **Phục hồi**: link bị từ chối, ExoPlayer báo lỗi giữa chừng hoặc 9 giây chưa lên hình thì đổi client (client vừa lỗi xếp cuối, như recovery của meetube), lấy link mới và phát tiếp đúng chỗ đang xem; quá 3 lần mới hiện "Thử lại".
- **Tải trước**: khi Short hiện tại đã lên hình, app lấy link, kênh và ~1 MB đầu của Short kế tiếp vào bộ nhớ đệm (như `Media3NextPreloader`), vuốt tới là phát ngay.
- **Nút Đăng ký** ở Short: kênh và trạng thái đăng ký thật đọc từ `next` song song với việc lấy link (không chờ video), nên nút luôn có và đúng ngay từ đầu. Bấm Đăng ký → đổi thành "Đã đăng ký"; bấm "Đã đăng ký" mở bảng **Thông báo** (Nhận / Không nhận thông báo) và **Hủy đăng ký**, cùng bảng với thẻ kênh ở Tìm kiếm.
- **Menu ba chấm ở Short** (`ShortsMenu.kt`): bấm ⋯ ở góc trên trượt lên bảng như Shorts của YouTube với 6 mục. *Thông tin mô tả* mở bảng mô tả (như chạm tiêu đề). *Làm sạch màn hình* ẩn avatar / tên / tiêu đề / các nút / kính lúp / ⋯, chỉ còn nút nhỏ ở góc dưới phải để hiện lại (Back cũng hiện lại). *Phụ đề* liệt kê `captionTracks` của /player, chọn xong app tải timedtext (`ShortsCaptions`, json3 rồi XML) và tự vẽ theo vị trí phát, lựa chọn nhớ qua các Short. *Chất lượng* ép mức video trong DASH bằng `TrackSelectionOverride` (`ShortsPool.setQuality`), "Tự động" là bỏ ép; chỉ có các mức ≤ 720p mà `PlayPlan` dựng. *Không quan tâm* / *Không đề xuất kênh này* lấy feedbackToken trong menu của `reel/reel_item_watch` rồi gửi `feedback` (`ShortsFeedback`), xong tự sang Short kế.
- **Bảng kéo được** (`DraggablePanel` ở `ShortsPanel.kt`): Thông tin mô tả, menu ba chấm và Bản ghi dùng chung một khung. Kéo thanh nắm trên đỉnh bảng thì bảng cao lên / thấp xuống theo ngón tay, video co giãn theo, thả tay tự về mốc đóng / mở mặc định / mở hết cỡ. Nội dung bên trong vẫn cuộn bình thường. Từ menu bấm "Thông tin mô tả" hoặc "Bản ghi" là đổi bảng ngay.
- **Phụ đề**: lấy các bản phụ đề `/player` liệt kê (`captionTracks`), chọn ở menu ba chấm → Phụ đề; tải từ timedtext của YouTube bằng OkHttp (HTTP/2, UA như meetube, IPv4 xếp trước IPv6), thử `json3`, `vtt`, XML; gặp 429 thì chờ rồi nghỉ 20 giây. Không có tính năng dịch phụ đề. Lựa chọn nhớ qua các Short (`ShortsChrome.captionKey`).
- **Bản ghi** (theo `TranscriptSheet` của meetube): liệt kê từng dòng phụ đề kèm mốc thời gian, dòng đang nói sáng lên và tự cuộn theo (kéo tay thì nghỉ 4 giây), chạm một dòng để phát từ đó, ô tìm kiếm lọc dòng khớp. Dùng bản phụ đề đang chọn, chưa chọn thì lấy bản của người đăng, không có thì bản tự tạo.
- **Tốc độ phát**: 0.25x đến 2x (`ShortsPool.selectSpeed`), áp cho mọi ô nên Short kế vẫn giữ tốc độ.
- **Tự động vuốt** (theo "Auto-scroll" của meetube): bật ở menu thì Short hết là tự trượt sang Short kế, tắt thì lặp lại như YouTube. Đang mở bảng hay bình luận thì không tự vuốt.

## Rời app khi đang xem Short (viết lại theo meetube)
Toàn bộ logic nằm ở `ShortsBackground.kt` + `ShortsPlaybackService.kt`, `MainActivity` chỉ gọi đúng chỗ (theo `MobilePlaybackActivity`, `MobilePlaybackService`, `BackgroundPlay` của meetube). Đã xóa cửa sổ nổi tự vẽ (`PipOverlay`, quyền SYSTEM_ALERT_WINDOW) và `ShortsAudioService` cũ.
- **onUserLeaveHint / onPause**: Short đang phát thì bật dịch vụ media nền ngay lúc app còn hiện (Android 12+ cấm bật từ lúc đã ẩn), rồi vào **PiP hệ thống** nếu cài đặt "Tự vào PiP khi rời app" bật (Android 12+ còn có auto-enter, chỉ bật khi app đang ở trước mặt và Short đang phát). Cửa sổ PiP là chính bộ phát Short, có nút Trước / Phát-Tạm dừng / Tiếp.
- **onStop mà không ở PiP** (tắt màn hình, sang app khác, PiP tắt hoặc bị từ chối): Short đang phát thành *phiên nền* (`ShortsMini.beginBackground`), chỉ phát âm thanh (tắt luồng hình), thông báo media + màn khóa điều khiển được. Compose không chạy lúc app ẩn nên phiên nền tự nạp video kế, chuyển video, tự vuốt (`ShortsPool.endedListener`).
- **Hiện lại app**: phiên nền trả về bộ phát toàn màn hình, tự cuộn tới đúng Short đang phát (`PipState.returnId`).
- **Đóng PiP (X) / nút Đóng ở thông báo / thoát bằng Back**: dừng hẳn (`ShortsBackground.stopAll`), không để âm thanh chạy mồ côi.
- **Pin**: sau lần phát nền đầu tiên, app xin miễn tối ưu pin một lần (`BackgroundPlay.askOnce`); bật lại ở Cài đặt → Thử nghiệm các tính năng mới.
- Short tạm dừng thì rời app không phát nền.

## Main và Search (kiểu YouTube)
- **Main**: thanh trên có logo NaNTube (ảnh `nantube_logo`, nền trong suốt) + nút kết nối TV + chuông thông báo (số đỏ = chưa xem) + nút tìm kiếm, hàng chip chủ đề (Tất cả, Âm nhạc, Trò chơi, Tin tức, Thể thao, Live), thẻ video lớn 16:9 có avatar kênh, tiêu đề 2 dòng, nút ba chấm. Cuộn gần cuối tự tải trang kế. "Tất cả" = trang chủ YouTube (chưa đăng nhập thì lấy tạm kết quả tìm "thịnh hành"); các chip còn lại là tìm kiếm theo chủ đề (`HOME_CHIPS` trong `Videos.kt`, muốn đổi chủ đề chỉ sửa danh sách này).
- **Ký hiệu nhạc ♪**: `DurationBadge` vẽ ♪ trước thời lượng ở mọi nơi có ảnh bìa (Main, Search, Info, lịch sử). Video được coi là nhạc khi YouTube gắn icon MUSIC trên ảnh bìa, kênh "- Topic"/VEVO, hoặc đang ở chip Âm nhạc (`VideoItem.isMusic`).
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
- **Tải xuống**: `DownloadService` tải video + audio H.264/AAC theo từng đoạn 10 MB, ghép bằng MediaMuxer (`MediaJoiner`, theo `MediaMuxHelper` của meetube) rồi lưu vào thư mục Tải xuống. Link lấy theo thứ tự của meetube (xem `PlaybackSource`). Body /player dựng như `QueryBuilder` của meetube (clientScreen, userAgent, browser, vùng, `visitorData` lấy từ `youtubei/v1/visitor_id` như `VisitorService`, cpn, racyCheckOk/contentCheckOk, URL không `key`); tải googlevideo bằng đúng UA media của meetube (`MEDIA_USER_AGENT`). Mỗi link được hỏi thử 2 byte đầu; bị googlevideo từ chối (403) thì đổi client và lấy link mới, giống `DownloadJob`. Khi không client nào dùng được, thông báo lỗi kèm lý do của YouTube (vd. LOGIN_REQUIRED) để dễ chẩn đoán.

## Music
Tab Music dùng InnerTube của YouTube Music (WEB_REMIX, theo `MusicApi` của meetube): trang chủ, tìm bài hát, mở album/danh sách/nghệ sĩ. Đăng nhập bằng "Tự nhập Gmail" thì trang chủ là của tài khoản; đăng nhập bằng mã thì xem như khách. Bấm bài hát sẽ mở music.youtube.com.

## Trang Info
- Hàng "Video đã xem": tối đa 20 video, hiện 4 cái đầu, mỗi lần vuốt tới cuối hàng hiện thêm 4 (logic theo hàng History ở trang You của meetube, `MobileBrowseActivity.showYouHistory`). Bấm tiêu đề "Video đã xem" để mở trang lịch sử đầy đủ (tìm kiếm, chip lọc, nhóm theo ngày, tự tải trang kế khi vuốt xuống).
- Ảnh bìa "Video đã thích", "Xem sau", "Video của bạn" là video đầu tiên của từng danh sách (playlist LL, WL và uploads của kênh).
- Mục "Danh sách Music" nằm ngay dưới "Nội dung đã tải": một danh sách phát riêng tư tên "Music" trên YouTube (`MusicList`), tự tìm theo tên hoặc tạo một lần, nên cũng hiện trong bảng "Lưu vào…". Chip "Âm nhạc" luôn giữ mục này.
- Mục "Nội dung đã tải" (video tải về xem khi không có mạng) mới có giao diện, chưa có chức năng tải; sẽ làm theo bộ tải của meetube (`DownloadRegistry`, `DownloadStorage`, `DownloadJob`).
- Chip "Gần đây" mở bảng sắp xếp Gần đây / A-Z / Z-A. Chip Đã tải xuống / Danh sách phát (Video đã thích + Xem sau) / Âm nhạc lọc các mục thư viện, chạm lại để bỏ lọc. "Âm nhạc" lấy các danh sách có video nhạc, đoán qua kênh "- Topic" hoặc tiêu đề MV / lyrics / remix (`VideoItem.isMusic`).

## Popup / phát nền cho cả video dài (Cài đặt → Thử nghiệm các tính năng mới)
Ba công tắc áp dụng cho **cả Short lẫn video dài** (màn xem `WatchScreen` và thẻ nổi `WatchMini`):
- **Xem video dạng popup**: bật thì Hình trong hình ở menu video dài thu video vào thẻ nổi, rời app thì thành PiP; tắt thì bấm Hình trong hình sẽ mở trang cài đặt này.
- **Hỗ trợ phát nền video**: bật thì video dài cũng phát tiếp khi tắt màn hình / sang app khác. Dùng chung `ShortsPlaybackService` + `ShortsBackground` với Short: app ẩn thì `WatchPlayback.setAudioOnly(true)` (tắt luồng hình), thẻ media có Phát/Tạm dừng và hai nút lùi / tua 10 giây, hiện lại app thì bật hình lại. Tắt thì rời app là dừng.
- **Chỉ phát nền khi có popup** (`PipPrefs.onlyWithPopup`, mặc định tắt): bật thì Short / video chỉ phát nền khi đang ở popup (thẻ nổi, hoặc PiP lúc vừa rời app). Đang xem toàn màn hình mà ẩn app không vào được PiP thì dừng, không phát nền.

## Version
Sửa duy nhất file `version.txt` (dạng `00.00.01`). Tên version và versionCode đều lấy từ đây.
Mỗi lần ra bản mới nhớ tăng số, đừng giảm, nếu không máy sẽ không cho cập nhật đè.

## Build
Push lên nhánh `main`, GitHub Actions tự build và đưa APK vào mục Releases (và Actions > Artifacts).

## Ký app
APK ký v1 + v2 + v3 bằng `keystore/nantube.p12`.
Giữ nguyên file này mãi mãi, đổi key là phải gỡ app cài lại.

## Lịch sử xem Short và mở Short từ danh sách
- **Ghi lịch sử** (`WatchHistory`, theo `TrackingService` của MediaServiceCore): Short lên hình ~2,5 giây thì app lấy `playbackTracking` từ /player của chính tài khoản (phản hồi ẩn danh VISIONOS / ANDROID_VR không gắn với tài khoản nên YouTube không ghi) rồi gọi `stats/playback` + `stats/watchtime` (final=1) như `createWatchRecordShort` / `updateWatchTimeShort`. Trước đây app không gửi ping nào nên Short xem trong app không vào "Video đã xem" của YouTube.
- **Mở Short từ danh sách** (Xem sau, Video đã thích, Video đã xem, hàng Video đã xem ở Info): `openVideo(context, video, queue)` dựng hàng đợi từ các Short của chính danh sách đó, đứng đúng Short được bấm (như `queueShorts` / `queueHistoryShorts` của meetube), rồi mới nối Short đề xuất khi vuốt gần hết. Trước đây chỉ truyền đúng 1 video; hàng Video đã xem ở Info còn mở Short bằng trình duyệt.

## Tim video và dấu "đã xem"
- **Nút tim ở Short** (`ShortsPlayback.likeStatus`): không còn nhớ kết quả cũ. Mỗi lần một Short thành Short đang xem (đổi trang, về lại tab, mở lại app) app hỏi lại YouTube bằng `next` của tài khoản và đọc `likeStatus` theo đúng các chỗ MediaServiceCore (`MediaItemMetadataImpl.likeStatusItem`) đọc: `videoMetadataRenderer.likeStatus`, `likeButtonRenderer.likeStatus`, nút Thích trong `transportControlsRenderer` (`isToggled`), cùng các dạng của client WEB mới. Bấm tim thì đổi ngay trên màn hình.
- **Dấu đã xem cho Short**: YouTube không gửi thanh tiến độ cho Short, nên `VideoItem.watchedProgress()` dùng `ShortsSeen` (Short đã lên hình ở bất cứ đâu trong app, nhớ 7 ngày) để vẽ thanh đỏ đầy ở Main / Tìm kiếm / Info / danh sách / lưới Short.

## Nhận diện Short trong mọi danh sách (Video đã thích, Xem sau, Video của bạn, Video đã xem, Tìm kiếm)
Thẻ TV của Short dài (≤ 3 phút, vd 2:55) không có huy hiệu SHORTS, còn các cách đoán (thời lượng, "#", ảnh bìa, chuyển hướng /shorts/) đều nhận nhầm video ngang và nhạc thành Short. Nên không đoán nữa:
- **Dấu hiệu của YouTube** (`Videos.tile/classic/lockup`, như `TileItem.isShorts` của MediaServiceCore): `reelWatchEndpoint`, `style = TILE_STYLE_YTLR_SHORTS`, huy hiệu SHORTS, `contentType`.
- **Video ≤ 3 phút chưa có dấu hiệu nào**: `ShortDetector` hỏi `/player` (qua `PlaybackSource.walk`, đúng như trình phát Short) và đọc kích thước thật của luồng video. Short = thời lượng ≤ 180 giây VÀ cao ≥ rộng (định nghĩa của YouTube). Video ngang và nhạc không bao giờ khớp. Không đọc được thì để trống, thử lại sau 60 giây. Kết quả nhớ vào máy (`SharedPreferences short_detector`).
- **Chỗ chung**: `VideoRepository.historyPage / continuePage / playlistPage / searchPage / searchMore` đều gọi `ShortDetector.resolve` trước khi trả trang (tối đa 4 giây mỗi trang, phần còn lại quét nền bằng `HistoryFeed.classifyShorts`), nên mọi màn dùng chúng đều có kết quả.
- **Chạm vào item chưa phân loại** (`openVideo`): hỏi nhanh (tối đa 3 giây) rồi mới mở trình phát dọc hay link ngoài.
- **"Đang tải…"** chỉ hiện khi thật sự đang tải; trang không thêm video mới thì dừng tải tiếp.
- Chẩn đoán: logcat, tag `ShortDetector`, mỗi video một dòng (client, trạng thái, thời lượng, kích thước, kết luận).

## Lịch sử "Video đã xem" tải vô hạn (theo meetube)
- **Token trang kế** (`VideoRepository.findContinuation`): chọn token của cả danh sách theo đường dẫn (`sectionListRenderer` / `gridRenderer` / `*Continuation` → `continuations`, tầng nông nhất) như `BrowseResultTV.getContinuationToken` của MediaServiceCore; token `reloadContinuationData` (tải lại trang, nằm cuối `tvSurfaceContentRenderer`) chỉ dùng khi không còn token nào khác. Trước đây lấy "token cuối theo thứ tự tài liệu" nên có thể dính token reload và lịch sử chỉ còn vài video đầu.
- **Trang rỗng không làm dừng** (`HistoryFeed.loadMore`, theo `ShelfTail` của meetube): trang không thêm được video (toàn trùng/Short bị lọc/rỗng) thì đi tiếp trang sau, tối đa 10 trang liền nhau; token lặp lại đúng cái vừa dùng mới là hết.

## Màn nghe giọng nói: size/vị trí/animation (đo từ video YouTube)
Nút micro đỏ 80dp đứng yên, tâm cách đáy 180dp; khuôn mặt 90x102dp thẳng hàng với tâm nút. Phía sau nút là hình tròn đặc xám `#2C2B2B` lớn dần theo âm lượng (tối đa ~2,2 lần bán kính nút); khi im thì có vòng viền mỏng loe ra rồi mờ dần (~0,7 giây, lặp lại).

## Kết quả tìm kiếm: nhạc và danh sách phát (v00.00.35)
- **Nhạc ♪ ở Main và Search**: ngoài icon ♪ YouTube gắn sẵn (thẻ TV của tài khoản mã giờ cũng đọc icon này), app hỏi YouTube Music (`MusicDetector.classify`) cho các video 30 giây–20 phút chưa rõ, bài nào là nhạc thì gắn ♪ dần trên thẻ. Ảnh bìa vuông 1:1 mà YouTube trả cho đĩa nhạc / bài hát (`VideoItem.artUrl`) được vẽ vuông như YouTube.
- **Danh sách phát / đĩa nhạc** (`PlaylistCard` ở `SearchScreen.kt`): bìa xếp chồng có nhãn "N video", tên, kênh · Danh sách phát, rồi khung "N video trong danh sách phát" (thu gọn được) có hàng video vuốt ngang (`VideoRepository.playlistPreview`). Thẻ TV loại playlist trước đây bị đọc thành một video nên chỉ hiện mỗi ảnh; nay đọc đúng thành danh sách (`tilePlaylist`), đĩa nhạc (LOCKUP_CONTENT_TYPE_ALBUM) cũng được nhận.

## Sửa lỗi 00.00.38 (theo meetube / MediaServiceCore)
- **Tài khoản web không lấy được Nhật ký tìm kiếm / Video đã xem**: `InnerTube.call` giờ gọi bằng phiên web đúng như `WebSession.post` của meetube (client WEB hiện tại + visitorData + UA desktop + chữ ký đủ SAPISIDHASH/SAPISID1PHASH/SAPISID3PHASH). Trước đây chỉ gửi một băm với client cũ nên YouTube coi phiên như ẩn danh. `webAuthHeaders` (gợi ý, Music, thống kê) cũng đủ ba băm.
- **Nhật ký tìm kiếm**: tài khoản web lấy từ `suggestqueries-clients6.youtube.com` (kèm cookie); đồng thời luôn lưu nhật ký trong máy theo từng tài khoản và gộp hai nguồn, nên mục Nhật ký không còn trống.
- **Music không hiện nhạc**: bộ đọc không đọc `itemSectionRenderer` (kết quả tìm kiếm nằm ở đó), `musicMultiRowListItemRenderer`, `musicCardShelfRenderer`, `gridRenderer`, `playlistItemData`; thêm header `X-Origin`; phiên web bị từ chối thì rơi về khách thay vì trống.
- **Tìm kiếm không ra bài lẻ**: Music tìm gộp Bài hát + Video + kết quả tổng hợp (nghệ sĩ/album/danh sách phát). Search chính ("Tất cả") nếu ít hơn 8 video ở trang đầu thì bổ sung từ bộ lọc "Video"; phiên đăng nhập lỗi thì tìm lại như khách.
- **Video đã xem**: nhạc phát trong app (`MusicPlayer`) giờ ghi vào lịch sử của tài khoản như Short.

## Sửa lỗi 00.00.39
- **Tài khoản mã**: tìm kiếm / chip Âm nhạc ở Main hỏi bằng mẫu WEB + Bearer (rồi khách WEB) vì kết quả client TV chỉ có danh sách phát, thiếu video lẻ và nhạc.
- **Tài khoản web ghi lịch sử**: /player lấy `playbackTracking` thử WEB (kèm signatureTimestamp) rồi MWEB; stats gửi kèm `c`/`cver` theo URL YouTube trả.

## 00.00.40: mở danh sách phát của người khác
Chạm bìa hoặc tên của thẻ danh sách phát (Tìm kiếm, trang chủ) mở trang danh sách ngay trong app (`PlaylistViewer` + `PlaylistScreen`, khóa `pl:<id>`): đủ video, tải tiếp khi vuốt, chip Tất cả/Video/Shorts/Âm nhạc, nút Phát tất cả. Khách cũng mở được. Back hoặc đổi tab thì đóng.

## 00.00.41
Danh sách kết hợp / tuyển tập / mix (mã RD…) mở được: không có trang danh sách nên đọc qua `next` (playlistPanelRenderer) khi trang thường trống.

## 00.00.42
Bỏ đoán nhạc theo tiêu đề (dễ bị bait). Nhạc nay chỉ nhận qua icon ♪ của YouTube, kênh "- Topic"/VEVO, chip Âm nhạc, hoặc MusicDetector hỏi YouTube Music.

## 00.00.43
Thanh nhạc nhỏ (MiniMusicBar) chỉ hiện ở tab Music, không chiếm chỗ ở Main/Short/Search/Info.

## Nút trên trình phát video dài (WatchScreen)
Hàng nút phía trên video, từ trái sang: thu nhỏ | công tắc tự phát | truyền lên TV | CC | bánh răng. Mọi cài đặt ở đây nằm trong `WatchPrefs` (`data/WatchPrefs.kt`), lưu theo từng tài khoản qua `NanStorage`: `NaNTube/accounts/<id tài khoản>/watch_settings.json`, chưa đăng nhập thì `NaNTube/guest/watch_settings.json`; đổi tài khoản thì nạp lại.
- **Công tắc tự phát** (mặc định bật): núm trắng + ▶ ở bên phải khi bật, núm xám + ⏸ ở bên trái khi tắt, có dòng "Tự động phát đang bật / tắt" như YouTube. Bật thì hết video tự phát đề xuất đầu tiên; nút "Sau" ở giữa cũng sang video đó. Hẹn giờ "Cuối video" thắng tự phát (dừng hẳn).
- **Nút cast**: mở `TvConnectScreen` có sẵn với video đang xem (đã ghép TV thì hỏi "Phát video này trên …?", chưa thì nhập mã).
- **CC**: bấm bật / tắt phụ đề; bật thì icon thành CC đặc (`WatchCcOn`), tắt là viền (`WatchCc`). Bật lại đúng bản gần nhất, chưa có thì bản của người đăng (`ShortsCaptions.defaultTrack`). Phụ đề tải bằng `ShortsCaptions` như Short, vẽ dưới giữa video; video không có phụ đề thì báo. Lựa chọn nhớ qua các video, theo tài khoản.
- **Bánh răng** (`WatchSettings.kt`): Chất lượng (ép bằng `TrackSelectionOverride`, các mức ≤ 720p mà `PlayPlan` dựng) · Tốc độ phát 0.25x–2x · Phụ đề · **Tuỳ chọn khác**: Cho video lặp lại, Chế độ môi trường xung quanh (theo `AmbientGlow` của meetube: mỗi 2 giây đọc khung 48x27 từ TextureView, phóng to + đậm màu làm nền dưới video), Âm lượng ổn định (theo `AudioLoudness` của meetube: `playerConfig.audioConfig.loudnessDb`, gain = min(1, 10^(-dB/20)), chỉ hạ không tăng), Hẹn giờ ngủ (theo `SleepTimer` của meetube: 10/15/20/30/45/60/90 phút hoặc cuối video, chỉ trong phiên). Đã bỏ: Khóa màn hình, Trên đường đi (Premium), Xem ở chế độ thực tế ảo, Trợ giúp và phản hồi.

## 00.00.63: Cài đặt → Nội dung tải xuống, giữ vị trí cuộn của Cài đặt
- **Màn "Nội dung tải xuống"** (`DownloadSettingsScreen.kt`, bố cục như YouTube, dùng chung `YtSettingsCommon`): *Chất lượng tải xuống* (Hỏi mỗi lần / 1080p / 720p / 480p / 360p / 240p / 144p), *Chỉ tải xuống qua Wi-Fi*, *Trợ giúp tải xuống* (mở trang trợ giúp của YouTube), *Xóa tất cả video tải xuống* (có hộp xác nhận), và mục *Dung lượng còn trống* (thanh xanh = dung lượng video app đã tải, phần xám = còn trống của máy). Mục "Đề xuất video tải xuống" của YouTube bị bỏ vì app không có tính năng tự đề xuất / tự tải.
- **Lưu cài đặt** ở `DownloadPrefs` (`data/DownloadPrefs.kt`), theo từng tài khoản qua `NanStorage` (`download_settings.json`).
- **Áp vào bảng Tải xuống** (`DownloadSheet.kt`, cả bảng tải một video và bảng tải cả danh sách phát): mức đồ họa đặt sẵn được chọn sẵn (video không có mức đó thì lấy mức gần nhất thấp hơn, trang "Đồ họa" vẫn đổi được cho riêng lần tải đó); bật "Chỉ tải xuống qua Wi-Fi" mà đang dùng mạng di động thì báo và không tải.
- **Xóa tất cả / dung lượng**: `VideoDownloads.deleteAll` và `VideoDownloads.usedBytes`, cùng điều kiện lọc với danh sách "Nội dung đã tải".
- **Sửa lỗi**: ra khỏi một trang con của Cài đặt (Chất lượng, Phát, Tiết kiệm dữ liệu, Nội dung tải xuống, Thử nghiệm...) về lại Cài đặt không còn nhảy lên đầu danh sách. Nguyên nhân: `LazyListState` nằm trong `LazyColumn` bị gỡ khỏi cây khi mở trang con; nay nằm ở đầu `SettingsScreen`, trên các lệnh `return`.

## 00.00.64: Cài đặt → Hỗ trợ tiếp cận (trình phát hỗ trợ tiếp cận)
- **Màn "Hỗ trợ tiếp cận"** (`AccessibilitySettingsScreen.kt`): công tắc *Trình phát hỗ trợ tiếp cận* và mục *Ẩn các nút điều khiển trình phát* (Không bao giờ / 3 / 5 / 10 giây; mờ và không bấm được khi công tắc tắt). Lưu trong `WatchPrefs` (`a11yPlayer`, `hideControlsSeconds`), theo từng tài khoản.
- **Khi bật** (`WatchPlayerArea` ở `WatchScreen.kt`): hai nút tua lùi / tiến ở giữa trình phát (cạnh Trước / Sau, số giây theo Cài đặt → Phát → "Nhấn đúp để tua"); không còn nhấn đúp để tua; chạm vào video lúc nút đang hiện thì không làm gì, chỉ nút **X** ở góc trên bên phải mới ẩn nút; nút đang ẩn thì chạm để hiện lại. Tự ẩn theo mức đã chọn (Không bao giờ = không tự ẩn).

## 00.00.65: tự động tắt trình phát hỗ trợ tiếp cận
Cài đặt → Hỗ trợ tiếp cận có thêm mục *Tự động tắt trình phát hỗ trợ tiếp cận*: Không bao giờ (mặc định) / 1 / 3 / 7 / 30 ngày kể từ lúc bật công tắc (`WatchPrefs.a11yAutoOffDays`, mốc bật `a11yEnabledAt`). Quá hạn thì công tắc tự tắt (`WatchPrefs.checkA11yExpiry`, kiểm tra khi nạp cài đặt, khi mở màn Hỗ trợ tiếp cận và khi trình phát dựng lên). Đổi mức thì đếm lại từ lúc đổi; tắt công tắc rồi bật lại cũng đếm lại.

## 00.00.66: thanh tiến trình video dài và nhấn đúp
- **Thanh tiến trình** (`WatchSeekBar`): chỉ kéo được khi chạm trúng chấm tròn (vùng bắt 24dp mỗi bên); chạm vào thanh không còn tua. Kéo theo độ dịch của ngón tay nên chấm không nhảy về chỗ chạm. Nút đang ẩn thì thanh không bắt cử chỉ, chạm xuống đây rơi xuống video để hiện lại nút. Thanh tua của Short giữ nguyên.
- **Đang kéo thì nút không tự ẩn**: `WatchSeekBar.onDragging` → `seekDragging` → `WatchPlayerArea.seeking`; thả tay xong mới đếm lại thời gian tự ẩn.
- **Nhấn đúp để tua**: thời gian chờ nhấn tiếp để cộng dồn giảm từ 0,8 giây còn 0,5 giây (`DoubleTapSeek.COMBO_MS`).
