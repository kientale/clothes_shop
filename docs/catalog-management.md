# Backend quản lý sản phẩm

API phục vụ đủ 7 mục trong menu quản trị. Các endpoint dưới đây có tiền tố `/api/v1/admin`, yêu cầu JWT và role `ADMIN`; GET yêu cầu `PRODUCT_READ`, POST/PUT/DELETE yêu cầu `PRODUCT_WRITE`. Flyway V6 tự cấp hai quyền cho role ADMIN. Thay đổi quyền trong database có hiệu lực ngay ở request tiếp theo.

| Chức năng | Endpoint | Các trường chính trong POST/PUT |
| --- | --- | --- |
| Sản phẩm | `/products` | `productCode`, `name`, `brandId`, `categoryId`, `gender`, `basePrice`, `status`, `images` |
| Danh mục sản phẩm | `/categories` | `name`, `parentId`, `slug`, `description`, `status` |
| Thương hiệu | `/brands` | `name`, `slug`, `logoUrl`, `description`, `status` |
| Biến thể sản phẩm | `/product-variants` | `productId`, `colorId`, `sizeId`, `sku`, `price`, `compareAtPrice`, `status` |
| Size | `/sizes` | `name`, `code`, `sortOrder`, `status` |
| Màu sắc | `/colors` | `name`, `code`, `hexCode`, `status` |
| Bộ sưu tập | `/collections` | `name`, `slug`, `description`, `imageUrl`, `startAt`, `endAt`, `status`, `productIds` |

Mỗi tài nguyên có GET danh sách, POST tạo mới và GET/PUT/DELETE `/{id}`. POST trả 201; GET/PUT/DELETE trả 200; DELETE trả `data: null`. PUT thay thế toàn bộ dữ liệu chỉnh sửa. Riêng PUT biến thể chỉ nhận `sku`, `price`, `compareAtPrice`, `status`; sản phẩm/màu/size của biến thể không đổi.

## Danh sách và chọn dữ liệu

