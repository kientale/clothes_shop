# Kiến trúc theo feature

Ứng dụng là backend REST theo Spring MVC, tổ chức package theo chức năng nghiệp vụ: auth quản lý xác thực/JWT, account quản lý tài khoản admin, role quản lý vai trò/quyền, customer quản lý khách hàng, file lưu ảnh, catalog quản lý sản phẩm, inventory quản lý kho và order quản lý đơn/thanh toán/giao hàng/đổi trả/hoàn tiền. Review quản lý đánh giá sau mua; marketing quản lý coupon, promotion, Flash Sale, banner và hộp thư thông báo. Controller nhận request, gọi Service và trả JSON từ response DTO. Service quản lý nghiệp vụ và transaction; Repository truy vấn entity.

```text
lemonadex.project.clothes
├── ClothesApplication
├── features
│   ├── auth
│   │   ├── controller         # AuthController
│   │   ├── service            # AuthService, AuthTokenService
│   │   ├── dto                # LoginRequest, RegisterRequest, AuthResponse
│   │   ├── mapper             # AuthMapper: đăng ký và response xác thực
│   │   ├── config             # SecurityConfig, SecurityProperties, CorsProperties
│   │   ├── security           # JWT converter, CurrentUser
│   │   └── exception          # EmailAlreadyRegisteredException
│   ├── account
│   │   ├── controller         # AccountController, AdminAccountController
│   │   ├── service            # Access, Query, AdminAccountService
│   │   ├── repository         # Account, AdminProfile + specification
│   │   ├── model              # Account, AccountStatus, AdminProfile
│   │   ├── dto                # Identity, request/response tài khoản
│   │   └── mapper             # AccountMapper, AdminAccountMapper
│   ├── role
│   │   ├── controller         # RoleController, PermissionController
│   │   ├── service            # RoleService
│   │   ├── repository         # Role, Permission + specification
│   │   ├── model              # Role, Permission
│   │   ├── dto                # Request/response vai trò, danh mục quyền
│   │   └── mapper             # RoleMapper
│   ├── customer               # Hồ sơ khách hàng: controller/service/repository/model/dto/mapper
│   ├── file                   # Upload và đọc file ảnh
│   ├── catalog                # Sản phẩm, taxonomy, biến thể và bộ sưu tập
│   ├── inventory              # Warehouse, Inventory, ledger và stock reservations
│   ├── order                  # Order, payment, shipment, return, refund và lịch sử
│   ├── review                 # Đánh giá đã mua và duyệt đánh giá
│   ├── marketing              # Campaign CRUD, checkout ưu đãi, thông báo và hộp thư
│   ├── content                # Bài viết/Lookbook, phiên bản chính sách, nội dung công khai
│   ├── report                 # Tổng hợp SQL từ dữ liệu thương mại, không sửa dữ liệu
│   ├── payment                # Cổng VNPay/MoMo: tạo liên kết thanh toán, IPN và trang kết quả
│   ├── carrier                # GHTK: phí theo địa chỉ, tạo đơn vận chuyển, webhook trạng thái
│   └── storefront             # API cửa hàng: catalog, đơn của khách/khách vãng lai, giỏ, đổi/trả, báo có hàng
└── common
    ├── dto                    # ApiResponse<T>, PageResponse<T>
    ├── model                  # BaseEntity, CreatedEntity
    ├── exception              # ConflictException, ResourceNotFoundException, xử lý lỗi
    ├── filter                 # X-Request-Id và MDC
    └── util                   # DateTimeUtils
```

Chiều phụ thuộc là `auth → customer → account → role`; auth cũng dùng trực tiếp account/role, mọi feature dùng common. Role không phụ thuộc ngược vào account/customer/auth; account không phụ thuộc customer/auth; common không import feature. RoleRepository kiểm tra việc gán role qua bảng nối bằng truy vấn SQL, giữ ranh giới Java không có vòng phụ thuộc. Chức năng mới đặt tại `features/<tên-feature>/` với controller, service, repository, model, DTO và mapper của chức năng đó.

Commerce dùng chiều `order → inventory → catalog`. InventoryService cung cấp DTO biến thể và thao tác reserve/release/ship/restock trong transaction; OrderService lưu snapshot để không phụ thuộc entity đã xóa mềm. Các thao tác ghi lấy khóa catalog trước commerce, bảo vệ kiểm tra tồn, vòng đời và hạn mức payment/refund qua nhiều request/server. Lịch sử/ledger chỉ được thêm; hủy nghiệp vụ tạo chuyển trạng thái, không xóa dữ liệu tài chính.

Controller và DTO không phụ thuộc entity persistence. Mapper chuyển entity thành DTO, không map id khi tạo entity. ArchUnit kiểm tra vị trí các thành phần, chiều Controller → Service → Repository, vòng phụ thuộc giữa các feature và ranh giới của common. Security gọi AccountAccessService để kiểm tra quyền từ database. Exception nghiệp vụ thuộc feature; GlobalExceptionHandler nhận ConflictException dùng chung và giữ nguyên error code của feature.

## Luồng đăng ký

1. Controller validate RegisterRequest: email, mật khẩu, xác nhận mật khẩu, họ tên, số điện thoại và ngày sinh.
2. Service chuẩn hóa email, kiểm tra giới hạn 72 byte UTF-8 của BCrypt và role CUSTOMER.
3. Một transaction tạo accounts, customers và account_roles. Unique index lower(email) xử lý cả request cạnh tranh và email của bản ghi đã xóa mềm.
4. Mapper tạo AccountResponse, token service ký JWT HS256, controller trả HTTP 201 với ApiResponse<AuthResponse>.

