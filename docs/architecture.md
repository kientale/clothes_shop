# Kiến trúc MVC

Ứng dụng là backend REST theo Spring MVC, triển khai auth trên schema thời trang PostgreSQL. Controller nhận request, gọi Service và trả JSON từ response DTO. Service quản lý nghiệp vụ và transaction; Repository truy vấn entity. Các nhóm catalog, tồn kho, đơn hàng và marketing đã có schema V1, chưa có API trong code hiện tại.

```text
lemonadex.project.clothes
├── controller                 # AuthController, AccountController
├── service                    # Auth, token, quyền hiện tại, truy vấn tài khoản
├── repository                 # Account, Customer, Role
│   └── specification          # Điều kiện deleted=false + lọc tài khoản
├── model                      # BaseEntity, Account, Customer, Role, Permission
├── dto
│   ├── auth                   # Request và response riêng
│   └── common                 # ApiResponse<T>, PageResponse<T>
├── mapper                     # MapStruct Spring bean
├── config                     # SecurityConfig: JWT, BCrypt, CORS, quyền
├── properties                 # Cấu hình JWT/CORS có validation
├── security                   # JWT converter, CurrentUser
├── filter                     # X-Request-Id và MDC
├── exception                  # Exception nghiệp vụ, handler và JSON lỗi security
└── util                       # DateTimeUtils
```

Controller và DTO không phụ thuộc entity persistence. Mapper chuyển entity thành DTO, không map id khi tạo entity. ArchUnit kiểm tra chiều phụ thuộc và vòng phụ thuộc giữa các package; Security gọi AccountAccessService để kiểm tra quyền từ database.

## Luồng đăng ký

1. Controller validate RegisterRequest: email, mật khẩu, xác nhận mật khẩu, họ tên, số điện thoại và ngày sinh.
2. Service chuẩn hóa email, kiểm tra giới hạn 72 byte UTF-8 của BCrypt và role CUSTOMER.
3. Một transaction tạo accounts, customers và account_roles. Unique index lower(email) xử lý cả request cạnh tranh và email của bản ghi đã xóa mềm.
4. Mapper tạo AccountResponse, token service ký JWT HS256, controller trả HTTP 201 với ApiResponse<AuthResponse>.

## Luồng đăng nhập và xác thực

AuthService tìm account chưa xóa mềm, kiểm tra BCrypt và trạng thái ACTIVE, cập nhật last_login_at rồi cấp JWT 15 phút. Email không tồn tại dùng BCrypt dummy hash; lỗi sai email/mật khẩu trả cùng code INVALID_CREDENTIALS.

Spring Security xác minh chữ ký, issuer, audience, expiry và UUID subject của JWT. Converter tải account, role và permission hiện tại từ database, chỉ nhận account ACTIVE và bản ghi deleted=false. Quyền từ JWT không được dùng để thay thế quyền hiện tại trong database.

Route auth/me yêu cầu AUTH_PROFILE_READ. Các route admin yêu cầu role ADMIN tại SecurityConfig và ACCOUNT_READ tại controller. ApiErrorWriter bọc lỗi từ security filter; GlobalExceptionHandler bọc lỗi từ controller/service.

## Persistence và xóa mềm

Flyway quản lý schema bằng V1 và V2, seed tài khoản admin bằng V3; JPA validate mapping, không cập nhật schema. Bốn entity auth kế thừa BaseEntity với UUID, deleted, createdAt và updatedAt. @SQLDelete chuyển thao tác xóa JPA thành UPDATE; @SQLRestriction ẩn bản ghi đã xóa. Repository auth có điều kiện DeletedFalse và specification luôn thêm deleted=false.

Không cascade REMOVE giữa account, customer, role và permission. Role/permission dùng bảng nối của schema hiện tại. Timestamp lưu TIMESTAMPTZ; DTO dùng UTC với định dạng từ DateTimeUtils.

## Gateway

Nginx định tuyến register, login, auth/me, admin/accounts và health đến backend. Authorization được chuyển nguyên vẹn; Spring Security kiểm tra JWT và quyền. Cấu hình gateway và ứng dụng nằm ở gateway/ và compose.auth.yaml. Container backend không công bố cổng ra host.

Test commerce cũ được giữ tại src/test/legacy vì các lớp catalog/cart/order tương ứng đã bị loại khỏi workspace trước phiên bản auth này. Bộ test đang chạy ở src/test/java kiểm chứng code hiện tại trên PostgreSQL riêng.
