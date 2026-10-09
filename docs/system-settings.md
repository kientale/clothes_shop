# Backend cài đặt hệ thống

Migration `V10__system_settings.sql` khởi tạo 6 nhóm cấu hình, 12 quyền và revision cho cấu hình/phương thức. Chạy ứng dụng để Flyway áp dụng migration. Các giá trị mặc định giữ hành vi hiện có: nhận đơn, thanh toán từng phần, phí giao hàng nhập tay, không tự xác nhận đơn, không giới hạn thời gian đổi/trả.

## API quản trị

Base URL: `/api/v1/admin/settings`. Mọi API yêu cầu JWT, role `ADMIN` và quyền đúng nhóm.

| Menu | Đường dẫn GET / PUT | Quyền READ / WRITE | Cấu hình |
| --- | --- | --- | --- |
| Thông tin cửa hàng | `/store` | `SETTINGS_STORE_READ/WRITE` | Tên cửa hàng, tên pháp lý, mã số thuế, email/điện thoại hỗ trợ, địa chỉ, logo, website, giờ mở cửa |
| Thanh toán | `/payment` | `SETTINGS_PAYMENT_READ/WRITE` | Bật thanh toán, cho phép trả từng phần, số tiền tối thiểu mỗi thanh toán |
| Giao hàng | `/shipping` | `SETTINGS_SHIPPING_READ/WRITE` | Bật giao hàng, áp dụng phí cấu hình, phí mặc định, ngưỡng miễn phí |
| Đơn hàng | `/order` | `SETTINGS_ORDER_READ/WRITE` | Bật nhận đơn, tự xác nhận, tiền tố mã đơn, giá trị tối thiểu, số dòng/số lượng tối đa, áp dụng Marketing mặc định, cho hủy/đổi trả, thời hạn đổi trả |
| Thông báo | `/notification` | `SETTINGS_NOTIFICATION_READ/WRITE` | Bật thông báo trong ứng dụng, cho phát toàn bộ khách hàng, số người nhận tối đa |
| Hệ thống chung | `/general` | `SETTINGS_GENERAL_READ/WRITE` | Múi giờ IANA, ngôn ngữ `VI`/`EN`, chế độ và thông điệp bảo trì |

GET trả `{revision, updatedAt, updatedBy, configuration}` trong `ApiResponse.data`. PUT thay thế toàn bộ cấu hình của nhóm, không dùng PATCH:

```json
{
  "expectedRevision": 0,
  "configuration": {
    "paymentsEnabled": true,
    "allowPartialPayments": false,
    "minimumPaymentAmount": 1000
  }
}
```

Lấy revision bằng GET trước khi lưu. Nếu revision thay đổi, API trả HTTP 409 `SETTINGS_REVISION_CONFLICT`; tải lại để đối chiếu trước khi lưu lần nữa. Dữ liệu không hợp lệ hoặc field không được khai báo trả HTTP 400. PUT thành công tăng revision một lần và ghi audit trong cùng transaction; ghi đồng thời được đồng bộ với transaction commerce.

Mỗi nhóm có `GET /{group}/history?page=0&size=20`, yêu cầu quyền READ của nhóm. Lịch sử có người sửa, thời gian, action và JSON trước/sau; thay đổi phương thức thanh toán/giao hàng nằm trong lịch sử nhóm tương ứng. API không cho sửa/xóa lịch sử. Trang tối đa 50 dòng.

## Phương thức thanh toán và giao hàng

| HTTP | Đường dẫn | Chức năng |
| --- | --- | --- |
| GET | `/payment-methods`, `/shipping-methods` | Danh sách gồm phương thức bật và tắt, ẩn phương thức đã xóa mềm |
| GET | `/payment-methods/{id}`, `/shipping-methods/{id}` | Chi tiết |
| POST | `/payment-methods`, `/shipping-methods` | Tạo, HTTP 201 |
| PUT | `/payment-methods/{id}`, `/shipping-methods/{id}` | Sửa với `expectedRevision` |
| DELETE | `/payment-methods/{id}?expectedRevision=0`, `/shipping-methods/{id}?expectedRevision=0` | Xóa mềm, tắt sử dụng mới, giữ lịch sử và mã duy nhất |

Quyền READ/WRITE thuộc nhóm PAYMENT hoặc SHIPPING. Mã phương thức bất biến, viết hoa, tối đa 50 ký tự ASCII chữ/số/gạch ngang/gạch dưới. Mã đã xóa mềm vẫn không được tái sử dụng.

Ví dụ tạo chuyển khoản thủ công:

```json
{
  "code": "BANK_VCB",
  "name": "Chuyển khoản Vietcombank",
  "enabled": true,
  "configuration": {
    "kind": "BANK_TRANSFER",
    "bankDetails": {
      "bankName": "Vietcombank",
      "accountNumber": "0123456789",
      "accountHolder": "LEMONADEX"
    },
    "instructions": "Ghi mã đơn hàng trong nội dung chuyển khoản"
  }
}
```

