# LemonadeX Khách hàng

Cửa hàng thời trang của LemonadeX: trang chủ, danh sách sản phẩm có bộ lọc, chi tiết sản phẩm, giỏ hàng, thanh toán và đơn hàng của khách. Toàn bộ dữ liệu đọc từ backend (xem [API cửa hàng](../docs/storefront.md)), không dùng dữ liệu giả.

Chạy backend theo [README gốc](../README.md), sau đó ở thư mục `frontend-user`:

```powershell
npm.cmd ci
npm.cmd run dev
```

Mở http://localhost:3000.

| Đường dẫn | Trang |
| --- | --- |
| `/` | Trang giới thiệu shop: hero ảnh (banner `INTRO_HERO`), câu chuyện (banner `INTRO_STORY`), danh mục, số liệu, đánh giá của khách, địa chỉ cửa hàng |
| `/shop` | Trang chủ cửa hàng: banner `HOME_HERO`, hàng mới về, danh mục, Lookbook, phương thức giao hàng/thanh toán |
| `/shop/products` | Danh sách sản phẩm; bộ lọc nằm trên URL (`category`, `brand`, `gender`, `size`, `color`, `min`, `max`, `inStock`, `sort`, `search`, `page`) |
| `/shop/products/:slug` | Chi tiết sản phẩm, chọn màu/size theo tồn kho thực, bảng size, "Báo khi có hàng" cho size đã hết, sản phẩm đã xem |
| `/shop/cart` | Giỏ hàng (lưu ở trình duyệt; khi đăng nhập được lưu cả trên server để dùng trên thiết bị khác) |
| `/shop/checkout` | Thanh toán; khách chưa đăng nhập đặt hàng bằng email và số điện thoại |
| `/shop/orders`, `/shop/orders/:id` | Cần đăng nhập; trang chi tiết có thanh toán online lại và yêu cầu đổi/trả |
| `/shop/payment/vnpay`, `/shop/payment/momo` | Kết quả khi quay về từ cổng thanh toán |
| `/shop/articles`, `/shop/articles/:slug` | Tạp chí: bài viết và Lookbook đã xuất bản |
| `/shop/login`, `/shop/register`, `/shop/verify-email` | Đăng nhập (kể cả Google/Facebook khi backend bật), đăng ký, xác nhận email |
| `/policies/:type` | Chính sách cửa hàng đang áp dụng |

Muốn có dữ liệu để xem thử, chạy [seed dữ liệu mẫu](../docs/demo-seed.md).

Nội dung quản lý trong trang admin: ảnh hero là banner vị trí `INTRO_HERO` (trang giới thiệu) và `HOME_HERO` (trang chủ cửa hàng), ảnh câu chuyện là banner `INTRO_STORY` (Marketing > Banner), Lookbook là bài viết loại Lookbook đã xuất bản, thông tin liên hệ ở chân trang lấy từ Cài đặt > Thông tin cửa hàng. Khi chưa có sản phẩm, trang hiển thị trạng thái "đang cập nhật sản phẩm".

Đăng nhập gọi `POST /api/v1/auth/login`, lưu `data.accessToken` tại `lemonadex.token`; tải lại trang xác thực qua `GET /api/v1/auth/me`. Đăng xuất hoặc lỗi 401 xóa token. Giao diện sáng/tối theo hệ thống, có nút chuyển và lưu lựa chọn tại `lemonadex.theme`.

Vite chuyển `/api` tới `http://localhost:8080`. Có thể sao chép `.env.example` thành `.env.local` rồi đổi `VITE_API_PROXY_TARGET`. `VITE_API_BASE_URL` để trống khi dùng proxy; nếu gọi backend trực tiếp, đặt origin backend và cho phép origin frontend trong `CORS_ALLOWED_ORIGINS`. Bản build là một SPA: reverse proxy chuyển `/api` tới backend và trả `index.html` cho mọi đường dẫn khác.

Ô "Tìm địa chỉ nhanh" ở trang thanh toán gợi ý địa chỉ khi khách gõ và có nút "Vị trí của tôi". Mặc định dùng Photon (dữ liệu OpenStreetMap, miễn phí, không cần key), ưu tiên kết quả gần TP. Hồ Chí Minh. Để dùng Google Maps, đặt `VITE_GOOGLE_MAPS_API_KEY` (bật Places API (New) và Geocoding API, giới hạn key theo domain của shop); Google tính phí theo lượt gọi. Khách luôn sửa tay được các ô địa chỉ, và có link mở địa chỉ đã nhập trên Google Maps để kiểm tra.

Build: `npm.cmd run build`. Các test trình duyệt đăng nhập/đăng ký của cả hai frontend được chạy từ [frontend-admin](../frontend-admin/README.md).

Trang landing cũ (chủ đề câu lạc bộ tennis) được giữ tại `legacy/tennis-landing.html` để tham khảo; nó không còn được phục vụ hay build.

Đặt hàng không cần tài khoản, đổi/trả, VNPay/MoMo, GHTK, đăng nhập Google/Facebook, giỏ hàng trên server, gợi ý tìm kiếm, báo có hàng và nút chat được mô tả trong [`docs/storefront-extensions.md`](../docs/storefront-extensions.md); các khóa đặt ở backend, frontend không cần biến môi trường mới.

Tài khoản khách, sổ địa chỉ, yêu thích, thông báo, đánh giá, bộ sưu tập, tra cứu đơn, VietQR, SEO và theo dõi lỗi (Sentry, GA4) được mô tả trong [`docs/customer-self-service.md`](../docs/customer-self-service.md). Biến môi trường tùy chọn: `VITE_GOOGLE_MAPS_API_KEY`, `VITE_SENTRY_DSN`, `VITE_GA_MEASUREMENT_ID` (xem `.env.example`).
