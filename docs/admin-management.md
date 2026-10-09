# Quản lý admin, vai trò và khách hàng

Flyway V4 bổ sung xóa mềm/timestamp cho `admin_profiles`, index và quyền quản lý; giữ V1–V3. Đăng nhập `POST /api/v1/auth/login` bằng `{"email":"admin","password":"admin123"}`; gửi `Authorization: Bearer <data.accessToken>`. Frontend quản trị đăng nhập qua API này và tự gửi JWT cho các request được bảo vệ.

| Endpoint | Method | Permission (ngoài role ADMIN) |
| --- | --- | --- |
| `/api/v1/admin/admin-accounts` | GET / POST | ACCOUNT_READ / ACCOUNT_WRITE |
| `/api/v1/admin/admin-accounts/{id}` | GET / PUT / DELETE | ACCOUNT_READ / ACCOUNT_WRITE |
| `/api/v1/admin/admin-accounts/{id}/password` | PUT | ACCOUNT_WRITE |
| `/api/v1/admin/roles` | GET / POST | ROLE_READ / ROLE_WRITE |
| `/api/v1/admin/roles/{id}` | GET / PUT / DELETE | ROLE_READ / ROLE_WRITE |
| `/api/v1/admin/permissions` | GET | ROLE_READ |
| `/api/v1/admin/customers` | GET / POST | CUSTOMER_READ / CUSTOMER_WRITE |
| `/api/v1/admin/customers/{id}` | GET / PUT / DELETE | CUSTOMER_READ / CUSTOMER_WRITE |
| `/api/v1/admin/customers/summary` | GET | CUSTOMER_READ |
| `/api/v1/admin/customers/linkable-accounts` | GET | CUSTOMER_WRITE |
| `/api/v1/admin/uploads/avatars` | POST (multipart) | ACCOUNT_WRITE hoặc CUSTOMER_WRITE |
| `/api/v1/files/{id}` | GET | Công khai, không cần JWT |
| `/api/v1/admin/uploads/images` | POST (multipart) | PRODUCT_WRITE |
| `/api/v1/admin/categories`, `/brands`, `/colors`, `/sizes`, `/collections`, `/products`, `/product-variants` | GET / POST | PRODUCT_READ / PRODUCT_WRITE |
| `…/{id}` của các route trên | GET / PUT / DELETE | PRODUCT_READ / PRODUCT_WRITE |
| `/api/v1/admin/product-variants/bulk` | POST | PRODUCT_WRITE |
| `/api/v1/admin/catalog/options` | GET | PRODUCT_READ |