`kind` hỗ trợ `COD` hoặc `BANK_TRANSFER`. COD không nhận bankDetails; chuyển khoản mới/sửa bắt buộc có đủ thông tin ngân hàng. Phương thức chuyển khoản được seed trước V10 có thể chưa điền bankDetails; cần bổ sung khi sửa. Provider là `MANUAL`: admin vẫn ghi nhận và xác nhận thanh toán, chưa gọi cổng thanh toán bên ngoài. Không có field API key hay mật khẩu.

Ví dụ tạo phương thức giao hàng:

```json
{
  "code": "STANDARD",
  "name": "Giao hàng tiêu chuẩn",
  "provider": "Đơn vị giao hàng",
  "baseFee": 30000,
  "estimatedDays": 3,
  "enabled": true
}
```

`estimatedDays` có thể null hoặc từ 0–365. Phí không âm, có tối đa 2 chữ số thập phân. PUT phương thức không nhận code và cần `expectedRevision` cùng các field sửa.

`GET /shipping/quote?methodCode=STANDARD&merchandiseAmount=200000` tính phí theo phương thức đang bật hoặc phí mặc định nếu không truyền mã. Ngưỡng miễn phí so với tiền hàng sau toàn bộ giảm giá, chưa gồm phí vận chuyển.

## Áp dụng vào nghiệp vụ

- Tạo đơn đọc cấu hình hiện tại. Giới hạn dòng/số lượng, giá trị tiền hàng tối thiểu sau giảm giá, tiền tố mã và tự xác nhận được áp dụng cùng transaction giữ hàng. `applyMarketing` trong request ghi đè mặc định khi có giá trị.
- Request tạo đơn có thêm `shippingMethodCode` tùy chọn. Khi chọn mã hoặc `useConfiguredFees=true`, backend tính phí và bỏ qua `shippingFee` nhập tay. Phí, mã phương thức và việc dùng phí cấu hình được lưu trên đơn; sửa/tắt/xóa mềm phương thức hoặc đổi cấu hình không tính lại đơn cũ. Shipment của đơn dùng phí cấu hình phải nhận đúng phí đã lưu.
- `shippingEnabled=false` chặn đơn và shipment mới. Shipment đã tạo tiếp tục cập nhật trạng thái và giao/hoàn hàng. Phương thức giao hàng ở đây cung cấp biểu phí; admin vẫn nhập nhà vận chuyển và mã vận đơn khi tạo shipment.
- `paymentsEnabled=false` chặn thanh toán mới. Thanh toán PENDING đã tạo vẫn có thể được xác nhận/thất bại/hủy khi trạng thái đơn cho phép. Khi cấm trả từng phần, thanh toán mới phải bằng tổng đơn trừ tiền đã trả và các khoản đang chờ.
- `allowCancellation=false` chặn thao tác hủy mới. `allowReturns=false` và `returnWindowDays` áp dụng khi tạo yêu cầu đổi/trả của đơn đã giao; thời hạn tính từ thời gian giao hàng được ghi nhận. Null nghĩa là không giới hạn thời gian. Hàng không giao được do nhà vận chuyển hoàn về vẫn có thể lập yêu cầu hoàn tiền.
- Cấu hình thông báo tác động lúc publish. Vượt giới hạn người nhận làm rollback toàn bộ lần phát, giữ thông báo DRAFT. Inbox cũ và publish lặp lại của thông báo đã phát vẫn sử dụng được. Kênh hiện có là thông báo trong ứng dụng; chưa tích hợp email/SMS/push.
- Múi giờ chung là mặc định của các báo cáo khi không truyền `timezone`; query cụ thể vẫn có thể ghi đè. Ngôn ngữ là lựa chọn hiển thị để frontend sử dụng.
- Bảo trì yêu cầu thông điệp không rỗng, chặn tạo đơn mới và công bố trạng thái qua cấu hình cửa hàng. Các API quản trị, đăng nhập, lịch sử và xử lý giao dịch hiện có tiếp tục hoạt động.
- Upload logo dùng `POST /api/v1/admin/uploads/images`, được cấp thêm quyền `SETTINGS_STORE_WRITE`; lưu URL trả về vào `logoUrl`.

## Cấu hình công khai

`GET /api/v1/store/configuration` không yêu cầu đăng nhập. Trả thông tin cửa hàng, múi giờ/ngôn ngữ/bảo trì, trạng thái nhận đơn và các phương thức đang bật. Khi tắt toàn bộ thanh toán/giao hàng, danh sách tương ứng rỗng. Không trả thông tin tài khoản ngân hàng, cấu hình thanh toán nội bộ, revision, người sửa hoặc audit. Các response dùng `Cache-Control: no-store`.

Xem schema và đủ 30 operation tại [OpenAPI](openapi.json), request mẫu tại [system-settings.http](system-settings.http). Kiểm thử bằng `.\mvnw.cmd clean verify` với PostgreSQL riêng; bao gồm ghi đồng thời, phân quyền, validation, audit và ảnh hưởng lên nghiệp vụ.
