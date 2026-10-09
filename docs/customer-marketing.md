# Backend khách hàng và Marketing

API trả `ApiResponse<T>`, danh sách dùng `PageResponse<T>` (`page` từ 0, `size` 1–50, mặc định 20). Tìm kiếm chuỗi con không phân biệt hoa/thường, ký tự `%`, `_` được tìm theo nghĩa đen. Thời gian dùng ISO-8601 có múi giờ; hiệu lực từ `startAt` đến trước `endAt`. CRUD tạo trả 201, các thao tác còn lại trả 200; lỗi dữ liệu 400, chưa đăng nhập 401, thiếu quyền 403, không tìm thấy 404, xung đột nghiệp vụ 409.

## Menu quản trị

Mọi endpoint trong bảng có tiền tố `/api/v1/admin`, cần role `ADMIN` và quyền tương ứng. Các tài nguyên có GET danh sách/GET chi tiết/POST tạo/PUT cập nhật/DELETE xóa mềm, trừ đánh giá dùng PUT duyệt và thông báo đã phát hành không được sửa/xóa.

| Menu | Endpoint | Quyền đọc / ghi |
| --- | --- | --- |
| Khách hàng | `/customers`, `/customers/summary`, `/customers/linkable-accounts` | `CUSTOMER_READ` / `CUSTOMER_WRITE` |
| Đánh giá sản phẩm | `/product-reviews`, `/product-reviews/{id}/moderation` | `REVIEW_READ` / `REVIEW_WRITE` |
| Mã giảm giá | `/coupons`, `/coupons/{id}/usages` | `COUPON_READ` / `COUPON_WRITE` |
| Chương trình khuyến mãi | `/promotions` | `PROMOTION_READ` / `PROMOTION_WRITE` |
| Flash Sale | `/flash-sales` | `FLASH_SALE_READ` / `FLASH_SALE_WRITE` |
| Banner | `/banners` | `BANNER_READ` / `BANNER_WRITE` |
| Thông báo | `/notifications`, POST `/notifications/{id}/publish`, GET `/notifications/{id}/recipients` | `NOTIFICATION_READ` / `NOTIFICATION_WRITE` |

Khách hàng đã có CRUD, tìm kiếm, trạng thái ACTIVE/INACTIVE/BLOCKED, tổng hợp và liên kết tài khoản. Xem [quản lý khách hàng](admin-management.md). Migration V8 cấp các quyền mới cho role ADMIN; quyền trong database có hiệu lực ở request tiếp theo.

## Đánh giá đã mua hàng

GET danh sách hỗ trợ `search` theo bình luận, `productId`, `customerId`, `status=PENDING|APPROVED|REJECTED`, `rating=1..5`. POST admin nhận query bắt buộc `customerId` và body `orderItemId`, `rating`, `comment`, `images`. Backend tự suy ra sản phẩm từ dòng mua và kiểm tra đơn thuộc đúng khách, trạng thái DELIVERED/COMPLETED, chưa trả/đổi hết dòng hàng. Mỗi dòng mua chỉ có một đánh giá, kể cả sau xóa mềm. Không thể tạo đánh giá tùy ý cho một sản phẩm.

PUT `/product-reviews/{id}/moderation` nhận `status` và `note` bắt buộc; lưu người duyệt và thời gian duyệt. Đánh giá mới luôn PENDING. `images` tối đa 10 URL HTTP/HTTPS, không trùng; xóa mềm giữ bằng chứng ảnh. Admin có quyền REVIEW_WRITE có thể tải ảnh qua POST multipart `/admin/uploads/images` (file PNG/JPEG/WebP, tối đa 5 MB).

Khách có role CUSTOMER và hồ sơ ACTIVE dùng GET/POST `/api/v1/me/product-reviews`. Người gửi được xác định từ JWT; không nhận customerId từ body. Danh sách chỉ chứa đánh giá của chính khách đó.

## Ưu đãi và đơn hàng

Coupon: mã ASCII chữ/số/`_`/`-`, tối đa 80 ký tự, chuẩn hóa chữ hoa và duy nhất kể cả đã xóa. `discountType=PERCENTAGE|FIXED_AMOUNT`; giá trị dương, phần trăm ≤100. Có `maxDiscount`, `minimumOrderValue`, `usageLimit` (null = không giới hạn toàn hệ thống), `usageLimitPerCustomer`, `startAt`, `endAt`, `status=ACTIVE|INACTIVE`. `usedCount` do backend quản lý; không thể sửa tổng hạn mức xuống dưới số đang dùng. GET `/coupons/{id}/usages` phân trang các đơn sử dụng và trạng thái đã hoàn lại hạn mức.

Promotion: `promotionType=ALL_PRODUCTS|PRODUCT_DISCOUNT|CATEGORY_DISCOUNT`. ALL_PRODUCTS có hai danh sách UUID rỗng; PRODUCT_DISCOUNT cần `productIds` và `categoryIds` rỗng; CATEGORY_DISCOUNT cần `categoryIds` và `productIds` rỗng. Tham chiếu phải tồn tại, chưa xóa; UUID không trùng. Chọn chương trình có `priority` cao nhất, rồi mức giảm lớn nhất, rồi UUID. Giảm cố định tính theo mỗi đơn vị sản phẩm.

