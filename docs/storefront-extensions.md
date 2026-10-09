# Mở rộng cửa hàng

Tài liệu này mô tả các chức năng thêm ở migration V12: đặt hàng không cần tài khoản, đổi/trả do khách gửi, trang bài viết, thanh toán VNPay/MoMo, xác nhận email, đăng nhập Google/Facebook, giỏ hàng lưu trên server, bảng size, giao hàng GHTK, gợi ý tìm kiếm, sản phẩm đã xem, báo khi có hàng, ảnh trong đánh giá và nút chat. Danh sách route đầy đủ ở `docs/openapi.json`.

## Đặt hàng không cần tài khoản

| Endpoint | Mô tả |
| --- | --- |
| POST `/api/v1/store/orders` | Đặt hàng như POST `/me/orders`, thêm `email` (bắt buộc). Trả về dạng tra cứu đơn (201). Giới hạn 5 lần/10 phút mỗi client. |
| POST `/api/v1/store/orders/pay` | Khách vãng lai thanh toán online: `orderCode`, `phone`, `method` (`VNPAY`/`MOMO`). |
| POST `/api/v1/store/orders/lookup` | Tra cứu đơn; nay có thêm `paidAmount` và danh sách `payments`. |

Khách vãng lai là một bản ghi `customers` không có `account_id`, tìm lại theo số điện thoại, nên giới hạn dùng mã giảm giá theo khách vẫn áp dụng. Email được lưu ở `orders.contact_email` và hiện trong trang đơn của admin. Email xác nhận đơn chứa liên kết `/shop/track?code=...&phone=...`; trang tra cứu tự tìm đơn khi có đủ hai tham số và hiện mã QR chuyển khoản hoặc nút thanh toán online nếu đơn chưa trả.

## Đổi/trả do khách gửi

| Endpoint | Mô tả |
| --- | --- |
| GET `/api/v1/me/returns?orderId=` | Yêu cầu đổi/trả của khách. |
| POST `/api/v1/me/returns` | Gửi yêu cầu `RETURN` hoặc `EXCHANGE` cho đơn đã giao. |
| POST `/api/v1/me/returns/{id}/cancel` | Rút yêu cầu khi cửa hàng chưa xử lý (`REQUESTED`). |
| GET `/api/v1/me/orders/{orderId}/items/{itemId}/exchange-options` | Size/màu khác của cùng sản phẩm, cùng giá, kèm tồn ở kho của đơn. |

Yêu cầu đi qua `ReturnService` như khi nhân viên tạo: kiểm tra đơn đã giao, cài đặt cho phép đổi/trả, thời hạn đổi/trả, số lượng chưa trả và giá khi đổi. Ảnh đính kèm (tối đa 6) tải lên qua `POST /api/v1/me/uploads/images`. Admin duyệt tại màn Đổi/trả như trước; danh sách nay hiện ảnh khách gửi.

## Thanh toán online (VNPay, MoMo)

1. Khách chọn phương thức `VNPAY` hoặc `MOMO` khi đặt hàng; đơn có một khoản thanh toán PENDING như COD.
2. Cửa hàng gọi `POST /me/orders/{id}/pay` (hoặc `/store/orders/pay` với khách vãng lai) và chuyển trình duyệt tới `payUrl`. Nếu đơn đang có khoản PENDING của phương thức khác, khoản đó bị VOID và tạo khoản mới.
3. Mỗi lần chuyển tới cổng là một dòng `payment_transactions` (provider `VNPAY`/`MOMO`, `transaction_code` là mã tham chiếu gửi cổng).
4. Cổng gọi IPN: `GET /api/v1/payments/vnpay/ipn` (đăng ký trong trang merchant VNPay) hoặc `POST /api/v1/payments/momo/ipn` (gửi theo từng yêu cầu, cần `PAYMENT_CALLBACK_BASE_URL`). Backend kiểm tra chữ ký (HMAC-SHA512 với VNPay, HMAC-SHA256 với MoMo) và số tiền, rồi chuyển khoản thanh toán sang PAID đúng một lần.
5. Khách quay về `/shop/payment/vnpay` hoặc `/shop/payment/momo`; trang này gửi tham số về `POST /api/v1/payments/{gateway}/return`, cũng kiểm tra chữ ký và chốt giao dịch nếu IPN chưa tới (bên nào tới trước thì chốt).

Giao dịch thất bại giữ khoản thanh toán PENDING để khách thử lại. Tiền về cho khoản đã bị VOID (đơn đã hủy) được ghi nhận và log lỗi để nhân viên hoàn tiền.

Biến môi trường: `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_PAY_URL` (mặc định sandbox); `MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY`, `MOMO_ENDPOINT` (mặc định sandbox); `PAYMENT_CALLBACK_BASE_URL`. V12 tạo sẵn phương thức `VNPAY` và `MOMO` ở trạng thái tắt; bật trong Cài đặt thanh toán sau khi đặt khóa. Màn đó hiện trạng thái khóa và địa chỉ IPN cần đăng ký. Khi khóa chưa đặt, đặt hàng bằng phương thức online trả `PAYMENT_GATEWAY_UNAVAILABLE`.

## Giao hàng GHTK

Phương thức giao hàng có `provider = GHTK` (V12 tạo sẵn mã `GHTK`, đang tắt) lấy phí thật từ GHTK theo địa chỉ khi có `GHTK_TOKEN`:

