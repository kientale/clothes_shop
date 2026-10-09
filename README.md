# LemonadeX Clothes Backend

Backend thời trang dùng Java 25, Spring Boot 4.1.1, PostgreSQL, Flyway và Spring Security JWT. Chức năng hiện có: xác thực, quản lý tài khoản admin, vai trò, khách hàng, catalog sản phẩm, kho hàng, đơn hàng, đánh giá và Marketing. V1 tạo schema; V2–V7 bổ sung auth, quản trị, file, catalog và kho/đơn; V8 bổ sung đánh giá sau mua, coupon, khuyến mãi, Flash Sale, banner và thông báo.

Backend tổ chức theo feature: `auth`, `account`, `role`, `customer`, `file`, `catalog`, `inventory`, `order`, `review`, `marketing`, `content`, `report`, `settings` và thành phần dùng chung `common`. Mỗi feature giữ luồng Controller → Service → Repository; JSON response là View của REST API. Entity dùng BaseEntity hoặc CreatedEntity cho lịch sử bền vững, request/response DTO riêng và ApiResponse<T>. Xem [kiến trúc feature](docs/architecture.md).

## Chạy local

Cần JDK 25 và PostgreSQL 14+; có thể khởi động PostgreSQL qua Docker Desktop:

```powershell
docker compose up -d postgres
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

Backend: http://localhost:8080. Health: http://localhost:8080/actuator/health.

Profile local dùng database clothes, user clothes, password clothes_local và JWT secret dành cho phát triển. Profile mặc định yêu cầu DB_PASSWORD và JWT_SECRET; secret có ít nhất 32 byte UTF-8. Có thể đổi DB_URL, DB_USERNAME, JWT_ISSUER và CORS_ALLOWED_ORIGINS qua biến môi trường.

Ứng dụng tự đọc file `.env` ở thư mục gốc qua `spring.config.import`; biến môi trường thật vẫn được ưu tiên. `.env.example` có mẫu Supabase và Docker local; điền mật khẩu và JWT secret vào `.env` (git bỏ qua file này). Khi chạy IDE, đặt working directory là thư mục chứa `pom.xml` và `.env`.

## Kết nối Supabase

Dùng Session pooler đã được cung cấp cho project này:

```properties
DB_URL=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require&connectTimeout=10&tcpKeepAlive=true
DB_USERNAME=postgres.wfhwedjxlgpggrlcfhkh
DB_PASSWORD=<mật khẩu database gốc>
DB_POOL_SIZE=5
```

Chạy `.\mvnw.cmd spring-boot:run` ở thư mục gốc sau khi điền `.env`. JDBC URL không chứa username/password; `DB_PASSWORD` nhận nguyên mật khẩu, không đổi `@` thành `%40`. `sslmode=require` yêu cầu mã hóa giữa JDBC client và pooler. Session pooler dùng cổng 5432 và hỗ trợ prepared statements; giới hạn pool phụ thuộc cấu hình/gói Supabase. Xem [tài liệu Supabase](https://supabase.com/docs/guides/database/connecting-to-postgres) và [PostgreSQL JDBC](https://jdbc.postgresql.org/documentation/use/).

Lỗi `UnknownHostException: aws-0-REGION.pooler.supabase.com` nghĩa là ứng dụng vẫn đọc hostname mẫu. Kiểm tra `.env` và biến môi trường `DB_URL` trong terminal/Run Configuration; biến môi trường thật được ưu tiên hơn `.env`.

Flyway tự chạy V1–V9; Hibernate chỉ dùng ddl-auto: validate. Xem [migration PostgreSQL](docs/database-migration.md).

## API

| Method | Endpoint | Quyền |
| --- | --- | --- |
| POST | /api/v1/auth/register | Công khai; luôn tạo CUSTOMER |
| POST | /api/v1/auth/login | Công khai |
| GET | /api/v1/auth/me | JWT + AUTH_PROFILE_READ |
| GET | /api/v1/admin/accounts | ADMIN + ACCOUNT_READ |
| GET | /api/v1/admin/accounts/{id} | ADMIN + ACCOUNT_READ |

CRUD admin tại `/api/v1/admin/admin-accounts`, vai trò tại `/api/v1/admin/roles`, khách hàng tại `/api/v1/admin/customers`; danh mục quyền tại `/api/v1/admin/permissions`. Xem [hướng dẫn quản lý và phân quyền](docs/admin-management.md).

CRUD quản lý sản phẩm tại `/api/v1/admin/products`, `/categories`, `/brands`, `/product-variants`, `/sizes`, `/colors`, `/collections`. Có tìm kiếm/lọc/phân trang, ảnh có thứ tự, tạo biến thể hàng loạt và xóa mềm. Xem [hướng dẫn backend quản lý sản phẩm](docs/catalog-management.md).

API kho/đơn tại `/api/v1/admin/warehouses`, `/inventory`, `/inventory/transactions`, `/inventory/history`, `/orders`, `/payments`, `/shipments`, `/shipments/history`, `/returns`, `/refunds`. Có giữ/xuất/nhập hàng theo đơn, lịch sử và kiểm soát số lượng/tiền khi thao tác đồng thời. COD/chuyển khoản/hoàn tiền do admin ghi nhận; VNPay/MoMo tự xác nhận qua IPN (xem bên dưới). Xem [backend kho và đơn hàng](docs/inventory-orders.md).

API khách hàng/Marketing có CRUD mã giảm giá, khuyến mãi, Flash Sale, banner, duyệt đánh giá sau mua và phát hành thông báo vào hộp thư khách hàng. Coupon và suất Flash Sale được kiểm soát trong transaction tạo/hủy đơn. Xem [backend khách hàng và Marketing](docs/customer-marketing.md).

Nội dung có CRUD bài viết/Lookbook, xuất bản công khai và chính sách có phiên bản. Báo cáo hỗ trợ doanh thu, đơn hàng, bán chạy, tồn kho, khách hàng, đổi/trả và khuyến mãi. Xem [backend nội dung và báo cáo](docs/content-reports.md); migration V9 bổ sung dữ liệu và quyền tương ứng.

Cài đặt hệ thống có 6 nhóm cấu hình, CRUD phương thức thanh toán/giao hàng, revision và audit. Xem [backend cài đặt hệ thống](docs/system-settings.md); migration V10 bổ sung cấu hình và quyền.

Cửa hàng có thêm đặt hàng không cần tài khoản, đổi/trả do khách gửi, thanh toán VNPay/MoMo, giao hàng GHTK, xác nhận email, đăng nhập Google/Facebook, giỏ hàng lưu trên server, bảng size, báo khi có hàng, gợi ý tìm kiếm và nút chat Zalo/Messenger. Cổng thanh toán, GHTK và đăng nhập mạng xã hội chỉ chạy khi đặt khóa trong `.env` (xem `.env.example`). Xem [mở rộng cửa hàng](docs/storefront-extensions.md); migration V12 bổ sung bảng và quyền.

[Hướng dẫn auth và checklist](docs/authentication.md), [OpenAPI](docs/openapi.json), [request HTTP mẫu](docs/api.http), [kiến trúc MVC](docs/architecture.md).

Frontend [quản trị](frontend-admin/README.md) và [khách hàng](frontend-user/README.md) đăng nhập qua backend `/api/v1/auth/login`, lưu JWT và khôi phục phiên qua `/api/v1/auth/me`. Admin dùng `admin/admin123` hoặc `admin@example.com/admin123`; tài khoản cần role ADMIN để vào giao diện quản trị.

## Kiểm thử và đóng gói

```powershell
.\mvnw.cmd clean verify
```

Test tự khởi động PostgreSQL 14.22 riêng, chạy cả mười migration và validate JPA. Không cần Docker hoặc database của ứng dụng. Có kiểm tra feature/MVC bằng ArchUnit, validation, BCrypt, JWT, CRUD quản lý, quyền hiện tại trong database, xóa mềm, kho/đơn/đổi trả/hoàn tiền, đánh giá, Marketing, nội dung, báo cáo, thao tác đồng thời và contract OpenAPI. PostgreSQL test được dừng khi JVM kết thúc.

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
