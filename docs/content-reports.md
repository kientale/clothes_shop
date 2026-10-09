# Backend nội dung và báo cáo

Migration V9 bổ sung trường quản lý nội dung, phiên bản chính sách, chỉ mục báo cáo và 11 quyền mới cho ADMIN. API dùng `ApiResponse<T>`, trang dùng `PageResponse<T>` (`page` từ 0, `size` 1–50, mặc định 20). CRUD tạo trả 201; đọc/cập nhật/chuyển trạng thái/xóa mềm trả 200. Dữ liệu không hợp lệ trả 400, chưa đăng nhập 401, thiếu quyền 403, không tìm thấy 404 và xung đột trạng thái 409.

## Bài viết / Lookbook

Các route admin có tiền tố `/api/v1/admin` và cần role ADMIN:

| Endpoint | Quyền |
| --- | --- |
| GET `/articles`, GET `/articles/{id}` | `ARTICLE_READ` |
| POST `/articles`, PUT `/articles/{id}`, DELETE `/articles/{id}` | `ARTICLE_WRITE` |
| PUT `/articles/{id}/status` | `ARTICLE_WRITE` |
| GET `/store-policies`, GET `/store-policies/{id}` | `POLICY_READ` |
| POST `/store-policies`, PUT `/store-policies/{id}`, DELETE `/store-policies/{id}` | `POLICY_WRITE` |
| PUT `/store-policies/{id}/status` | `POLICY_WRITE` |

Body bài viết gồm `title`, `slug` tùy chọn, `thumbnailUrl` tùy chọn, `content`, `articleType=ARTICLE|LOOKBOOK`, `images` tối đa 50 URL không trùng. URL ảnh phải HTTP/HTTPS. Nội dung tối đa 100.000 ký tự; backend lưu chuỗi nội dung, không render HTML. Slug chỉ chữ thường/số/dấu gạch nối; nếu bỏ trống, backend tạo từ tiêu đề và chuyển dấu tiếng Việt. Slug đã xóa vẫn được giữ để không chuyển URL cũ sang bài khác. Tác giả lấy từ JWT khi tạo; client không được gửi `authorId` hay timestamp.

Tạo luôn ở DRAFT. Chỉ DRAFT được sửa. PUT status nhận `DRAFT|PUBLISHED|ARCHIVED`; xuất bản chỉ từ DRAFT, ghi `publishedAt`; Lookbook cần ít nhất một ảnh. Có thể đưa bài về DRAFT để rút khỏi nội dung công khai rồi sửa; muốn xuất bản bài ARCHIVED phải chuyển về DRAFT trước. DELETE xóa mềm và giữ ảnh. GET danh sách hỗ trợ `search` theo tiêu đề/slug, `articleType`, `status`; tìm kiếm chuỗi con không phân biệt hoa/thường, `%` và `_` là ký tự thường. Admin ARTICLE_WRITE có thể upload PNG/JPEG/WebP tối đa 5 MB qua POST multipart `/api/v1/admin/uploads/images`.

Các route chỉ đọc nội dung đã phát hành được dùng công khai, không cần JWT:

- GET `/api/v1/content/articles?articleType=LOOKBOOK&page=0&size=20`: chỉ PUBLISHED, có `search`, sắp theo thời gian xuất bản; danh sách tóm tắt không tải toàn bộ nội dung/ảnh.
- GET `/api/v1/content/articles/{slug}`: chi tiết PUBLISHED, không lộ ID tài khoản tác giả.
- GET `/api/v1/content/store-policies/{policyType}`: chỉ phiên bản ACTIVE, không lộ ID người cập nhật.

## Chính sách có phiên bản

`policyType=SHIPPING|RETURN_EXCHANGE|PAYMENT|PRIVACY|TERMS_OF_SERVICE|WARRANTY`. Body gồm loại, tiêu đề, nội dung. POST tự tăng `version` theo loại, kể cả bản nháp đã xóa, và tạo DRAFT. Client không gán phiên bản. PUT chỉ sửa DRAFT và không đổi loại.

