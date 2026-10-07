# LemonadeX Clothes Backend

Backend thời trang dùng Java 25, Spring Boot 4.1.1, PostgreSQL, Flyway và Spring Security JWT. Chức năng hiện có: đăng ký, đăng nhập, xem tài khoản hiện tại và danh sách tài khoản dành cho quản trị viên. Schema V1 gồm 50 bảng e-commerce; V2 bổ sung xóa mềm và dữ liệu phân quyền; V3 tạo tài khoản admin theo yêu cầu.

Code tổ chức theo MVC: Controller → Service → Repository; JSON response là View của REST API. Entity kế thừa BaseEntity, request/response DTO riêng, MapStruct và ApiResponse<T>.

## Chạy local

Cần JDK 25 và PostgreSQL 14+; có thể khởi động PostgreSQL qua Docker Desktop:

```powershell
docker compose up -d postgres
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

Backend: http://localhost:8080. Health: http://localhost:8080/actuator/health.

Profile local dùng database clothes, user clothes, password clothes_local và JWT secret dành cho phát triển. Profile mặc định yêu cầu DB_PASSWORD và JWT_SECRET; secret có ít nhất 32 byte UTF-8. Có thể đổi DB_URL, DB_USERNAME, JWT_ISSUER và CORS_ALLOWED_ORIGINS qua biến môi trường. Spring Boot không tự đọc file .env khi chạy Maven/Java trực tiếp.

Flyway tự chạy V1, V2, V3; Hibernate chỉ dùng ddl-auto: validate. Xem [migration PostgreSQL](docs/database-migration.md).

## API

| Method | Endpoint | Quyền |
| --- | --- | --- |
| POST | /api/v1/auth/register | Công khai; luôn tạo CUSTOMER |
| POST | /api/v1/auth/login | Công khai |
| GET | /api/v1/auth/me | JWT + AUTH_PROFILE_READ |
| GET | /api/v1/admin/accounts | ADMIN + ACCOUNT_READ |
| GET | /api/v1/admin/accounts/{id} | ADMIN + ACCOUNT_READ |

[Hướng dẫn auth và checklist](docs/authentication.md), [OpenAPI](docs/openapi.json), [request HTTP mẫu](docs/api.http), [kiến trúc MVC](docs/architecture.md).

## Kiểm thử và đóng gói

```powershell
.\mvnw.cmd clean verify
```

Test tự khởi động PostgreSQL 14.22 riêng, chạy cả ba migration và validate JPA. Không cần Docker hoặc database của ứng dụng. Có kiểm tra MVC bằng ArchUnit, validation, BCrypt, JWT, quyền hiện tại trong database, xóa mềm, đăng ký đồng thời, tài khoản admin được seed và contract OpenAPI. PostgreSQL test được dừng khi JVM kết thúc.

JAR: target/clothes-0.0.1-SNAPSHOT.jar.

## Chạy qua gateway

Sau khi build JAR, tạo .env từ .env.example và đặt JWT_SECRET riêng, rồi chạy:

```powershell
docker compose -f compose.yaml -f compose.auth.yaml up -d --build
```

Gateway: http://localhost:8081. Backend trong Docker chỉ mở cổng nội bộ; gateway chuyển Authorization đến Spring Security. Nginx giới hạn request đăng ký/đăng nhập theo IP. Xem chi tiết trong [tài liệu auth](docs/authentication.md).

Mật khẩu database phía ứng dụng phải khớp với PostgreSQL. Biến POSTGRES_* chỉ có tác dụng khởi tạo khi volume còn trống.

## Tài khoản quản trị

Migration V3 tạo tài khoản có email **admin@example.com**, tên đăng nhập **admin**, mật khẩu **admin123** và role ADMIN. Mật khẩu lưu bằng BCrypt, cost 12. Tài khoản được insert khi Flyway chạy; migration không ghi đè hoặc cấp quyền cho account khác đã dùng email này.

Đăng nhập bằng `{"email":"admin","password":"admin123"}` tại POST /api/v1/auth/login. Có thể dùng admin@example.com thay cho admin. Để cấp thêm tài khoản quản trị, dùng SQL trong [authentication.md](docs/authentication.md). Thay đổi quyền hoặc trạng thái account có hiệu lực với request JWT tiếp theo.
