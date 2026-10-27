# VOV Radio cho đầu Android ô tô

App nghe các kênh VOV trực tuyến, giao diện ô to dễ bấm khi lái xe, tự xoay theo màn hình
(ngang cho đầu Android ô tô, dọc cho điện thoại). Cài được cả trên Android TV box.

## Tính năng
- 9 kênh VOV: VOV1, VOV2, VOV3, VOV4, VOV5, VOV Giao thông Hà Nội, VOV Giao thông TP.HCM, VOV Mekong FM90, VOV English 24/7.
- Mỗi kênh có 2–3 link; link hỏng tự chuyển link dự phòng ("đang thử link 2/3...").
- Mất sóng (vào hầm, vùng yếu) tự kết nối lại liên tục, không cần bấm.
- Phát nền khi tắt màn hình/mở bản đồ; tự nhỏ tiếng khi bản đồ đọc chỉ đường.
- Nút Next/Prev/Play trên vô-lăng và trên thanh thông báo.
- Tự phát kênh nghe lần trước khi mở app (tắt được trong menu ⋮).
- Yêu thích (♥), lọc theo nhóm, thanh âm lượng.
- Menu ⋮ → **Kiểm tra tất cả link**: chấm xanh = nghe được, đỏ = hỏng. Link sống tự đưa lên đầu.
- **Nhấn giữ một ô kênh** để sửa tên/link, hoặc xoá. Menu ⋮ → Thêm kênh mới.

## Build APK

### Cách 1 – Android Studio (máy tính)
1. Cài Android Studio (bản mới), chọn **Open** → thư mục `VOVRadioCar`.
2. Đợi Gradle tải xong (lần đầu khá lâu).
3. Menu **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. File ra ở `app/build/outputs/apk/debug/app-debug.apk`.

Dòng lệnh (cần JDK 17 + Android SDK): `./gradlew assembleRelease`
→ `app/build/outputs/apk/release/app-release.apk` (đã ký sẵn, cài được ngay).

### Cách 2 – Không cần cài gì (GitHub Actions)
1. Tạo repo GitHub mới, upload toàn bộ thư mục này lên (giữ cả thư mục `.github`).
2. Vào tab **Actions** → đợi job "Build APK" chạy xong (~5 phút).
3. Mở job → mục **Artifacts** → tải `VOV-Radio-APK` (file zip chứa APK).

## Cài lên đầu Android ô tô
1. Chép APK vào USB/thẻ nhớ, cắm vào đầu.
2. Mở trình quản lý file → bấm vào APK. Nếu bị chặn: cho phép "Cài ứng dụng không rõ nguồn gốc".
3. Đầu đời cũ (Android 5–7) nếu không vào được link https: cập nhật ngày giờ đúng, hoặc dùng link http.

## Khi link VOV bị đổi – cách lấy link mới
1. Trên máy tính, mở Chrome vào trang nghe trực tiếp của VOV (vov.vn / vovgiaothong.vn / vovlive).
2. Bấm F12 → tab **Network** → ô lọc gõ `m3u8` → bấm phát kênh.
3. Copy link `...playlist.m3u8` (hoặc `.m3u8`/`.aac`/`.mp3`) xuất hiện.
4. Trong app: nhấn giữ ô kênh → dán link vào dòng đầu → Lưu.
   Hoặc sửa `app/src/main/java/vn/vovradio/car/DefaultStations.kt` rồi build lại.