GET danh sách nhận `page` từ 0, `size` 1–50 (mặc định 20), `search` tối đa 254 ký tự. Tìm kiếm không phân biệt hoa/thường; `%`, `_`, `\` là ký tự thường. Admin tìm theo email/họ tên; vai trò theo tên/code; khách hàng theo họ tên/phone/email tài khoản liên kết. Admin/khách hàng lọc `status`; danh mục quyền lọc `module`. Response có `data.content`, `page`, `size`, `totalElements`, `totalPages`, sắp xếp mới nhất rồi UUID.

API `/api/v1/admin/accounts` cũ vẫn đọc mọi tài khoản. API `/api/v1/admin/admin-accounts` chỉ quản lý tài khoản có ADMIN, kể cả chưa có hồ sơ; PUT sẽ tạo hồ sơ thiếu.

## Tài khoản admin

POST nhận email, password, fullName, phone, avatarUrl và roleIds:

```json
{"email":"manager@example.com","password":"Password-1234","fullName":"Nguyễn An","phone":"0901234567","avatarUrl":null,"roleIds":["<UUID role ADMIN từ GET /api/v1/admin/roles>"]}
```

Tài khoản mới ACTIVE; email chuẩn hóa chữ thường; password 8–72 ký tự, tối đa 72 byte UTF-8, băm BCrypt. RoleIds bắt buộc, 1–50 UUID hiện có, phải chứa ADMIN. Response không chứa password/hash.

PUT `/{id}` thay thế email, fullName, phone, avatarUrl, status, roleIds; không nhận password. Status: ACTIVE/INACTIVE/LOCKED/SUSPENDED. PUT `/{id}/password` nhận `{"password":"Changed-5678"}`. Password đổi không thu hồi JWT đã cấp; khóa/xóa account có hiệu lực ở request tiếp theo.

DELETE xóa mềm account và hồ sơ. Không tự khóa/xóa account đang thao tác hoặc làm mất admin ACTIVE cuối cùng; kiểm tra trong transaction có khóa để xử lý đồng thời. Email đã xóa không được dùng lại.

## Vai trò

POST nhận name, code, permissionIds:

```json
{"name":"Chăm sóc khách hàng","code":"CUSTOMER_SUPPORT","permissionIds":["<UUID CUSTOMER_READ từ GET /api/v1/admin/permissions>"]}
```

Code 2–50 ký tự, bắt đầu chữ hoa, chỉ chứa chữ hoa/số/`_`; không trùng kể cả code đã xóa, không phân biệt hoa/thường. PermissionIds bắt buộc, có thể rỗng, tối đa 100 UUID quyền hiện có. PUT nhận name/permissionIds; code không đổi. ADMIN/CUSTOMER không cho sửa/xóa; vai trò còn được gán cho account chưa xóa không cho xóa. Quyền tùy chỉnh được cập nhật ngay trên JWT đang dùng. Permission chỉ có API đọc danh mục.

## Khách hàng

POST:

```json
{"accountId":null,"fullName":"Trần Bình","phone":"0912345678","gender":"MALE","dateOfBirth":"1995-05-20","avatarUrl":null,"status":"ACTIVE"}
```

AccountId có thể null để tạo khách không có tài khoản. Khi liên kết, account phải tồn tại, có CUSTOMER, không có ADMIN và chưa có hồ sơ, kể cả hồ sơ đã xóa. Khách đăng ký public đã có hồ sơ, dùng PUT để sửa. PUT nhận các trường hồ sơ như POST, không nhận accountId; liên kết không thay đổi.

Response khách hàng có `email` của tài khoản liên kết; `null` với khách vãng lai hoặc khi tài khoản đã bị xóa mềm.

`GET /summary` trả `total`, `active`, `inactive`, `blocked` của hồ sơ chưa xóa. `GET /linkable-accounts?search=&size=` (size 1–20, mặc định 10) trả `id`, `email`, `createdAt` của tài khoản có CUSTOMER, không có ADMIN và chưa từng có hồ sơ (kể cả hồ sơ đã xóa), dùng để chọn `accountId` khi tạo hồ sơ.

FullName/status bắt buộc; status: ACTIVE/INACTIVE/BLOCKED. Gender null hoặc MALE/FEMALE/OTHER; ngày sinh trong quá khứ. Phone 8–15 chữ số, có thể bắt đầu `+`; avatar HTTP/HTTPS, tối đa 2048 ký tự. DELETE chỉ xóa mềm hồ sơ, giữ account đăng nhập và dữ liệu liên quan. Trạng thái hồ sơ không thay đổi trạng thái đăng nhập.

## Ảnh đại diện

`POST /api/v1/admin/uploads/avatars` nhận multipart field `file`: JPEG, PNG, WebP hoặc GIF, tối đa 2 MB (`app.uploads.max-avatar-size`). Định dạng được xác định từ chữ ký byte của file, không tin `Content-Type` của client. Response 201 có `id`, `url`, `contentType`, `size`; gán `url` vào `avatarUrl` khi tạo/sửa admin hoặc khách hàng. Lỗi: `EMPTY_FILE`, `UNSUPPORTED_FILE_TYPE`, `FILE_TOO_LARGE` (400), hoặc 413 khi vượt giới hạn multipart.

File lưu trong bảng `uploaded_files` (Flyway V5, cột `bytea`) nên không mất khi deploy lại server. `GET /api/v1/files/{id}` trả ảnh công khai với `nosniff`, CSP sandbox và cache 1 năm (`immutable`). `url` là đường dẫn tuyệt đối: origin lấy từ `UPLOADS_PUBLIC_BASE_URL` nếu có, nếu không thì từ origin của request upload (qua Vite proxy là origin của frontend). Ảnh không còn được hồ sơ nào dùng hiện chưa bị dọn tự động.

## Catalog sản phẩm

Chi tiết trường dữ liệu, luồng tạo sản phẩm/biến thể và request mẫu tại [hướng dẫn backend quản lý sản phẩm](catalog-management.md).

Flyway V6 thêm xóa mềm/timestamp cho categories, brands, colors, sizes, collections, products, product_variants và quyền `PRODUCT_READ`/`PRODUCT_WRITE` (cấp cho ADMIN). Mọi danh sách nhận `search`, `status`, `page`, `size` như các màn quản lý khác; sản phẩm lọc thêm `brandId`, `categoryId`, `gender`; biến thể lọc `productId`, `colorId`, `sizeId`.

- Slug để trống được sinh từ tên (bỏ dấu tiếng Việt, `đ` thành `d`); mã màu/size, mã sản phẩm, SKU lưu chữ hoa. Slug/mã/SKU duy nhất kể cả bản ghi đã xóa: `SLUG_EXISTS`, `CODE_EXISTS`, `PRODUCT_CODE_EXISTS`, `SKU_EXISTS` (409).
- Danh mục: không đặt cha tạo vòng lặp (`CATEGORY_CYCLE`, 400); không xóa khi còn danh mục con (`CATEGORY_HAS_CHILDREN`) hoặc sản phẩm (`CATEGORY_IN_USE`). Thương hiệu/màu/size không xóa khi còn được dùng (`BRAND_IN_USE`, `COLOR_IN_USE`, `SIZE_IN_USE`).
- Sản phẩm: `images` là danh sách có thứ tự, ảnh đầu là ảnh chính; PUT thay toàn bộ. Response có `variantCount`, `minPrice`, `maxPrice` theo biến thể. Xóa sản phẩm xóa mềm cả biến thể.
- Biến thể: product/color/size không đổi sau khi tạo; SKU trống thành `MÃSP-MÃMÀU-MÃSIZE` (mã dài dùng hậu tố từ UUID tổ hợp để không mất phần phân biệt khi giới hạn 100 ký tự). Tạo lại tổ hợp đã xóa sẽ khôi phục bản ghi cũ. `compareAtPrice` không nhỏ hơn `price` (`INVALID_COMPARE_PRICE`). `POST /product-variants/bulk` tạo mọi tổ hợp màu × size còn thiếu, rollback toàn bộ khi có lỗi.
- Bộ sưu tập: `productIds` có thứ tự, không trùng (`DUPLICATE_PRODUCTS`), `endAt` sau `startAt` (`INVALID_PERIOD`). Danh sách trả `productCount`; chi tiết trả `products`.
- `GET /catalog/options` trả toàn bộ danh mục, thương hiệu, màu, size chưa xóa (không phân trang) cho các ô chọn. Ảnh catalog upload qua `/uploads/images`, tối đa 5 MB.

## Response và kiểm thử

Backend kho và đơn hàng có các quyền riêng INVENTORY, ORDER, PAYMENT, SHIPMENT, RETURN, REFUND. Xem [endpoint, vòng đời và kiểm thử](inventory-orders.md).

Response dùng ApiResponse với requestId/timestamp UTC. POST: 201; GET/PUT/DELETE: 200; DELETE/đặt mật khẩu có `data: null`. Field không khai báo bị từ chối. Lỗi 400: dữ liệu sai; 401: JWT/account không hợp lệ; 403: thiếu quyền; 404: không tồn tại/đã xóa; 409: trùng dữ liệu, vai trò hệ thống/đang dùng, tự khóa hoặc admin cuối cùng.

[OpenAPI](openapi.json) chứa schema; [api.http](api.http) có request mẫu. `.\mvnw.cmd clean verify` kiểm tra migration/JPA, CRUD, phân quyền, xóa mềm, cạnh tranh xóa admin và contract API bằng PostgreSQL riêng.
