# Migration PostgreSQL cho e-commerce thời trang

File Flyway: `src/main/resources/db/migration/V1__init_fashion_ecommerce.sql`.

Migration khởi tạo database trống với đúng 50 bảng và các cột trong mô hình đã cung cấp. Các bảng được tạo theo thứ tự phụ thuộc; không có dữ liệu tài khoản, role, permission hay phương thức thanh toán mặc định.

## Quy ước

- Khóa chính `id`: UUID, mặc định `gen_random_uuid()`. Các bảng liên kết nhiều-nhiều dùng khóa chính ghép.
- Thời gian: `TIMESTAMPTZ`; ngày sinh: `DATE`; tiền: `NUMERIC(18,2)`; số lượng: `INTEGER`.
- `payment_methods.configuration`, `payment_transactions.raw_response`, `audit_logs.old_data/new_data`: `JSONB`. `audit_logs.ip_address`: `INET`.
- Status và type dùng mã `VARCHAR`; ứng dụng quản lý vòng đời. `discount_type` của coupon/promotion nhận `PERCENTAGE` hoặc `FIXED_AMOUNT`.
- `customers.account_id` cho phép NULL để hỗ trợ khách chưa có tài khoản; account có tối đa một customer profile và một admin profile.
- Các trường `created_by`, `changed_by`, `processed_by`, `updated_by`, `author_id` tham chiếu `accounts.id`. Actor tự động có thể NULL, riêng tác giả bài viết bắt buộc có account.
- `reference_id` và `entity_id` là UUID tham chiếu nhiều loại thực thể nên không có khóa ngoại trực tiếp.
- Các bảng có `updated_at` được cập nhật tự động bằng trigger. Không bổ sung cột ngoài danh sách được cung cấp.