- `page`: từ 0, mặc định 0; `size`: 1–50, mặc định 20.
- `search`: tối đa 254 ký tự, tìm chuỗi con không phân biệt hoa/thường. `%`, `_`, `\` được coi là ký tự thường. Tìm sản phẩm theo tên/mã/slug, biến thể theo SKU hoặc tên/mã sản phẩm, danh mục/thương hiệu/bộ sưu tập theo tên/slug, màu/size theo tên/code.
- `status`: `ACTIVE` hoặc `INACTIVE`; riêng sản phẩm có thêm `DRAFT`, `ARCHIVED`.
- Sản phẩm lọc thêm `brandId`, `categoryId`, `gender` (`MEN`, `WOMEN`, `UNISEX`, `KIDS`). Danh mục lọc thêm `parentId`; biến thể lọc thêm `productId`, `colorId`, `sizeId`.
- Response danh sách có `data.content`, `page`, `size`, `totalElements`, `totalPages`. Sắp xếp mới nhất rồi UUID; size sắp xếp theo `sortOrder`, tên, UUID; biến thể theo thời gian tạo, SKU, UUID.
- `GET /catalog/options` trả toàn bộ danh mục, thương hiệu, màu, size chưa xóa, bao gồm mục `INACTIVE` để sửa dữ liệu hiện có. Response có `categories`, `brands`, `colors`, `sizes`; ô chọn có thể dùng trường `status` để hiển thị hoặc lọc.

## Sản phẩm và ảnh

Tạo thương hiệu và danh mục trước, sau đó gửi POST `/products`:

```json
{
  "productCode": "TEE-001",
  "name": "Áo thun LemonadeX",
  "slug": null,
  "description": "Áo cotton mặc hằng ngày",
  "shortDescription": "Cotton 100%",
  "brandId": "<UUID thương hiệu>",
  "categoryId": "<UUID danh mục>",
  "material": "Cotton",
  "gender": "UNISEX",
  "basePrice": 199000,
  "status": "ACTIVE",
  "images": [{"url": "https://example.com/front.png", "altText": "Mặt trước"}]
}
```

`productCode` dài tối đa 80 ký tự, chỉ chứa chữ ASCII, số, `_`, `-`; lưu chữ hoa. `name` tối đa 255 ký tự. Slug để null hoặc rỗng sẽ sinh từ tên, bỏ dấu tiếng Việt và đổi `đ` thành `d`. `description` tối đa 20000, `shortDescription` tối đa 1000, `material` tối đa 255 ký tự.

`basePrice`, `price`, `compareAtPrice` không âm, tối đa 16 chữ số phần nguyên và 2 chữ số thập phân; `compareAtPrice` có thể null và phải lớn hơn hoặc bằng `price`.

`images` bắt buộc, có thể rỗng, tối đa 10 ảnh; URL HTTP/HTTPS tối đa 2048 ký tự, alt text tối đa 255. Ảnh đầu là ảnh chính; PUT thay toàn bộ danh sách, giữ thứ tự gửi lên. Cập nhật chỉ ảnh vẫn cập nhật `updatedAt` của sản phẩm. Response có thông tin thương hiệu/danh mục, ảnh, `variantCount`, `minPrice`, `maxPrice`; thống kê tính mọi biến thể chưa xóa, giá min/max null nếu chưa có biến thể.

`POST /uploads/images` nhận multipart field `file`: JPEG, PNG, WebP hoặc GIF, tối đa 5 MB. Gán `data.url` vào ảnh sản phẩm, logo thương hiệu hoặc ảnh bộ sưu tập. File lưu trong PostgreSQL và đọc công khai qua `GET /api/v1/files/{id}`. Xem cấu hình URL và upload tại [admin-management.md](admin-management.md#ảnh-đại-diện).

## Biến thể đơn lẻ và hàng loạt

POST `/product-variants` nhận UUID sản phẩm, màu, size, giá và trạng thái. SKU null hoặc rỗng tự sinh thành `MÃSP-MÃMÀU-MÃSIZE`. Nếu vượt giới hạn 100 ký tự, SKU dùng tiền tố 67 ký tự và hậu tố 32 ký tự từ UUID của tổ hợp để giữ các tổ hợp khác nhau. SKU tự nhập chỉ chứa chữ ASCII, số, `_`, `-` và lưu chữ hoa.

POST `/product-variants/bulk` tạo các tổ hợp còn thiếu:

```json
{
  "productId": "<UUID sản phẩm>",
  "colorIds": ["<UUID màu đỏ>", "<UUID màu xanh>"],
  "sizeIds": ["<UUID size S>", "<UUID size M>"],
  "price": 199000,
  "compareAtPrice": 249000,
  "status": "ACTIVE"
}
```

Mỗi nhóm màu/size có 1–30 UUID khác nhau, tối đa 900 tổ hợp. Response là danh sách biến thể vừa tạo. Tổ hợp đang tồn tại được bỏ qua, giữ nguyên giá/SKU/trạng thái; gửi lại cùng yêu cầu sẽ trả danh sách rỗng. Tổ hợp đã xóa được khôi phục với ID cũ và dữ liệu mới. Nếu bất kỳ SKU nào trùng hoặc dữ liệu không hợp lệ, toàn bộ thao tác rollback.

## Ràng buộc và xóa mềm

- Danh mục không được đặt cha là chính nó hoặc hậu duệ (`CATEGORY_CYCLE`, 400). Không xóa khi còn con (`CATEGORY_HAS_CHILDREN`) hoặc sản phẩm (`CATEGORY_IN_USE`).
- Không xóa thương hiệu đang có sản phẩm (`BRAND_IN_USE`), màu/size đang có biến thể (`COLOR_IN_USE`, `SIZE_IN_USE`). Các lỗi đang sử dụng trả 409.
- Xóa sản phẩm xóa mềm cả biến thể, giữ ảnh và các quan hệ lịch sử. Sản phẩm đã xóa biến mất khỏi danh sách/chi tiết và khỏi nội dung bộ sưu tập.
- Bộ sưu tập có `productIds` bắt buộc, có thể rỗng, tối đa 500 UUID có thứ tự và không trùng (`DUPLICATE_PRODUCTS`, 400). Nếu có cả hai mốc thời gian, `endAt` phải sau `startAt` (`INVALID_PERIOD`, 400). Thời gian lưu/trả UTC. Danh sách chỉ trả `productCount`; GET chi tiết và POST/PUT trả thêm `products`. Chỉ đổi danh sách sản phẩm vẫn cập nhật `updatedAt`.
- Slug, mã sản phẩm, mã màu/size và SKU duy nhất kể cả bản ghi đã xóa (`SLUG_EXISTS`, `PRODUCT_CODE_EXISTS`, `CODE_EXISTS`, `SKU_EXISTS`, 409). Hai biến thể đang tồn tại không được trùng tổ hợp (`VARIANT_EXISTS`, 409).
- UUID không tồn tại hoặc đã xóa trả 404. Trường không khai báo, enum sai, giá/URL/hex không hợp lệ hoặc thiếu trường bắt buộc trả 400. Thiếu JWT trả 401; thiếu ADMIN hoặc quyền tương ứng trả 403.

Các thao tác ghi catalog dùng một PostgreSQL advisory lock trong transaction để kiểm tra và sửa dữ liệu nhất quán giữa nhiều request/server: tạo sản phẩm/biến thể không thể dùng cha vừa bị xóa, kiểm tra danh mục không tạo vòng lặp do cập nhật đồng thời, khôi phục/tạo hàng loạt không tranh chấp tổ hợp. Lock tự giải phóng khi commit/rollback; các request đọc tiếp tục chạy đồng thời. Các thao tác ghi catalog được thực hiện lần lượt.

## Kiểm thử

```powershell
.\mvnw.cmd clean verify
```

Kiểm thử sử dụng PostgreSQL riêng, chạy toàn bộ Flyway migration và Hibernate validate. `CatalogIntegrationTests` kiểm tra CRUD 7 tài nguyên, tìm kiếm/lọc, validation, quyền JWT, ảnh có thứ tự, xóa mềm, SKU dài, khôi phục, tạo hàng loạt lặp lại/rollback và cạnh tranh tạo biến thể với xóa sản phẩm. ArchUnit kiểm tra phân lớp; kiểm thử contract đối chiếu các route thực tế với [OpenAPI](openapi.json). Request chạy thử tại [api.http](api.http).
