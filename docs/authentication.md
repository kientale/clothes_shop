# Đăng ký và đăng nhập

## Đối chiếu tiêu chuẩn code

| Tiêu chuẩn | Triển khai |
| --- | --- |
| Migration | [V2](../src/main/resources/db/migration/V2__authentication_soft_delete_and_roles.sql) bổ sung deleted, timestamp và role/permission; giữ V1. Flyway chạy SQL, ddl-auto: validate. |
| Entity | Account, Customer, Role, Permission kế thừa [BaseEntity](../src/main/java/lemonadex/project/clothes/model/BaseEntity.java). @SQLDelete cập nhật deleted=true; @SQLRestriction ẩn bản ghi xóa mềm. |
| DTO/Validation | RegisterRequest, LoginRequest và response DTO riêng; @NotBlank, @Email, @Size, @Pattern, @Past, @AssertTrue. |
| Ngày/giờ | [DateTimeUtils](../src/main/java/lemonadex/project/clothes/util/DateTimeUtils.java) cung cấp DATE_PATTERN, TIME_PATTERN, DATE_TIME_PATTERN; DTO dùng @JsonFormat. |
| Mapper | [AuthMapper](../src/main/java/lemonadex/project/clothes/mapper/AuthMapper.java) dùng componentModel="spring"; bỏ qua id, trạng thái, quyền và trường hệ thống khi tạo entity. |
| Repository/Spec | Truy vấn auth có DeletedFalse; AccountSpecification.visible luôn thêm deleted=false; entity restriction áp dụng cả quan hệ role/permission. |
| Response | Controller trả ApiResponse<T>; danh sách tài khoản trả ApiResponse<PageResponse<AccountSummaryResponse>>. |
| Exception | [GlobalExceptionHandler](../src/main/java/lemonadex/project/clothes/exception/GlobalExceptionHandler.java) xử lý ResourceNotFoundException, IllegalArgumentException, validation và lỗi dữ liệu; ApiErrorWriter xử lý lỗi security filter. |
| Security/Gateway | [SecurityConfig](../src/main/java/lemonadex/project/clothes/config/SecurityConfig.java) khai báo route public, JWT, ADMIN; controller kiểm tra permission. [Nginx](../gateway/nginx.conf) định tuyến auth/admin và giới hạn request auth. |

## Request

POST /api/v1/auth/register:

```json
{
  "email": "customer@example.com",
  "password": "Password-1234",
  "confirmPassword": "Password-1234",
  "fullName": "Nguyễn An",
  "phone": "0901234567",
  "dateOfBirth": "2000-01-02"
}
```

Email, password, confirmPassword, fullName bắt buộc. Phone và dateOfBirth có thể bỏ qua hoặc gửi null. Mật khẩu từ 8 đến 72 ký tự và tối đa 72 byte UTF-8; ngày sinh phải trong quá khứ. Trường không khai báo trong DTO bị từ chối, bao gồm id, roles, status và deleted. Đăng ký luôn cấp CUSTOMER và tạo customer profile cùng transaction.

POST /api/v1/auth/login:

```json
{"email":"customer@example.com","password":"Password-1234"}
```

Tài khoản admin do V3 tạo có email admin@example.com, alias đăng nhập admin và mật khẩu admin123:

```json
{"email":"admin","password":"admin123"}
```

Alias admin không phân biệt hoa/thường, được chuyển thành admin@example.com trước validation. Các tài khoản khác vẫn đăng nhập bằng email.

Hai endpoint trả data.accessToken, tokenType="Bearer", expiresIn=900 và account. Dùng Authorization: Bearer <accessToken> để gọi GET /api/v1/auth/me. Token chứa UUID account trong subject và được ký HS256. API không trả password/password_hash.

## Response và lỗi

```json
{
  "success": false,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "data": null,
  "timestamp": "2026-10-07T08:00:00Z",
  "requestId": "fd4306af-4c4a-4707-80b5-4db32bd2b1b4",
  "errors": {"email": "must be a well-formed email address"}
}
```

RequestId cũng có trong header X-Request-Id. Ngày sinh dùng yyyy-MM-dd; timestamp DTO là UTC, yyyy-MM-dd'T'HH:mm:ssXXX. errors chỉ xuất hiện khi có lỗi theo field.

