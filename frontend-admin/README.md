# LemonadeX Admin

Chạy backend theo [README gốc](../README.md), sau đó ở thư mục `frontend-admin`:

```powershell
npm.cmd ci
npm.cmd run dev
```

Mở http://localhost:3001/login. Tài khoản admin được Flyway tạo trong database: tên đăng nhập `admin` hoặc email `admin@example.com`, mật khẩu `admin123`.

Frontend gọi `POST /api/v1/auth/login`, đọc `ApiResponse.data.account` và chỉ chấp nhận tài khoản ACTIVE có role ADMIN. JWT từ `data.accessToken` được lưu tại `lemonadex.admin.token` và gửi qua `Authorization: Bearer <token>`. Khi tải lại trang, `GET /api/v1/auth/me` kiểm tra tài khoản và quyền hiện tại. Đăng xuất, phiên không hợp lệ hoặc lỗi 401 của phiên hiện tại xóa token; lỗi 403 hiển thị lỗi và giữ phiên. Không còn đăng nhập mock khi backend không chạy. Nếu localStorage không khả dụng, phiên chỉ giữ trong bộ nhớ đến khi tải lại trang.

Vite chuyển `/api` tới `http://localhost:8080`. Có thể sao chép `.env.example` thành `.env.local` rồi đổi `VITE_API_PROXY_TARGET`. `VITE_API_BASE_URL` để trống khi dùng proxy; nếu gọi backend trực tiếp, đặt origin backend và cho phép origin frontend trong `CORS_ALLOWED_ORIGINS`. Bản build cần reverse proxy `/api` hoặc cấu hình backend origin trước khi build.

## Kiểm chứng

```powershell
npm.cmd run build
npx.cmd playwright install chromium --only-shell
npm.cmd test
```

Test Chromium dùng fixture đúng contract backend, kiểm tra cả admin và khách hàng: request đăng nhập/đăng ký, JWT, reload, đăng xuất, quyền ADMIN, lỗi 401/403, backend không khả dụng và response sai. Test này không ghi database; cần cài dependencies ở cả hai thư mục frontend.

Để chạy trình duyệt với backend thật và PostgreSQL test riêng, ở thư mục gốc:

```powershell
$env:RUN_FRONTEND_AUTH_E2E='true'
.\mvnw.cmd verify
Remove-Item Env:RUN_FRONTEND_AUTH_E2E
```

Suite này tự chạy Spring Boot ở cổng ngẫu nhiên, Vite ở 3400/3401, rồi kiểm tra admin và khách hàng bằng HTTP thật. Cần Node, dependencies của cả hai frontend và Chromium đã cài. Nó không dùng database ứng dụng; log tại `target/frontend-auth-e2e.log`. Các API sản phẩm, giỏ hàng và đơn hàng chưa có trong backend hiện tại; test tập trung vào xác thực.
