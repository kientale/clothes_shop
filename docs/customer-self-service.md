# Tài khoản khách hàng và các tiện ích mua hàng

Tài liệu cho các chức năng khách tự phục vụ: quên mật khẩu, hồ sơ, sổ địa chỉ, yêu thích, thông báo, đánh giá, bộ sưu tập, tra cứu đơn không cần đăng nhập, chuyển khoản bằng VietQR, email xác nhận đơn, giới hạn tần suất, SEO và theo dõi lỗi.

## API

| Nhóm | Endpoint | Ghi chú |
| --- | --- | --- |
| Quên mật khẩu | `POST /api/v1/auth/password/forgot` | Công khai. Luôn trả 202 dù email có tài khoản hay không. Gửi liên kết dùng một lần, hết hạn sau 30 phút; tối đa 3 liên kết mỗi giờ cho một tài khoản. |
| | `POST /api/v1/auth/password/reset` | Công khai. `INVALID_RESET_TOKEN` khi liên kết sai, đã dùng hoặc hết hạn. Đặt mật khẩu xong, mọi liên kết còn lại của tài khoản bị vô hiệu. |
| Hồ sơ | `GET/PUT /api/v1/me/profile` | Họ tên, số điện thoại, giới tính, ngày sinh. Email không đổi được. |
| Đổi mật khẩu | `PUT /api/v1/me/password` | Cần mật khẩu hiện tại (`CURRENT_PASSWORD_INCORRECT`). |
| Sổ địa chỉ | `GET/POST /api/v1/me/addresses`, `PUT/DELETE /api/v1/me/addresses/{id}`, `POST /api/v1/me/addresses/{id}/default` | Tối đa 10 địa chỉ. Địa chỉ đầu tiên tự thành mặc định; xóa địa chỉ mặc định thì địa chỉ cũ nhất còn lại thành mặc định. Quận/huyện không bắt buộc (cải cách hành chính 2025). |
| Yêu thích | `GET /api/v1/me/wishlist`, `GET /api/v1/me/wishlist/ids`, `PUT/DELETE /api/v1/me/wishlist/{productId}` | Lưu trên server, theo tài khoản. Tối đa 200 sản phẩm; chỉ sản phẩm đang bán. |
| Thông báo | `GET /api/v1/me/notifications`, `PUT /api/v1/me/notifications/{id}/read` | Đã có từ trước; web khách hiển thị chuông và trang Thông báo. |
| Đánh giá | `GET/POST /api/v1/me/product-reviews` | Đã có từ trước; web khách cho đánh giá từng sản phẩm của đơn đã giao. Đánh giá hiện công khai sau khi admin duyệt. |
| Bộ sưu tập | `GET /api/v1/store/collections`, `GET /api/v1/store/collections/{slug}` | Công khai. Chỉ bộ sưu tập đang bật và trong thời gian hiển thị. |
| Tra cứu đơn | `POST /api/v1/store/orders/lookup` | Công khai. Cần cả mã đơn và số điện thoại trên đơn (chỉ so chữ số). Tên người nhận bị che, chỉ hiện phường và tỉnh của địa chỉ. |
| Sitemap | `GET /api/v1/store/sitemap.xml` | Trang cố định, sản phẩm đang bán và bộ sưu tập, theo `STOREFRONT_URL`. |
| Cấu hình | `GET /api/v1/store/configuration` | Phương thức thanh toán chuyển khoản nay kèm thông tin ngân hàng để khách chuyển tiền và tạo mã VietQR. |

Chi tiết từng trường nằm trong `docs/openapi.json`.

## Email

Backend gửi email đặt lại mật khẩu và email xác nhận đơn (sản phẩm, tổng tiền, địa chỉ, thông tin chuyển khoản nếu có). Email chỉ gửi sau khi giao dịch đã lưu, chạy nền nên không làm chậm yêu cầu; gửi lỗi chỉ ghi log.

```properties
MAIL_HOST=smtp.gmail.com        # để trống: chỉ ghi email ra log (môi trường dev)
MAIL_PORT=587
MAIL_USERNAME=...
MAIL_PASSWORD=...               # Gmail: dùng App Password
MAIL_FROM=no-reply@lemonadex.vn
STOREFRONT_URL=https://lemonadex.vn
```

Khi `MAIL_HOST` trống và `STOREFRONT_URL` là localhost, nội dung email (kể cả liên kết đặt lại mật khẩu) được in ra log để thử ở máy dev. Với địa chỉ khác localhost, log chỉ ghi người nhận và tiêu đề.

## Giới hạn tần suất