PUT status nhận `ACTIVE|ARCHIVED` hoặc DRAFT nếu bản hiện tại vốn DRAFT. Kích hoạt DRAFT sẽ lưu `activatedAt`, người cập nhật và chuyển bản ACTIVE cũ cùng loại sang ARCHIVED trong một transaction. Khóa PostgreSQL bảo vệ tăng phiên bản/kích hoạt đồng thời; luôn tối đa một ACTIVE mỗi loại. ACTIVE/ARCHIVED không thể trở lại nháp hoặc kích hoạt lại; thay nội dung bằng cách tạo phiên bản mới. DELETE chỉ xóa mềm DRAFT; các bản đã phát hành giữ làm lịch sử. GET danh sách lọc `policyType`, `status` và có phân trang.

## Báo cáo và thống kê

Tất cả route cần ADMIN và quyền riêng, có tiền tố `/api/v1/admin/reports`:

| Báo cáo | Route | Quyền |
| --- | --- | --- |
| Doanh thu thu tiền | GET `/revenue` | `REPORT_REVENUE_READ` |
| Đơn hàng | GET `/orders` | `REPORT_ORDER_READ` |
| Sản phẩm bán chạy | GET `/bestsellers` | `REPORT_PRODUCT_READ` |
| Tồn kho | GET `/inventory` | `REPORT_INVENTORY_READ` |
| Khách hàng | GET `/customers` | `REPORT_CUSTOMER_READ` |
| Đổi/trả hàng | GET `/returns` | `REPORT_RETURN_READ` |
| Hiệu quả khuyến mãi | GET `/promotions` | `REPORT_PROMOTION_READ` |

Trừ tồn kho hiện tại, báo cáo nhận `from`, `to` là ISO-8601 có múi giờ; bao gồm `from`, không bao gồm `to`. Nếu bỏ `to`, dùng thời điểm hiện tại; bỏ `from`, lấy 30 ngày trước `to`. Khoảng phải dương, tối đa 366 ngày. `timezone` mặc định `Asia/Ho_Chi_Minh`, cần tên IANA được Java và PostgreSQL hỗ trợ, ví dụ `UTC`. `warehouseId` tùy chọn lọc đơn thuộc kho. Doanh thu và đơn hàng có `groupBy=DAY|WEEK|MONTH`; tuần bắt đầu thứ Hai, tháng bắt đầu ngày 1 theo múi giờ đã chọn. Chuỗi biểu đồ điền cả các mốc không có dữ liệu bằng 0. `period` trong response ghi rõ thời gian và múi giờ thực dùng.

Mỗi response đọc một snapshot REPEATABLE_READ để phần tổng hợp và trang chi tiết nhất quán khi có giao dịch khác chạy cùng lúc. Bảng con được tổng hợp trước khi nối để tránh cộng trùng tiền do một đơn có nhiều thanh toán, dòng hàng hoặc khoản hoàn.

### Ý nghĩa từng chỉ số