PostgreSQL cung cấp hàm sinh UUID này trực tiếp; migration không cần extension riêng. Xem [PostgreSQL UUID functions](https://www.postgresql.org/docs/17/functions-uuid.html).

## Ràng buộc nghiệp vụ

- Email và mã coupon không trùng khi chỉ khác hoa/thường; mã sản phẩm, SKU, slug và mã RBAC có unique constraint.
- Variant là duy nhất theo sản phẩm/màu/size. Ảnh variant phải thuộc đúng sản phẩm; mỗi sản phẩm có tối đa một ảnh chính chung và mỗi variant có tối đa một ảnh chính riêng.
- Mỗi khách có tối đa một địa chỉ mặc định.
- Một dòng tồn kho cho mỗi kho/variant; `0 <= quantity_reserved <= quantity_on_hand`. Số có thể bán bằng `quantity_on_hand - quantity_reserved`.
- Inventory transaction phải gắn với dòng tồn kho có thật. `quantity` khác 0 và có thể âm; ứng dụng xác định ý nghĩa theo `transaction_type` và ghi ledger cùng transaction cập nhật tồn kho.
- `orders.total_amount = subtotal - discount_amount + shipping_fee`. `subtotal` là giá trước giảm; `discount_amount` là tổng giảm toàn đơn, bao gồm phần giảm của các dòng.
- `order_items.total_amount = unit_price * quantity - discount_amount`; số lượng dương, tiền không âm, giảm giá không vượt giá trị hàng.
- Return request và coupon usage phải thuộc cùng customer với đơn hàng.
- Rating từ 1 đến 5; mỗi dòng mua hàng có tối đa một review. Phần kiểm tra sản phẩm/customer của review khớp giao dịch mua do service thực hiện.
- Khoảng thời gian khuyến mãi hợp lệ; giảm theo phần trăm không vượt 100%; lượt dùng coupon và số đã bán flash sale không vượt giới hạn.
- Giao dịch provider và mã tracking chống trùng theo provider; mỗi loại policy có tối đa một bản ACTIVE.
- Dữ liệu đơn, thanh toán, ledger và lịch sử có khóa ngoại giữ tính toàn vẹn. Các bảng nối và ảnh/profile phụ thuộc dùng cascade tại những quan hệ được khai báo.

Service cần xử lý trong transaction các quy tắc tổng hợp nhiều dòng: tổng đơn khớp các dòng hàng, số lượng đổi trả không vượt lượng mua, refund khớp đơn/thanh toán và không vượt số tiền được hoàn, giới hạn coupon theo customer và đồng thời khi giữ tồn kho. Migration không tự tạo giao dịch thanh toán hoặc tự điều phối những workflow này.

## Chạy và kiểm chứng

Spring Boot đang bật Flyway và dùng location mặc định `classpath:db/migration`, nên file V1 được chạy khi ứng dụng kết nối database mới. Schema này dành cho PostgreSQL 14 trở lên; có thể chạy trực tiếp trên database trống bằng:

```powershell
psql -h localhost -p 5432 -U clothes -d clothes -v ON_ERROR_STOP=1 --single-transaction -f src/main/resources/db/migration/V1__init_fashion_ecommerce.sql
```

Chọn một cách quản lý schema: Flyway hoặc chạy SQL trực tiếp. Chạy SQL trực tiếp không ghi lịch sử migration của Flyway.

File kiểm chứng: `src/test/resources/db/verification/fashion_schema_assertions.sql`. Chạy sau V1 trên database riêng dành cho test:

```powershell
psql -h localhost -p 5432 -U clothes -d clothes_test -v ON_ERROR_STOP=1 -f src/test/resources/db/verification/fashion_schema_assertions.sql
```

File kiểm chứng đối chiếu tên/cột của 50 bảng, chèn dữ liệu liên kết hợp lệ, kiểm tra trigger và 16 thao tác sai phải bị từ chối. Toàn bộ fixture được rollback.

Đã thực thi V1 và file kiểm chứng trên PostgreSQL 14.22 cục bộ: 50 bảng, 60 khóa ngoại, 153 index và 8 trigger cập nhật thời gian. Đây là kết quả kiểm chứng schema V1 độc lập.

## V2: auth và xóa mềm

`V2__authentication_soft_delete_and_roles.sql` bổ sung `deleted` cho accounts, customers, roles và permissions; bổ sung timestamp còn thiếu để bốn entity kế thừa BaseEntity. Các cột mới có giá trị mặc định cho dữ liệu V1 sẵn có. V2 thêm index cho bản ghi chưa xóa và trigger cập nhật timestamp.

V2 seed CUSTOMER, ADMIN, AUTH_PROFILE_READ và ACCOUNT_READ cùng role_permissions; dùng ON CONFLICT để tương thích với dữ liệu RBAC đã có. Không seed account hay mật khẩu admin. Unique index email ở V1 vẫn giữ email của account đã xóa mềm.

Flyway chạy V1 rồi V2, Hibernate dùng `ddl-auto: validate`. Bộ test Java hiện tại tự khởi động PostgreSQL 14.22 riêng và kiểm chứng cả hai migration cùng mapping JPA khi chạy `.\mvnw.cmd clean verify`.

File `fashion_schema_assertions.sql` đối chiếu **đúng các cột V1**, nên chỉ chạy trên database test sau V1, trước V2. Kiểm thử auth/V2 nằm trong `AuthIntegrationTests` và `ClothesApplicationTests`.

## V3: tài khoản admin

`V3__seed_admin_account.sql` tạo account admin@example.com với mật khẩu admin123 đã băm BCrypt (cost 12), liên kết role ADMIN và admin profile. Có thể đăng nhập bằng alias admin. Flyway chạy V3 sau V2 khi ứng dụng kết nối PostgreSQL.

SQL seed có thể chạy lại mà không tạo account/profile/role link trùng. Email của account khác không bị ghi đè hoặc tự nâng quyền; nếu trùng, SQL báo lỗi và rollback. Các test auth kiểm tra đăng nhập bằng alias/email và truy cập route admin.
