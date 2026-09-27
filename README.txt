TOGGLE MEANING TEST — bước 1

Mục tiêu:
- Bóng T nổi trên app khác.
- Kéo bóng đi được.
- Chạm bóng 1 lần = ON, chạm lần nữa = OFF.
- Khi ON, chạm vào text trên app khác.
- Service tìm AccessibilityNodeInfo nằm dưới tọa độ chạm.
- Nếu app cung cấp character locations, APK cố lấy ĐÚNG WORD tại vị trí chạm.
- Kết quả hiện thành bong bóng nhỏ gần điểm chạm.
- Sau đó APK replay lại tap để app gốc vẫn nhận thao tác.

LƯU Ý VỀ ĐỘ CHÍNH XÁC:
- Dấu ✓ = lấy từ character-location data của Accessibility, đây là đường chính xác.
- Dấu ~ = fallback ước lượng từ text + bounds khi app không cung cấp character locations.
- Nếu hiện "no accessible text", app đó không expose text ở điểm chạm cho Accessibility.

BUILD:
- Đây là Gradle Android application project dạng FLAT.
- ZIP chỉ chứa file ở 1 cấp, không chứa thư mục con.
- Cần Android Studio / AndroidIDE có Gradle + Android SDK.
- compileSdk = 35, minSdk = 26.
- Project cố ý không dùng AndroidX để giảm phụ thuộc.

CÁCH TEST:
1. Build/install debug APK.
2. Mở app Toggle Meaning Test.
3. Mở Accessibility Settings.
4. Bật service "Toggle Meaning Test".
5. Quay ra ChatGPT hoặc trình duyệt.
6. Bong bóng T xuất hiện.
7. Chạm T -> màu xanh = ON.
8. Chạm vào một word.
9. Nhìn popup cạnh điểm chạm.
10. Chạm T lần nữa -> OFF.

Bước 2 sau khi bản test này xác nhận lấy word ổn: thay popup bằng dictionary/meaning toggle.
