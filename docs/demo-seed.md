# Dữ liệu mẫu (demo seed)

[`src/main/resources/db/seed/R__demo_seed.sql`](../src/main/resources/db/seed/R__demo_seed.sql) là Flyway migration dạng repeatable, điền dữ liệu mẫu đầy đủ để chạy thử cửa hàng và trang quản trị:

| Nhóm | Nội dung |
| --- | --- |
| Cửa hàng | Thông tin liên hệ, giờ mở cửa; phí giao hàng theo cấu hình (30.000 ₫, miễn phí từ 499.000 ₫); 2 phương thức giao hàng; tên tiếng Việt và thông tin ngân hàng cho phương thức thanh toán |
| Catalog | 5 thương hiệu, 13 danh mục, 11 màu, 6 size, 63 sản phẩm cho nam, nữ, trẻ em (ảnh, mô tả, chất liệu), khoảng 300 biến thể |
| Kho | 2 kho (TP. Hồ Chí Minh, Hà Nội), tồn kho đầu kỳ có sổ kho; vài size đã hết hàng |
| Nội dung | Banner `HOME_HERO`, `INTRO_HERO`, `INTRO_STORY`; 3 Lookbook và 1 bài viết; 6 chính sách cửa hàng; 4 bộ sưu tập |
| Marketing | 3 mã giảm giá (`WELCOME10`, `FREESHIP30K`, `LXMEMBER50K`), 1 chương trình giảm 15% áo khoác |
| Khách hàng | 4 tài khoản khách kèm hồ sơ và địa chỉ, mật khẩu chung `Khachhang@2026` |
| Đơn hàng | 7 đơn: 4 hoàn tất, 1 đã giao, 1 đang giao, 1 chờ xác nhận; có vận đơn, thanh toán COD, lịch sử trạng thái và 9 đánh giá đã duyệt |

## Bật seed

Seed nằm ngoài thư mục migration chính nên mặc định không chạy (test và production không bị ảnh hưởng). Để bật, thêm vào `.env`:

```properties
FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed
```

rồi khởi động backend. Flyway chạy seed sau các migration V1-V10, ghi vào `flyway_schema_history` với tên `R__demo_seed.sql`, và chỉ chạy lại khi nội dung file thay đổi. Tắt seed bằng cách xóa dòng trên; dữ liệu đã tạo vẫn giữ nguyên và Flyway không báo lỗi.

Cũng có thể chạy tay (backend đã chạy migration ít nhất một lần):

```powershell
psql "postgresql://USER:PASSWORD@HOST:5432/postgres?sslmode=require" -v ON_ERROR_STOP=1 -f src/main/resources/db/seed/R__demo_seed.sql
```

## An toàn khi chạy lại

- Mọi bản ghi được tra theo slug, mã, SKU hoặc email trước khi tạo; chạy lại không nhân bản dữ liệu.
- Danh mục, màu, size đã có (cùng slug hoặc cùng tên) được dùng lại và không bị sửa.
- Cài đặt cửa hàng và giao hàng chỉ được điền khi còn ở giá trị mặc định ban đầu; thông tin admin đã nhập được giữ nguyên.
- Chính sách chỉ được thêm cho loại chưa có phiên bản đang áp dụng.
- Đơn mẫu (mã `LX-SEED0001` đến `LX-SEED0007`) chỉ được tạo một lần và bỏ qua đơn nào thiếu hàng ở kho TP. Hồ Chí Minh.

Sổ kho, số lượng giữ hàng, thanh toán và lịch sử đơn được ghi giống như khi thao tác qua API, nên báo cáo và màn hình quản trị hiển thị nhất quán. `DemoSeedIntegrationTests` bật seed qua Flyway trên PostgreSQL riêng, chạy lại thêm một lần và kiểm tra điều này qua API thật.

## Ảnh

Ảnh sản phẩm, banner và Lookbook là ảnh miễn phí từ [Pexels](https://www.pexels.com/license/), tải trực tiếp từ `images.pexels.com`. Thay bằng ảnh thật của cửa hàng trong trang quản trị khi có.