- `POST /api/v1/store/shipping/quote` báo phí trước khi đặt; đặt hàng gửi kèm `area` (tỉnh, phường/xã, quận nếu có, đường) để tính lại phí. Ngưỡng miễn phí giao hàng vẫn áp dụng. Không gọi được GHTK thì dùng phí cố định của phương thức.
- Admin bấm "Gửi qua GHTK" ở đơn đã xác nhận (`POST /api/v1/admin/carriers/ghtk/shipments`): tạo đơn GHTK (shop trả phí, GHTK thu hộ phần khách còn nợ) và vận đơn với mã nhãn GHTK.
- GHTK gọi `POST /api/v1/carriers/ghtk/webhook?hash=<GHTK_WEBHOOK_SECRET>` khi đổi trạng thái; backend đưa vận đơn qua đúng quy trình thủ công (3 đã lấy hàng, 4/10 đang giao, 5/6 đã giao, 9 giao không thành công, 11/21 đã hoàn, -1 hủy), nên tồn kho, trạng thái đơn và lịch sử luôn khớp.

Biến môi trường: `GHTK_TOKEN`, `GHTK_BASE_URL` (mặc định staging), `GHTK_WEBHOOK_SECRET`, địa chỉ lấy hàng `GHTK_PICK_NAME`, `GHTK_PICK_TEL`, `GHTK_PICK_ADDRESS`, `GHTK_PICK_PROVINCE`, `GHTK_PICK_DISTRICT`, `GHTK_PICK_WARD`, và `GHTK_ITEM_WEIGHT_GRAMS` (mặc định 300 g mỗi sản phẩm, vì sản phẩm chưa có cân nặng). Địa chỉ trên đơn là một dòng nên được tách theo dấu phẩy: phần cuối là tỉnh, trước đó là phường/xã (và quận nếu địa chỉ có 4 phần trở lên).

## Tài khoản: xác nhận email, Google, Facebook

- Đăng ký gửi liên kết `/shop/verify-email?token=...` (24 giờ, dùng một lần; chỉ lưu SHA-256). `POST /api/v1/auth/email/verify` xác nhận; `POST /api/v1/auth/email/resend` gửi lại (tối đa 3 lần/giờ). `AccountResponse` có thêm `emailVerified`. Tài khoản có trước V12 và tài khoản admin coi như đã xác nhận. Chưa xác nhận vẫn mua hàng được; cửa hàng hiện thanh nhắc.
- `GET /api/v1/auth/providers` cho biết nút nào cần hiện. `POST /api/v1/auth/google` nhận ID token của Google Identity Services (kiểm tra chữ ký, issuer, audience = `GOOGLE_CLIENT_ID`). `POST /api/v1/auth/facebook` nhận access token và kiểm tra bằng Graph API `debug_token` với `FACEBOOK_APP_ID`/`FACEBOOK_APP_SECRET`.
- Danh tính đã liên kết (`account_identities`) đăng nhập thẳng; nếu chưa, email đã được nhà cung cấp xác minh sẽ liên kết vào tài khoản khách cùng email hoặc tạo tài khoản khách mới (mật khẩu ngẫu nhiên, đặt lại qua "Quên mật khẩu"). Tài khoản nhân viên không đăng nhập bằng Google/Facebook.

## Giỏ hàng, tìm kiếm, sản phẩm

- `GET/PUT /api/v1/me/cart`: giỏ của khách đã đăng nhập (chỉ biến thể và số lượng; tên, ảnh, giá, tồn đọc trực tiếp). Khi đăng nhập, cửa hàng gộp giỏ trên trình duyệt với giỏ đã lưu (lấy số lượng lớn hơn) rồi lưu lại sau mỗi thay đổi; đăng xuất xóa giỏ trên thiết bị.
- `GET /api/v1/store/search/suggest?q=`: tối đa 6 sản phẩm, cùng danh mục và thương hiệu khớp tên (không phân biệt dấu). Ô tìm kiếm ở header gợi ý khi gõ; danh sách sản phẩm lọc được theo thương hiệu (`?brand=`).
- Bảng size: `GET/PUT/DELETE /api/v1/admin/products/{id}/size-chart` (các cột, mỗi dòng một size, ghi chú). Chi tiết sản phẩm công khai có `sizeChart`; trang sản phẩm hiện nút "Bảng size". Admin sửa trong trang chi tiết sản phẩm.
- Sản phẩm đã xem gần đây lưu trên trình duyệt (`localStorage`, 12 sản phẩm), hiện ở trang sản phẩm và trang chủ.

## Báo khi có hàng

`POST /api/v1/store/stock-alerts` (`productVariantId`, `email`) chỉ nhận size đang hết hàng của sản phẩm đang bán; mỗi email một yêu cầu mở cho mỗi size, tối đa 30 yêu cầu mở. Một job chạy mỗi `STOCK_ALERT_INTERVAL` (mặc định 5 phút) gửi email cho các yêu cầu mà size đã có hàng lại (nhập kho, trả hàng, đơn hủy đều tính) và đóng yêu cầu. Admin xem size được chờ nhiều nhất ở Kho hàng > Yêu cầu báo có hàng (quyền `STOCK_ALERT_READ`).

## Đánh giá có ảnh, nút chat, bài viết, SEO

- Khách đính kèm tối đa 5 ảnh khi đánh giá (tải qua `/api/v1/me/uploads/images`, JPEG/PNG/WebP/GIF dưới 5 MB, giới hạn 20 ảnh/10 phút).
- Cài đặt cửa hàng có thêm `zaloPhone` và `messengerUrl` (`https://m.me/...`). Cửa hàng hiện nút chat nổi mở Zalo, Messenger hoặc gọi số hỗ trợ.
- Trang `/shop/articles` (lọc Bài viết/Lookbook) và `/shop/articles/{slug}`; lookbook ở trang chủ dẫn tới bài. Bài viết có meta và JSON-LD `Article`; sitemap có thêm các bài đã xuất bản.