## Luồng đăng nhập và xác thực

AuthService tìm account chưa xóa mềm, kiểm tra BCrypt và trạng thái ACTIVE, cập nhật last_login_at rồi cấp JWT 15 phút. Email không tồn tại dùng BCrypt dummy hash; lỗi sai email/mật khẩu trả cùng code INVALID_CREDENTIALS.

Spring Security xác minh chữ ký, issuer, audience, expiry và UUID subject của JWT. Converter tải account, role và permission hiện tại từ database, chỉ nhận account ACTIVE và bản ghi deleted=false. Quyền từ JWT không được dùng để thay thế quyền hiện tại trong database.

Route auth/me yêu cầu AUTH_PROFILE_READ. Route admin yêu cầu ADMIN tại SecurityConfig và permission đọc/ghi tương ứng tại controller. ApiErrorWriter bọc lỗi security filter; GlobalExceptionHandler bọc lỗi controller/service. Xem [API quản lý](admin-management.md).

## Persistence và xóa mềm

Flyway quản lý schema bằng V1–V12; V3 seed admin, V4 bổ sung hồ sơ/quyền quản lý, V5 lưu file ảnh, V6 quản lý catalog, V7 quản lý kho/đơn, V8 quản lý đánh giá/Marketing. JPA validate mapping, không cập nhật schema. Các entity xóa mềm kế thừa BaseEntity với UUID, deleted, createdAt/updatedAt. @SQLDelete chuyển xóa JPA thành UPDATE; @SQLRestriction ẩn bản ghi đã xóa. Entity commerce và lịch sử dùng CreatedEntity với UUID/createdAt, không có API xóa; Order/Inventory giữ thêm updatedAt theo schema.

Không cascade REMOVE giữa account, customer, role và permission. Role/permission dùng bảng nối của schema hiện tại. Timestamp lưu TIMESTAMPTZ; DTO dùng UTC với định dạng từ DateTimeUtils.

## Gateway

Nginx định tuyến register, login, auth/me, admin/accounts và health đến backend. Authorization được chuyển nguyên vẹn; Spring Security kiểm tra JWT và quyền. Cấu hình gateway và ứng dụng nằm ở gateway/ và compose.auth.yaml. Container backend không công bố cổng ra host.

Test commerce cũ được giữ tại src/test/legacy vì các lớp catalog/cart/order tương ứng đã bị loại khỏi workspace trước phiên bản auth này. Bộ test đang chạy ở src/test/java kiểm chứng code hiện tại trên PostgreSQL riêng.

Review và marketing dùng InventoryService.lock để thống nhất thứ tự khóa catalog → commerce. Order phụ thuộc MarketingCheckoutService để ghi ưu đãi và hoàn hạn mức; marketing không import entity/service của order mà truy vấn bảng phân bổ qua repository. Đồ thị Java có `order → marketing → inventory → catalog`, `review → inventory`, không có vòng phụ thuộc. Đánh giá xóa mềm bằng UPDATE repository để giữ ảnh làm bằng chứng. Thông báo đã phát hành và các bản ghi sử dụng/hoàn hạn mức được giữ bền vững.

Content tự quản lý khóa transaction PostgreSQL `(1279613007, 3)` cho bài viết và tăng/kích hoạt phiên bản chính sách; không phụ thuộc commerce. V9 bổ sung trường quản lý nội dung và chỉ mục báo cáo. Report chỉ đọc qua NamedParameterJdbcTemplate tại repository, dùng transaction REPEATABLE_READ để tổng và chi tiết cùng snapshot. Service chuyển các dòng tổng hợp sang DTO; controller không truy cập SQL/entity. Các enum đổi/trả được dùng từ order, không tạo phụ thuộc ngược. Xem [nội dung và báo cáo](content-reports.md).


## System settings

The settings feature owns typed JSON groups in system_settings, manual payment methods, shipping methods and append-only audit_logs writes. Updates compare revisions and acquire the catalog lock (1279613007, 1), then commerce lock (1279613007, 2), in the same order as checkout. Configuration and audit changes commit together. The feature uses JDBC and depends only on common; order, marketing and report services read settings without reverse feature dependencies. Shipping fees and method codes are saved on orders, so later configuration changes preserve historical prices. V10 seeds all six groups with compatible defaults and grants twelve separate read/write permissions to ADMIN. See [system settings](system-settings.md).


## Mở rộng cửa hàng

`payment` và `carrier` chỉ đi qua service công khai của `order` (PaymentService, ShipmentService), nên khoản thanh toán do cổng xác nhận và vận đơn do GHTK cập nhật đi đúng các chuyển trạng thái, khóa và lịch sử như thao tác của admin. Chiều phụ thuộc: `storefront → payment → order`, `storefront → carrier → order/settings`; `order` không biết tới cổng hay hãng vận chuyển. Phí GHTK đi vào `OrderService.create` qua tham số `carrierFee`, và `SettingsService.quote` vẫn áp ngưỡng miễn phí giao hàng. Job báo có hàng (`StockAlertService`) chạy theo lịch trong `storefront` (tắt bằng `app.scheduling.enabled=false` khi kiểm thử). Xem [mở rộng cửa hàng](storefront-extensions.md).