- **Doanh thu:** `paidAmount` là tổng payment PAID theo `paidAt`; `refundedAmount` là refund SUCCEEDED theo `processedAt`; `netReceivedAmount = paidAmount - refundedAmount`. Chỉ PAID/SUCCEEDED được tính, không cộng log giao dịch. Tiền của đơn hủy vẫn tính nếu thực tế đã thu và chưa hoàn. Một kỳ chỉ có hoàn tiền có thể âm. Đây là dòng tiền đã ghi nhận trong hệ thống, không phải doanh thu kế toán theo thời điểm giao hàng.
- **Đơn hàng:** chọn theo `placedAt`; tổng số và phân bố trạng thái gồm cả đơn hủy. `orderAmount` cộng tổng tiền các đơn chưa CANCELLED, gồm phí giao hàng; `cancelledAmount` báo riêng. `averageOrderValue` chia tiền đơn chưa hủy cho số đơn chưa hủy, làm tròn hai chữ số.
- **Bán chạy:** chọn đơn DELIVERED/COMPLETED theo `placedAt`, nhóm theo sản phẩm. `grossQuantity` lượng đã giao, `returnedQuantity` lượng RETURN đã COMPLETED, `netQuantity` chênh lệch. EXCHANGE cùng sản phẩm không giảm lượng bán. `merchandiseAmount` phân bổ giảm toàn đơn theo giá trị ròng từng dòng, rồi trừ phần hàng đã trả; không gồm phí giao hàng. Trả hàng đã hoàn tất được tính đến snapshot hiện tại, kể cả sau kỳ đặt đơn. Đây là giá trị hàng giữ lại theo đơn, khác với tiền hoàn thực tế. Tên lấy từ snapshot mua gần nhất. Có `productId`, `page`, `size`; xếp lượng ròng giảm dần, giá trị hàng giảm dần, UUID. Sản phẩm đã xóa vẫn có dữ liệu lịch sử và cờ `deleted`.
- **Tồn kho:** snapshot hiện tại, không nhận khoảng thời gian lịch sử. Có `warehouseId`, `productId`, `search` theo tên/SKU, `lowStock=true`, `threshold` mặc định 5, phân trang. Tổng `quantityOnHand`, `quantityReserved`, `quantityAvailable` và giá trị tính trên toàn bộ kết quả lọc, không chỉ trang đang xem. `retailValue` dùng giá bán biến thể hiện tại nhân lượng có trong kho; schema chưa có giá vốn nên không diễn giải thành giá vốn tồn kho. Bản ghi kho/sản phẩm/biến thể đã xóa được giữ và có cờ tương ứng.
- **Khách hàng:** `newCustomers` là hồ sơ tạo trong kỳ, kể cả đã xóa; `totalCustomers`/`activeCustomers` là tổng hiện tại chưa xóa, không lọc theo kho. `purchasingCustomers`/`repeatCustomers` dùng đơn DELIVERED/COMPLETED đặt trong kỳ và lọc kho; mua lặp lại nghĩa là từ hai đơn. Trang xếp `netOrderAmount` giảm dần, số đơn giảm dần, UUID. Tiền đơn gồm phí giao hàng; tổng refund SUCCEEDED cho các đơn thuộc kỳ được trừ đến snapshot hiện tại. Khách đã xóa vẫn có lịch sử mua.
- **Đổi/trả:** chọn theo `requestedAt`, có `status`, `requestType=RETURN|EXCHANGE` và phân trang. Báo riêng số yêu cầu trả/đổi, lượng đề nghị, lượng của yêu cầu COMPLETED, phân bố trạng thái và tổng hoàn SUCCEEDED cho các yêu cầu được chọn. Tổng hoàn bao gồm các giao dịch xử lý sau kỳ tạo yêu cầu đến snapshot hiện tại.
- **Khuyến mãi:** chọn phân bổ trên các đơn có `placedAt` trong kỳ; có `campaignType=COUPON|PROMOTION|FLASH_SALE`, phân trang. Mỗi chiến dịch đếm đơn một lần dù có nhiều dòng; `releasedOrderCount` đếm đơn đã hoàn hạn mức/hủy, không cộng vào lượt/giảm/tiền hiệu lực. Coupon tính theo đơn nên `quantity=0`; promotion/flash ghi lượng dòng hàng. `discountAmount` là mức giảm của riêng nguồn đó, `attributedOrderAmount` là toàn bộ tiền đơn hiệu lực từng dùng chiến dịch, đếm một lần mỗi chiến dịch. Một đơn có thể dùng coupon và flash/promotion, nên không cộng tiền quy thuộc giữa các chiến dịch để suy ra doanh thu tổng. Báo cáo không suy diễn tỷ lệ chuyển đổi/ROI khi chưa có dữ liệu lượt xem hoặc chi phí chiến dịch. Chiến dịch đã xóa vẫn được báo cáo từ lịch sử phân bổ.

Các báo cáo có trang rỗng vẫn trả tổng số đúng ngay cả khi `page` vượt trang cuối. Lịch sử phân bổ ưu đãi chỉ có cho đơn tạo sau khi bật chức năng Marketing; báo cáo không dựng ngược ưu đãi từ giảm giá thủ công của đơn cũ.

## Chạy và kiểm chứng

`ContentReportIntegrationTests` kiểm tra xuất bản/ẩn bài, slug và ảnh, phiên bản chính sách/kích hoạt đồng thời, phân quyền từng báo cáo, dữ liệu rỗng, nhóm theo múi giờ, hoàn tiền, giảm giá và hàng trả, tổng hợp không nhân bản, phân trang vượt cuối và lịch sử sau xóa mềm. Chạy `./mvnw.cmd clean verify`; PostgreSQL test riêng chạy V1–V9 và validate JPA. Xem [OpenAPI](openapi.json) và [HTTP mẫu nội dung/báo cáo](content-reports.http) để nối frontend.