Flash Sale: thời gian và trạng thái như coupon, có 1–100 dòng `productVariantId`, `flashPrice`, `quantityLimit`. Biến thể phải đang bán; giá flash không âm và thấp hơn giá hiện tại. `soldQuantity` bao gồm số lượng đã đặt còn hiệu lực. Không sửa hạn mức thấp hơn số đang dùng; dòng đã có lịch sử phân bổ không được xóa. Có thể thêm/xóa dòng chưa phân bổ, sửa giá cho đơn mới hoặc chuyển chiến dịch INACTIVE. Xóa mềm chiến dịch vẫn giữ dòng hàng và phân bổ của đơn cũ.

POST `/api/v1/admin/orders` bổ sung hai trường tùy chọn:

```json
{ "applyMarketing": true, "couponCode": "WELCOME10" }
```

Gửi kèm các trường đơn hàng vốn có trong [API đơn hàng](inventory-orders.md). `applyMarketing=true` chọn giữa chương trình và Flash Sale có mức giảm tốt hơn, không cộng hai ưu đãi này trên cùng dòng. Flash Sale được chọn nhưng thiếu suất trả 409 `FLASH_SALE_EXHAUSTED`. Nếu bỏ `applyMarketing`, giá và giảm thủ công vẫn giữ hành vi cũ. `couponCode` áp dụng độc lập với cờ này, sau giảm dòng và giảm thủ công toàn đơn; phí vận chuyển không được giảm. Giảm theo phần trăm làm tròn xuống hai chữ số, có trần giảm và không vượt tiền hàng còn lại.

Toàn bộ tính giá, hạn mức, ghi phân bổ và giữ tồn kho cùng transaction và khóa PostgreSQL như kho/đơn hàng. Lỗi một dòng hàng hoàn tác toàn bộ đơn, suất flash và lượt coupon. Hủy trước giao hoặc hàng giao thất bại đã nhập lại kho hoàn lại lượt coupon và suất flash đúng một lần; đổi/trả sau giao không mở lại suất chiến dịch. Giá và tổng giảm được chụp vào dòng/đơn nên sửa hoặc xóa chiến dịch không thay đổi đơn cũ.

## Banner

`title`, `imageUrl` HTTP/HTTPS, `linkUrl` HTTP/HTTPS hoặc đường dẫn bắt đầu `/`, `position` mã ASCII chữ/số/`_`/`-` chuẩn hóa chữ hoa, `sortOrder` không âm, thời gian bắt đầu/kết thúc tùy chọn, `status=ACTIVE|INACTIVE`. GET danh sách nhận `search`, `status`, `position`, sắp theo `sortOrder`, rồi thời gian tạo giảm dần, rồi UUID. Admin BANNER_WRITE được tải ảnh qua `/admin/uploads/images`.

## Thông báo trong ứng dụng

Body gồm `title`, `content`, `notificationType=GENERAL|ORDER|PROMOTION`, `targetType=ALL|SELECTED`, `customerIds`. ALL yêu cầu danh sách rỗng; SELECTED yêu cầu 1–1000 khách không trùng. Tạo và sửa chỉ lưu DRAFT. DELETE chỉ xóa mềm bản nháp. POST publish phát hành cho khách ACTIVE chưa xóa tại thời điểm thực hiện; nếu không có người nhận thì trả 409 và giữ nháp. Publish lặp lại không tạo bản sao. Nội dung, tập người nhận và thời gian phát hành của bản PUBLISHED được giữ nguyên. GET danh sách hỗ trợ `search`, `status=DRAFT|PUBLISHED`; chi tiết có `recipientCount`/`readCount`, lịch sử người nhận có phân trang.

GET `/api/v1/me/notifications?read=false&page=0&size=20` trả hộp thư của khách hiện tại. PUT `/api/v1/me/notifications/{id}/read` dùng ID dòng hộp thư (khác ID thông báo), đánh dấu đã đọc; gọi lại giữ nguyên `readAt`. Truy cập ID của khách khác trả 404. Các endpoint này cần role CUSTOMER và hồ sơ ACTIVE.

Thông báo được phát hành vào database/hộp thư ứng dụng. Chưa tích hợp email, SMS, Firebase hay dịch vụ gửi bên ngoài.

## Kiểm chứng

`MarketingIntegrationTests` kiểm tra CRUD, quyền theo module, validation, ưu đãi trên đơn, rollback do thiếu hàng, tranh chấp coupon/Flash Sale, đánh giá sau mua, xóa mềm giữ ảnh, phát hành thông báo lặp lại và quyền đọc hộp thư. Chạy `./mvnw.cmd clean verify`; test tự khởi động PostgreSQL riêng và chạy V1–V8. [OpenAPI](openapi.json) và [HTTP mẫu](api.http) chứa endpoint/request để nối frontend.