| Endpoint | Giới hạn mỗi địa chỉ IP |
| --- | --- |
| Đăng nhập | 10 lần / phút |
| Đăng ký | 5 lần / 10 phút |
| Quên mật khẩu | 5 lần / 15 phút |
| Đặt lại mật khẩu | 10 lần / 15 phút |
| Đặt hàng | 10 lần / 10 phút |
| Tra cứu đơn | 10 lần / 5 phút |

Vượt giới hạn trả `429 TOO_MANY_REQUESTS` kèm `Retry-After`. Bộ đếm nằm trong bộ nhớ của từng instance: đủ cho một server; chạy nhiều instance cần chuyển sang Redis. Sau reverse proxy, đặt `FORWARD_HEADERS_STRATEGY=native` để đếm theo IP thật của khách. Tắt bằng `RATE_LIMIT_ENABLED=false` (bộ test làm vậy, trừ `RateLimitIntegrationTests`).

## Web khách hàng

- `/shop/forgot-password`, `/shop/reset-password?token=...`: quên và đặt lại mật khẩu; trang đăng nhập có link "Quên mật khẩu?".
- `/shop/account`: hồ sơ, sổ địa chỉ (có ô tìm địa chỉ nhanh), đổi mật khẩu, lối tắt tới đơn hàng, yêu thích, thông báo.
- Thanh toán: chọn địa chỉ đã lưu (mặc định chọn sẵn) hoặc "Giao đến địa chỉ khác". Với địa chỉ mới, ô tìm nhanh điền sẵn và hiện thành thẻ tóm tắt; các ô chi tiết chỉ mở khi bấm "Sửa", "Nhập địa chỉ thủ công" hoặc khi thiếu thông tin. Có tùy chọn lưu địa chỉ cho lần sau.
- Trang đơn hàng: đơn chuyển khoản chưa thanh toán hiện mã VietQR (số tiền và nội dung là mã đơn đã điền sẵn), nút sao chép số tài khoản và nội dung. Đơn đã giao có nút "Đánh giá sản phẩm" cho từng món.
- Trái tim trên thẻ sản phẩm và trang sản phẩm; `/shop/wishlist`.
- Chuông thông báo trên thanh đầu trang (số chưa đọc, 5 tin mới nhất, tự kiểm tra mỗi phút) và `/shop/notifications`.
- `/shop/collections`, `/shop/collections/:slug`.
- `/shop/track`: tra cứu đơn bằng mã đơn và số điện thoại; footer có link "Tra cứu đơn hàng".

## SEO

- Mỗi trang đặt tiêu đề, mô tả, Open Graph (ảnh xem trước khi chia sẻ link Facebook/Zalo), `canonical`; trang cá nhân (giỏ hàng, thanh toán, tài khoản...) được đánh dấu `noindex`.
- Trang sản phẩm có dữ liệu cấu trúc schema.org `Product` (giá, còn hàng, điểm đánh giá).
- `public/robots.txt` chặn các trang cá nhân và trỏ tới `/sitemap.xml`. Reverse proxy cần chuyển `/sitemap.xml` tới `GET /api/v1/store/sitemap.xml` (dev server Vite đã làm sẵn). Nên đổi dòng `Sitemap:` thành địa chỉ đầy đủ của shop khi triển khai.
- Hạn chế: web là ứng dụng một trang (SPA). Google chạy JavaScript nên đọc được thẻ meta; một số trình xem trước link (Facebook, Zalo) không chạy JavaScript và chỉ thấy mô tả chung trong `index.html`. Muốn mỗi link sản phẩm có ảnh riêng khi chia sẻ thì cần prerender hoặc SSR (ví dụ một dịch vụ prerender đặt trước reverse proxy).

## Theo dõi lỗi và lượt truy cập

Trong `frontend-user/.env.local`:

```properties
VITE_SENTRY_DSN=https://...@o0.ingest.sentry.io/0   # lỗi JavaScript và lỗi render được gửi lên Sentry
VITE_GA_MEASUREMENT_ID=G-XXXXXXX                      # lượt xem trang và sự kiện mua hàng (purchase) lên GA4
```

Để trống thì không tải script nào. Lỗi render hiện trang "Đã có lỗi xảy ra" thay vì màn hình trắng.

## Ảnh sản phẩm

Admin đã tải được ảnh lên server (lưu trong bảng `uploaded_files`, phục vụ ở `/api/v1/files/{id}` với cache 1 năm). Dữ liệu mẫu dùng link Pexels; sản phẩm thật nên tải ảnh lên qua trang quản trị thay vì dán link ngoài.
