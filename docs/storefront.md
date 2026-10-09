# Storefront API (cửa hàng)

API cho `frontend-user`. Phần catalog công khai, không cần đăng nhập; phần đơn hàng cần JWT của tài khoản khách (role `CUSTOMER`) có hồ sơ khách hàng ACTIVE.

## Catalog công khai (`/api/v1/store`)

| Endpoint | Mô tả |
| --- | --- |
| GET `/catalog` | Danh mục, thương hiệu, màu, size đang bật (dùng cho bộ lọc). |
| GET `/banners?position=HOME_HERO` | Banner đang bật và trong thời gian chạy, sắp theo vị trí rồi thứ tự. Trang chủ cửa hàng dùng vị trí `HOME_HERO`; trang giới thiệu dùng `INTRO_HERO` (ảnh hero) và `INTRO_STORY` (ảnh câu chuyện, tối đa 3). |
| GET `/products` | Danh sách sản phẩm đang bán, phân trang (`page` từ 0, `size` 1-60, mặc định 24). |
| GET `/products/{key}` | Chi tiết theo slug hoặc id: ảnh, biến thể kèm tồn khả dụng, điểm đánh giá và tối đa 10 đánh giá đã duyệt. |

Một sản phẩm được bán khi sản phẩm ACTIVE, chưa xóa và có ít nhất một biến thể ACTIVE với màu và size ACTIVE. Tồn khả dụng của biến thể là tổng `quantity_on_hand - quantity_reserved` trên các kho ACTIVE chưa xóa. Sản phẩm DRAFT, ẩn hoặc đã xóa trả 404.

Bộ lọc của `/products`: `search` (tên sản phẩm hoặc thương hiệu), `categoryId` (gồm cả danh mục con), `brandId`, `gender` (`MEN`, `WOMEN`, `UNISEX`, `KIDS`), `colorId`, `sizeId`, `minPrice`, `maxPrice` (so với giá thấp nhất), `inStock=true`, `sort` (`newest`, `price_asc`, `price_desc`, `name`). Mỗi thẻ sản phẩm có ảnh chính, ảnh thứ hai (hover), khoảng giá, các màu và cờ còn hàng. `compareAtPrice` chỉ có khi cao hơn giá bán cao nhất. Tên người đánh giá được rút gọn (ví dụ "Minh Thư N.").

Response catalog có `Cache-Control: public, max-age=30`.

## Đơn hàng của khách (`/api/v1/me/orders`)

| Endpoint | Mô tả |
| --- | --- |
| GET `/me/orders?status=&page=&size=` | Đơn của khách đang đăng nhập. |
| GET `/me/orders/{id}` | Đơn kèm các khoản thanh toán và vận đơn. |
| POST `/me/orders` | Đặt hàng (201). |
| POST `/me/orders/{id}/cancel` | Hủy đơn khi chưa được xác nhận. |

Body đặt hàng:

```json
{
  "recipientName": "Nguyễn Minh Thư",
  "recipientPhone": "0903418772",
  "shippingAddress": "214 Nguyễn Trãi, Quận 5, TP. Hồ Chí Minh",
  "note": null,
  "items": [{ "productVariantId": "<UUID>", "quantity": 2 }],
  "shippingMethodCode": "STANDARD",
  "paymentMethod": "COD",
  "couponCode": null
}
```

Khách hàng lấy từ JWT, không nhận từ body. Đặt hàng dùng chung luồng tạo đơn của admin (giá theo biến thể, khuyến mãi theo cài đặt mặc định, mã giảm giá, phí giao hàng theo phương thức, giữ hàng) nên mọi quy tắc trong [inventory-orders.md](inventory-orders.md), [customer-marketing.md](customer-marketing.md) và [system-settings.md](system-settings.md) vẫn áp dụng. Kho được chọn tự động: kho ACTIVE đầu tiên đủ hàng cho mọi dòng (ưu tiên kho còn nhiều hàng nhất); nếu không có kho nào đủ, trả 409 `INSUFFICIENT_STOCK`. Nếu chọn `paymentMethod`, một khoản thanh toán PENDING bằng tổng đơn được tạo cùng transaction; nhân viên xác nhận khi nhận tiền.

Đơn của khách khác trả 404. Hủy chỉ được khi đơn còn PLACED (409 `ORDER_NOT_CANCELLABLE` nếu đã xác nhận); hủy trả hàng giữ và hủy các khoản thanh toán đang chờ.

Giỏ hàng được lưu ở trình duyệt (`localStorage`), không lưu trên server. Trang giỏ hàng đọc lại sản phẩm để cập nhật giá và báo dòng hết hàng trước khi thanh toán.

## Kiểm thử

`StorefrontIntegrationTests` kiểm tra lọc catalog (gồm danh mục con, ẩn sản phẩm nháp), chi tiết theo slug/id, tồn khả dụng, banner theo lịch, phân quyền đặt hàng, chọn kho, tạo thanh toán chờ, không xem/hủy được đơn của người khác và hủy trả hàng giữ.