| HTTP | Code | Trường hợp |
| --- | --- | --- |
| 201 | REGISTER_SUCCESS | Đăng ký thành công |
| 200 | LOGIN_SUCCESS / PROFILE_SUCCESS | Đăng nhập / xem profile |
| 400 | VALIDATION_FAILED / INVALID_REQUEST / INVALID_ARGUMENT | DTO không hợp lệ, JSON sai hoặc vượt giới hạn byte mật khẩu |
| 401 | INVALID_CREDENTIALS | Email/mật khẩu sai, account không ACTIVE hoặc đã xóa mềm |
| 401 | UNAUTHORIZED | Thiếu JWT, JWT không hợp lệ hoặc tài khoản không còn được phép sử dụng |
| 403 | FORBIDDEN | Thiếu role/permission |
| 404 | RESOURCE_NOT_FOUND | Tài khoản không tồn tại hoặc đã xóa mềm |
| 409 | EMAIL_ALREADY_REGISTERED | Email trùng, không phân biệt hoa/thường, kể cả account đã xóa mềm |
| 429 | RATE_LIMITED | Gateway giới hạn request đăng ký/đăng nhập |

## Tài khoản quản trị

V3 tạo account admin@example.com, admin profile và liên kết role ADMIN theo thông tin được yêu cầu; mật khẩu được băm BCrypt cost 12. Khi chạy lại SQL seed, account đã được seed không bị tạo trùng hoặc đặt lại mật khẩu. Nếu email đã thuộc account khác, migration báo lỗi và rollback, không thay đổi credential/quyền có sẵn.

Để cấp thêm ADMIN cho tài khoản đã đăng ký, người quản lý database dùng SQL sau và thay email bằng tài khoản cần cấp quyền:

```sql
INSERT INTO account_roles(account_id, role_id)
SELECT a.id, r.id
FROM accounts a CROSS JOIN roles r
WHERE lower(a.email) = lower('another-admin@example.com')
  AND a.deleted = FALSE AND a.status = 'ACTIVE'
  AND r.code = 'ADMIN' AND r.deleted = FALSE
ON CONFLICT (account_id, role_id) DO NOTHING;
```

V2 seed CUSTOMER, ADMIN, AUTH_PROFILE_READ và ACCOUNT_READ; V3 seed account admin. ADMIN có cả hai permission. GET /api/v1/admin/accounts yêu cầu ADMIN + ACCOUNT_READ và trả data.content, page, size, totalElements, totalPages; page từ 0, size 1–50, mặc định 20. Có thể lọc status và search theo email. GET /api/v1/admin/accounts/{id} có cùng yêu cầu quyền.

Mỗi request được xác thực tải quyền hiện tại từ DB. Xóa role/permission khỏi quan hệ, đánh dấu deleted hoặc khóa account có hiệu lực ngay với request tiếp theo, dù JWT còn hạn. JWT sống 15 phút; chưa triển khai refresh token hay endpoint logout.

## Gateway

Compose auth dùng Nginx làm gateway tại http://localhost:8081 và backend:8080 trong mạng Docker. Nó chuyển nguyên đường dẫn và header Authorization đến backend; chỉ chuyển các nhóm auth/admin và /actuator/health. Chạy bằng lệnh trong README sau khi build JAR và cấu hình JWT_SECRET.

Nginx giới hạn register/login theo IP: 10 request/phút, cho burst thêm 10, trả JSON 429 cùng Retry-After. Giới hạn này nằm ở gateway; khi chạy Maven trực tiếp trên cổng 8080, request không đi qua Nginx. Lỗi upstream trả JSON 502. Khi triển khai ngoài local, cấu hình HTTPS tại ingress/gateway của môi trường.

Cấu hình proxy dựa trên tài liệu chính thức [Nginx proxy module](https://nginx.org/en/docs/http/ngx_http_proxy_module.html) và [limit_req module](https://nginx.org/en/docs/http/ngx_http_limit_req_module.html).

## Kiểm thử

Chạy .\mvnw.cmd clean verify. AuthIntegrationTests dùng PostgreSQL embedded riêng, MockMvc và Spring Security thật; kiểm tra migration/JPA, validation, BCrypt, duplicate/race registration, timestamp, JWT claims, xóa mềm, thay đổi quyền, phân trang và khớp OpenAPI với route controller. ArchitectureTests kiểm tra MVC và không phụ thuộc vòng.
