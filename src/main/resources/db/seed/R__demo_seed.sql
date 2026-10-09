-- =====================================================================================================
-- LemonadeX: dữ liệu mẫu chi tiết (Flyway repeatable migration, chỉ chạy khi được bật)
--
-- Nội dung: thông tin cửa hàng, phương thức giao/thanh toán, thương hiệu, danh mục, màu, size,
-- 24 sản phẩm với ảnh và biến thể, 2 kho với tồn kho đầu kỳ (có sổ kho), bộ sưu tập, banner trang
-- chủ và trang giới thiệu, Lookbook, chính sách cửa hàng, mã giảm giá, chương trình khuyến mãi,
-- 4 khách hàng có tài khoản đăng nhập, 7 đơn hàng ở các trạng thái khác nhau và đánh giá đã duyệt.
--
-- Bật: thêm vào .env rồi khởi động backend
--   FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed
-- Flyway chạy file này sau các migration V1-V10 và chạy lại mỗi khi nội dung file thay đổi.
-- Có thể chạy tay: psql "$DB_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/seed/R__demo_seed.sql
--
-- An toàn khi chạy lại: mọi bản ghi được tra theo slug/mã/email trước khi tạo, nên chạy nhiều lần
-- không nhân bản dữ liệu. Danh mục, màu, size đã có (cùng slug hoặc cùng tên) được dùng lại, không
-- bị sửa. Cài đặt cửa hàng chỉ được điền khi còn ở giá trị mặc định. Đơn hàng mẫu chỉ tạo một lần.
--
-- Tài khoản khách mẫu (mật khẩu chung: Khachhang@2026):
--   minhthu.nguyen@example.com, hoangphuc.le@example.com, giahan.pham@example.com, quocbao.tran@example.com
--
-- Ảnh sản phẩm: ảnh miễn phí từ Pexels (https://www.pexels.com/license/), tải trực tiếp từ images.pexels.com.
-- Thay bằng ảnh thật của cửa hàng trong trang admin khi có.
-- =====================================================================================================

DO $seed$
DECLARE
    v_admin UUID;
    v_customer_role UUID;
    v_now TIMESTAMPTZ := now();
    v_item JSONB;
    v_sub JSONB;
    v_id UUID;
    v_product UUID;
    v_variant UUID;
    v_color UUID;
    v_size UUID;
    v_wh_hcm UUID;
    v_wh_hn UUID;
    v_new BOOLEAN;
    v_qty INT;
    v_sku TEXT;
    v_rev BIGINT;
    v_value JSONB;
    v_idx INT;
    v_order UUID;
    v_customer UUID;
    v_account UUID;
    v_placed TIMESTAMPTZ;
    v_subtotal NUMERIC(18,2);
    v_total NUMERIC(18,2);
    v_status TEXT;
    v_payment UUID;
    v_shipment UUID;
    v_stock RECORD;
    v_line RECORD;
    v_order_item UUID;
    v_code TEXT;
    -- lower(name) -> id of the color/size each seed name resolves to in this database.
    v_color_ids JSONB := '{}';
    v_size_ids JSONB := '{}';

    -- Pexels photo URL from its id.
    c_img CONSTANT TEXT := 'https://images.pexels.com/photos/%s/pexels-photo-%s.jpeg?auto=compress&cs=tinysrgb&w=1200';

    c_brands CONSTANT JSONB := '[
      {"slug": "lemonadex-basics", "name": "LemonadeX Basics", "description": "Dòng cơ bản mặc hằng ngày: cotton dày, form dễ mặc, giá dễ chịu."},
      {"slug": "saigon-atelier", "name": "Saigon Atelier", "description": "Thiết kế nữ tối giản, chất liệu tự nhiên như linen và lụa."},
      {"slug": "indigo-lab", "name": "Indigo Lab", "description": "Denim và streetwear: jean, hoodie, áo nỉ."},
      {"slug": "northline", "name": "Northline", "description": "Đồ công sở và áo khoác cho mùa thu đông."},
      {"slug": "motion-lab", "name": "Motion Lab", "description": "Đồ tập co giãn bốn chiều, thấm hút nhanh."}
    ]';

    c_categories CONSTANT JSONB := '[
      {"slug": "ao-thun", "name": "Áo thun"},
      {"slug": "ao-so-mi", "name": "Áo sơ mi"},
      {"slug": "ao-polo", "name": "Áo polo"},
      {"slug": "ao-hoodie", "name": "Áo hoodie"},
      {"slug": "ao-khoac", "name": "Áo khoác"},
      {"slug": "quan-jeans", "name": "Quần jeans"},
      {"slug": "quan-kaki", "name": "Quần kaki"},
      {"slug": "vay-dam", "name": "Váy đầm"},
      {"slug": "do-the-thao", "name": "Đồ thể thao"},
      {"slug": "phu-kien", "name": "Phụ kiện"},
      {"slug": "ao-len", "name": "Áo len"},
      {"slug": "quan-tay", "name": "Quần tây"},
      {"slug": "chan-vay", "name": "Chân váy"}
    ]';

    c_colors CONSTANT JSONB := '[
      {"name": "Đen", "code": "BLACK", "hex": "#1D1D1F"},
      {"name": "Trắng", "code": "WHITE", "hex": "#F5F5F2"},
      {"name": "Be", "code": "BEIGE", "hex": "#D9C7A7"},
      {"name": "Xám", "code": "GREY", "hex": "#8A8D91"},
      {"name": "Xanh navy", "code": "NAVY", "hex": "#1F2A44"},
      {"name": "Xanh dương", "code": "BLUE", "hex": "#2F5DA8"},
      {"name": "Xanh denim", "code": "DENIM", "hex": "#4A6A8F"},
      {"name": "Nâu", "code": "BROWN", "hex": "#7A5230"},
      {"name": "Đỏ", "code": "RED", "hex": "#B8302E"},
      {"name": "Hồng", "code": "PINK", "hex": "#E7A1B0"},
      {"name": "Xanh rêu", "code": "OLIVE", "hex": "#5B6B3A"}
    ]';

    c_sizes CONSTANT JSONB := '[
      {"name": "S", "code": "S", "sort": 1},
      {"name": "M", "code": "M", "sort": 2},
      {"name": "L", "code": "L", "sort": 3},
      {"name": "XL", "code": "XL", "sort": 4},
      {"name": "XXL", "code": "XXL", "sort": 5},
      {"name": "Free size", "code": "FREE", "sort": 9}
    ]';

    -- price: giá bán; compare: giá gốc (null nếu không giảm); images: id ảnh Pexels, ảnh đầu là ảnh chính.
    c_products CONSTANT JSONB := '[
      {"code": "LXS-SM01", "slug": "ao-so-mi-linen-co-tru", "name": "Áo sơ mi linen cổ trụ", "category": "ao-so-mi", "brand": "saigon-atelier", "gender": "WOMEN",
       "material": "55% linen, 45% cotton", "price": 459000, "compare": 529000, "images": [4036896, 4153172], "colors": ["Trắng", "Be"], "sizes": ["S", "M", "L"],
       "short": "Linen pha cotton thoáng mát, form rộng vừa, mặc đi làm hay dạo phố đều hợp.",
       "description": "Chất linen pha cotton mềm, ít nhăn hơn linen thường.\nCổ trụ, tay dài có thể xắn, hàng cúc gỗ.\nGiặt tay hoặc giặt máy chế độ nhẹ, phơi trong bóng râm."},
      {"code": "LXS-SM02", "slug": "ao-so-mi-oxford-nam", "name": "Áo sơ mi oxford nam", "category": "ao-so-mi", "brand": "lemonadex-basics", "gender": "MEN",
       "material": "100% cotton oxford", "price": 429000, "compare": null, "images": [775771], "colors": ["Trắng", "Xanh dương"], "sizes": ["M", "L", "XL"],
       "short": "Sơ mi oxford dáng regular, cổ button-down, mặc với quần jean hay quần tây đều đẹp.",
       "description": "Vải oxford dệt chéo dày dặn, bền màu.\nCổ button-down giữ form khi không đeo cà vạt.\nGiặt máy ở 30 độ C."},
      {"code": "LXS-TH01", "slug": "ao-thun-cotton-day-basic", "name": "Áo thun cotton dày basic", "category": "ao-thun", "brand": "lemonadex-basics", "gender": "UNISEX",
       "material": "100% cotton 250gsm", "price": 199000, "compare": 249000, "images": [8217536, 8217507], "colors": ["Trắng", "Đen", "Xám"], "sizes": ["S", "M", "L", "XL", "XXL"],
       "short": "Cotton 250gsm dày dặn, không lộ, cổ bo rib giữ form sau nhiều lần giặt.",
       "description": "Cotton compact 250gsm, mặt vải mịn.\nCổ rib 2cm, đường may đôi ở vai và lai.\nForm regular, nam nữ đều mặc được."},
      {"code": "LXS-TH02", "slug": "ao-thun-oversize-den", "name": "Áo thun oversize", "category": "ao-thun", "brand": "lemonadex-basics", "gender": "MEN",
       "material": "100% cotton", "price": 249000, "compare": null, "images": [17630522, 28446958], "colors": ["Đen", "Trắng"], "sizes": ["M", "L", "XL"],
       "short": "Dáng oversize vai rơi, mặc thoải mái cả ngày.",
       "description": "Vai rơi, thân rộng, dài qua hông.\nCotton mềm đã xử lý co rút.\nMặc rộng thì chọn đúng size, muốn vừa người thì giảm một size."},
      {"code": "LXS-TH03", "slug": "ao-thun-relaxed-fit", "name": "Áo thun relaxed fit", "category": "ao-thun", "brand": "lemonadex-basics", "gender": "MEN",
       "material": "95% cotton, 5% spandex", "price": 229000, "compare": null, "images": [24779116, 9775825], "colors": ["Trắng", "Be"], "sizes": ["S", "M", "L", "XL"],
       "short": "Có chút spandex nên co giãn nhẹ, ôm vừa phải.",
       "description": "Cotton pha 5% spandex, đàn hồi tốt.\nCổ tròn, tay ngắn vừa bắp tay.\nGiặt máy, không dùng thuốc tẩy."},
      {"code": "LXS-PL01", "slug": "ao-polo-pique", "name": "Áo polo piqué", "category": "ao-polo", "brand": "northline", "gender": "MEN",
       "material": "100% cotton piqué", "price": 349000, "compare": null, "images": [3779453, 9842545], "colors": ["Đỏ", "Xanh navy"], "sizes": ["M", "L", "XL"],
       "short": "Vải piqué mắt chim thoáng khí, cổ dệt không bai.",
       "description": "Cotton piqué truyền thống.\nCổ và bo tay dệt rib, nẹp 2 cúc.\nPhù hợp đi làm thứ Sáu hay chơi golf cuối tuần."},
      {"code": "LXS-PL02", "slug": "ao-polo-det-kim", "name": "Áo polo dệt kim", "category": "ao-polo", "brand": "northline", "gender": "MEN",
       "material": "70% cotton, 30% modal", "price": 399000, "compare": 459000, "images": [8422392, 10769408], "colors": ["Be", "Đen"], "sizes": ["M", "L", "XL"],
       "short": "Polo dệt kim mềm, rủ nhẹ, lên form thanh lịch.",
       "description": "Sợi cotton pha modal mát và mềm.\nCổ mở không cúc theo kiểu resort.\nGiặt tay, phơi ngang để giữ form."},
      {"code": "LXS-PL03", "slug": "ao-polo-cotton-thoang", "name": "Áo polo cotton thoáng", "category": "ao-polo", "brand": "lemonadex-basics", "gender": "MEN",
       "material": "100% cotton", "price": 299000, "compare": null, "images": [15835619, 12183279], "colors": ["Xanh navy", "Trắng"], "sizes": ["S", "M", "L"],
       "short": "Polo cơ bản giá tốt, mặc hằng ngày.",
       "description": "Cotton jersey nhẹ.\nForm regular, tay ngắn.\nGiặt máy chế độ thường."},
      {"code": "LXS-HD01", "slug": "hoodie-ni-bong-graphic", "name": "Hoodie nỉ bông graphic", "category": "ao-hoodie", "brand": "indigo-lab", "gender": "UNISEX",
       "material": "80% cotton, 20% polyester, nỉ bông", "price": 549000, "compare": null, "images": [31700390, 15988334], "colors": ["Xám", "Đen"], "sizes": ["S", "M", "L", "XL"],
       "short": "Nỉ bông mặt trong ấm, in graphic mặt trước.",
       "description": "Nỉ bông 360gsm, mặt trong lót bông.\nMũ hai lớp, túi kangaroo.\nLộn trái khi giặt để giữ hình in."},
      {"code": "LXS-HD02", "slug": "hoodie-den-basic", "name": "Hoodie đen basic", "category": "ao-hoodie", "brand": "indigo-lab", "gender": "UNISEX",
       "material": "80% cotton, 20% polyester", "price": 499000, "compare": 590000, "images": [32430590, 16637465], "colors": ["Đen"], "sizes": ["S", "M", "L", "XL"],
       "short": "Hoodie trơn màu đen, dễ phối với mọi kiểu quần.",
       "description": "Nỉ chân cua dày vừa, mặc được cả mùa mưa.\nBo tay và lai co giãn.\nGiặt máy ở 30 độ C."},
      {"code": "LXS-KH01", "slug": "ao-trench-coat-dang-dai", "name": "Áo trench coat dáng dài", "category": "ao-khoac", "brand": "saigon-atelier", "gender": "WOMEN",
       "material": "Gabardine cotton chống thấm nhẹ", "price": 1290000, "compare": null, "images": [10057713, 9968540, 9968414], "colors": ["Be", "Nâu"], "sizes": ["S", "M", "L"],
       "short": "Trench coat cổ điển hai hàng cúc, có đai thắt eo.",
       "description": "Vải gabardine cotton cản gió, chống thấm nhẹ.\nHai hàng cúc, cầu vai, đai thắt eo rời.\nGiặt khô để giữ form."},
      {"code": "LXS-KH02", "slug": "ao-khoac-da-that-dai", "name": "Áo khoác dạ thắt đai", "category": "ao-khoac", "brand": "northline", "gender": "WOMEN",
       "material": "60% len, 40% polyester", "price": 1490000, "compare": 1690000, "images": [18904211, 18904209], "colors": ["Nâu", "Đen"], "sizes": ["S", "M", "L"],
       "short": "Dạ pha len ấm, dáng dài qua gối, thắt đai mềm.",
       "description": "Dạ pha len dày, lót trơn trong.\nCổ ve lớn, túi ốp hai bên.\nGiặt khô."},
      {"code": "LXS-KH03", "slug": "ao-khoac-nau-dang-ngan", "name": "Áo khoác nâu dáng ngắn", "category": "ao-khoac", "brand": "northline", "gender": "WOMEN",
       "material": "Kaki cotton", "price": 1150000, "compare": null, "images": [10059064, 20867424], "colors": ["Nâu"], "sizes": ["S", "M"],
       "short": "Áo khoác ngắn ngang hông, hợp với quần ống rộng.",
       "description": "Kaki cotton đứng form.\nCổ bẻ, khóa kéo giấu.\nGiặt tay."},
      {"code": "LXS-JN01", "slug": "quan-jean-ong-dung", "name": "Quần jean ống đứng", "category": "quan-jeans", "brand": "indigo-lab", "gender": "MEN",
       "material": "98% cotton, 2% spandex, denim 12oz", "price": 629000, "compare": null, "images": [9775489, 9558246], "colors": ["Xanh denim"], "sizes": ["S", "M", "L", "XL"],
       "short": "Jean ống đứng cạp vừa, có chút co giãn.",
       "description": "Denim 12oz pha spandex, mặc ngồi lâu vẫn thoải mái.\nỐng đứng, cạp vừa, 5 túi.\nLộn trái khi giặt, không sấy nóng."},
      {"code": "LXS-JN02", "slug": "quan-jean-ong-rong-wash-nhat", "name": "Quần jean ống rộng wash nhạt", "category": "quan-jeans", "brand": "indigo-lab", "gender": "MEN",
       "material": "100% cotton denim", "price": 659000, "compare": null, "images": [10004175, 9558713], "colors": ["Xanh denim"], "sizes": ["M", "L", "XL"],
       "short": "Ống rộng thả, màu wash nhạt kiểu vintage.",
       "description": "Denim 100% cotton, wash đá nhạt.\nỐng rộng từ đùi xuống gấu.\nGiặt riêng vài lần đầu."},
      {"code": "LXS-KK01", "slug": "quan-kaki-chino", "name": "Quần kaki chino", "category": "quan-kaki", "brand": "northline", "gender": "MEN",
       "material": "97% cotton, 3% spandex", "price": 489000, "compare": null, "images": [11990104, 27385944], "colors": ["Be", "Xám"], "sizes": ["M", "L", "XL"],
       "short": "Chino ống côn nhẹ, mặc đi làm hay đi chơi.",
       "description": "Kaki cotton co giãn nhẹ.\nỐng côn, cạp có đỉa đeo thắt lưng.\nGiặt máy, ủi ở nhiệt độ trung bình."},
      {"code": "LXS-VD01", "slug": "dam-suong-trang-toi-gian", "name": "Đầm suông trắng tối giản", "category": "vay-dam", "brand": "saigon-atelier", "gender": "WOMEN",
       "material": "Lụa satin", "price": 789000, "compare": null, "images": [30736117, 30736113], "colors": ["Trắng"], "sizes": ["S", "M", "L"],
       "short": "Đầm suông dài, đường cắt tối giản, mặc dự tiệc hay chụp ảnh.",
       "description": "Lụa satin rủ mềm, có lớp lót.\nCổ vuông, dây vai điều chỉnh được.\nGiặt tay nước lạnh."},
      {"code": "LXS-VD02", "slug": "dam-den-dang-dai", "name": "Đầm đen dáng dài", "category": "vay-dam", "brand": "saigon-atelier", "gender": "WOMEN",
       "material": "Crepe co giãn", "price": 899000, "compare": 990000, "images": [30706568, 17570989], "colors": ["Đen"], "sizes": ["S", "M", "L"],
       "short": "Đầm đen dài thanh lịch, dáng ôm nhẹ.",
       "description": "Vải crepe co giãn, ít nhăn.\nKhóa kéo giấu sau lưng.\nGiặt tay hoặc giặt khô."},
      {"code": "LXS-VD03", "slug": "dam-no-thiet-ke", "name": "Đầm nơ thiết kế", "category": "vay-dam", "brand": "saigon-atelier", "gender": "WOMEN",
       "material": "Tafta", "price": 1190000, "compare": null, "images": [13364870, 13381636, 30736118], "colors": ["Trắng"], "sizes": ["S", "M"],
       "short": "Đầm tafta đứng form với nơ lớn, dành cho dịp đặc biệt.",
       "description": "Tafta đứng form, lót lụa bên trong.\nNơ lớn có thể tháo rời.\nGiặt khô."},
      {"code": "LXS-TT01", "slug": "bo-do-tap-the-thao-nu", "name": "Bộ đồ tập thể thao nữ", "category": "do-the-thao", "brand": "motion-lab", "gender": "WOMEN",
       "material": "78% nylon, 22% spandex", "price": 459000, "compare": null, "images": [3931364, 11117152], "colors": ["Đen", "Xanh dương"], "sizes": ["S", "M", "L"],
       "short": "Áo bra và legging co giãn bốn chiều, khô nhanh.",
       "description": "Nylon pha spandex, co giãn bốn chiều.\nCạp cao ôm bụng, không lộ khi squat.\nGiặt nước lạnh, không sấy."},
      {"code": "LXS-TT02", "slug": "quan-legging-tap-gym", "name": "Quần legging tập gym", "category": "do-the-thao", "brand": "motion-lab", "gender": "WOMEN",
       "material": "75% polyester, 25% spandex", "price": 329000, "compare": null, "images": [3621168, 206341], "colors": ["Xám", "Đen"], "sizes": ["S", "M", "L"],
       "short": "Legging cạp cao, có túi nhỏ ở cạp.",
       "description": "Vải dày, không xuyên thấu.\nTúi nhỏ ở cạp sau đựng chìa khóa.\nGiặt máy chế độ nhẹ."},
      {"code": "LXS-PK01", "slug": "tui-tote-vai-canvas", "name": "Túi tote vải canvas", "category": "phu-kien", "brand": "lemonadex-basics", "gender": "UNISEX",
       "material": "Canvas cotton 12oz", "price": 259000, "compare": null, "images": [4004226, 3808249], "colors": ["Be"], "sizes": ["Free size"],
       "short": "Túi tote canvas dày, đựng vừa laptop 14 inch.",
       "description": "Canvas cotton 12oz, quai đeo vai.\nNgăn trong có khóa kéo.\nGiặt tay."},
      {"code": "LXS-PK02", "slug": "bo-phu-kien-da-nam", "name": "Bộ phụ kiện da nam", "category": "phu-kien", "brand": "northline", "gender": "MEN",
       "material": "Da bò thật", "price": 690000, "compare": null, "images": [28719728, 5405644], "colors": ["Nâu"], "sizes": ["Free size"],
       "short": "Thắt lưng và ví da bò, đóng hộp quà.",
       "description": "Da bò thật, khóa hợp kim không gỉ.\nĐóng hộp, kèm thiệp.\nLau bằng khăn khô."},
      {"code": "LXS-KD01", "slug": "set-do-tre-em-nang-dong", "name": "Set đồ trẻ em năng động", "category": "ao-thun", "brand": "lemonadex-basics", "gender": "KIDS",
       "material": "100% cotton", "price": 279000, "compare": null, "images": [6261877], "colors": ["Be"], "sizes": ["S", "M"],
       "short": "Set áo thun và quần cotton mềm cho bé 4-8 tuổi.",
       "description": "Cotton mềm, không gây kích ứng.\nSize S cho bé 4-5 tuổi, M cho bé 6-8 tuổi.\nGiặt máy chế độ nhẹ."},
      {"code": "LXS-TH04", "slug": "ao-thun-ke-soc-breton", "name": "Áo thun kẻ sọc Breton", "category": "ao-thun", "brand": "lemonadex-basics", "gender": "WOMEN",
       "material": "100% cotton jersey", "price": 219000, "compare": 259000, "images": [3050005, 1171601], "colors": ["Xanh navy", "Đen"], "sizes": ["S", "M", "L"],
       "short": "Sọc ngang kiểu thủy thủ, cổ thuyền, mặc với quần jean hay chân váy đều hợp.",
       "description": "Cotton jersey dày vừa, sọc dệt nên không bong tróc.\nCổ thuyền rộng, tay dài có thể xắn.\nGiặt máy ở 30 độ C."},
      {"code": "LXS-TH05", "slug": "ao-thun-in-graphic-oversize", "name": "Áo thun in graphic oversize", "category": "ao-thun", "brand": "indigo-lab", "gender": "MEN",
       "material": "100% cotton 230gsm", "price": 289000, "compare": null, "images": [12922525], "colors": ["Đen"], "sizes": ["M", "L", "XL"],
       "short": "Hình in mặt trước khổ lớn, dáng oversize vai rơi.",
       "description": "In lụa mực gốc nước, bền màu sau nhiều lần giặt.\nVai rơi, thân rộng.\nLộn trái khi giặt, không ủi lên hình in."},
      {"code": "LXS-TH06", "slug": "ao-croptop-dai-tay", "name": "Áo croptop dài tay", "category": "ao-thun", "brand": "saigon-atelier", "gender": "WOMEN",
       "material": "95% cotton, 5% spandex", "price": 239000, "compare": null, "images": [20821027], "colors": ["Trắng", "Đen"], "sizes": ["S", "M"],
       "short": "Croptop dài tay ôm nhẹ, hợp với quần cạp cao.",
       "description": "Cotton pha spandex co giãn.\nDài ngang eo, cổ tròn.\nGiặt tay hoặc giặt máy chế độ nhẹ."},
      {"code": "LXS-TH07", "slug": "ao-thun-boxy-co-tron", "name": "Áo thun boxy cổ tròn", "category": "ao-thun", "brand": "lemonadex-basics", "gender": "WOMEN",
       "material": "100% cotton", "price": 199000, "compare": null, "images": [9558777], "colors": ["Trắng", "Be"], "sizes": ["S", "M", "L"],
       "short": "Dáng boxy ngắn vừa, sơ vin hay thả ngoài đều gọn.",
       "description": "Cotton 220gsm mềm, không xù.\nTay lửng qua vai, thân suông.\nGiặt máy chế độ thường."},
      {"code": "LXS-SM03", "slug": "ao-so-mi-flannel-caro", "name": "Áo sơ mi flannel caro", "category": "ao-so-mi", "brand": "indigo-lab", "gender": "MEN",
       "material": "100% cotton flannel", "price": 389000, "compare": 449000, "images": [11943572, 15835605], "colors": ["Đỏ", "Nâu"], "sizes": ["M", "L", "XL"],
       "short": "Flannel caro dày, mặc như áo khoác nhẹ khi trời se lạnh.",
       "description": "Cotton flannel chải lông mặt trong, ấm và mềm.\nHai túi ngực có nắp.\nGiặt máy ở 30 độ C, không sấy nóng."},
      {"code": "LXS-SM04", "slug": "ao-so-mi-cong-so-nu", "name": "Áo sơ mi công sở nữ", "category": "ao-so-mi", "brand": "saigon-atelier", "gender": "WOMEN",
       "material": "70% cotton, 30% polyester", "price": 399000, "compare": null, "images": [8837763], "colors": ["Trắng", "Xanh dương"], "sizes": ["S", "M", "L"],
       "short": "Sơ mi cổ đức dáng suông, ít nhăn, mặc đi làm cả tuần.",
       "description": "Vải pha polyester ít nhăn, ủi nhanh.\nCổ đức, tay dài có măng sét.\nGiặt máy, ủi ở nhiệt độ trung bình."},
      {"code": "LXS-SM05", "slug": "ao-kieu-tre-vai", "name": "Áo kiểu trễ vai", "category": "ao-so-mi", "brand": "saigon-atelier", "gender": "WOMEN",
       "material": "Cotton pha modal", "price": 329000, "compare": null, "images": [19185919], "colors": ["Trắng"], "sizes": ["S", "M", "L"],
       "short": "Áo trễ vai tay dài, ôm vừa, đi chơi hay hẹn hò đều hợp.",
       "description": "Cotton pha modal mềm và mát.\nCổ trễ vai có chun giữ form.\nGiặt tay nước lạnh."},
      {"code": "LXS-SM06", "slug": "ao-so-mi-linen-nam", "name": "Áo sơ mi linen nam", "category": "ao-so-mi", "brand": "lemonadex-basics", "gender": "MEN",
       "material": "100% linen", "price": 449000, "compare": null, "images": [18016780, 16235607], "colors": ["Trắng", "Be"], "sizes": ["M", "L", "XL"],
       "short": "Linen thoáng mát cho mùa hè, mặc đi biển hay dạo phố.",
       "description": "Linen nguyên chất, càng giặt càng mềm.\nCổ bẻ, tay dài có thể xắn.\nGiặt tay, phơi trong bóng râm."},
      {"code": "LXS-SM07", "slug": "ao-so-mi-hoa-tiet-di-bien", "name": "Áo sơ mi họa tiết đi biển", "category": "ao-so-mi", "brand": "lemonadex-basics", "gender": "MEN",
       "material": "100% rayon", "price": 359000, "compare": 399000, "images": [34068161], "colors": ["Xanh navy"], "sizes": ["M", "L", "XL"],
       "short": "Sơ mi cổ mở in họa tiết, vải rayon rủ và mát.",
       "description": "Rayon nhẹ, khô nhanh.\nCổ mở kiểu resort, tay ngắn.\nGiặt tay, không vắt mạnh."},
      {"code": "LXS-AL01", "slug": "ao-len-co-tron-nam", "name": "Áo len cổ tròn nam", "category": "ao-len", "brand": "northline", "gender": "MEN",
       "material": "50% len merino, 50% acrylic", "price": 559000, "compare": null, "images": [13917253, 6643012, 2698935], "colors": ["Trắng", "Nâu", "Xanh rêu"], "sizes": ["M", "L", "XL"],
       "short": "Len merino pha mềm, không ngứa, mặc thẳng lên da được.",
       "description": "Len merino pha acrylic, giữ ấm mà vẫn thoáng.\nCổ, tay và lai bo rib.\nGiặt tay nước lạnh, phơi ngang."},
      {"code": "LXS-AL02", "slug": "ao-len-ke-soc", "name": "Áo len kẻ sọc", "category": "ao-len", "brand": "northline", "gender": "UNISEX",
       "material": "100% acrylic", "price": 499000, "compare": null, "images": [45982], "colors": ["Xanh navy"], "sizes": ["S", "M", "L", "XL"],
       "short": "Áo len dệt sọc ngang dày dặn, phối với sơ mi bên trong.",
       "description": "Sợi acrylic dệt kim to, ấm.\nForm rộng vừa, nam nữ mặc được.\nGiặt tay, không treo khi phơi."},
      {"code": "LXS-AL03", "slug": "ao-len-co-lo-nu", "name": "Áo len cổ lọ nữ", "category": "ao-len", "brand": "saigon-atelier", "gender": "WOMEN",
       "material": "70% len, 30% nylon", "price": 529000, "compare": 599000, "images": [5491145, 9894727, 7274470], "colors": ["Be", "Trắng", "Đỏ"], "sizes": ["S", "M", "L"],
       "short": "Cổ lọ ôm cổ giữ ấm, dệt gân mềm, mặc trong áo khoác.",
       "description": "Len pha nylon bền, ít xù.\nCổ lọ gập đôi, dáng ôm nhẹ.\nGiặt tay nước lạnh, phơi ngang."},
      {"code": "LXS-AL04", "slug": "ao-cardigan-len-ke", "name": "Áo cardigan len kẻ", "category": "ao-len", "brand": "saigon-atelier", "gender": "WOMEN",
       "material": "60% cotton, 40% acrylic", "price": 489000, "compare": null, "images": [15052341], "colors": ["Xanh rêu"], "sizes": ["S", "M", "L"],
       "short": "Cardigan dệt kẻ, cài cúc, khoác ngoài văn phòng máy lạnh.",
       "description": "Sợi cotton pha acrylic mềm.\nHàng cúc trước, hai túi ốp.\nGiặt tay, phơi ngang."},
      {"code": "LXS-KH04", "slug": "ao-khoac-jean", "name": "Áo khoác jean", "category": "ao-khoac", "brand": "indigo-lab", "gender": "MEN",
       "material": "100% cotton denim 13oz", "price": 689000, "compare": 790000, "images": [2344601, 6968739], "colors": ["Xanh denim"], "sizes": ["M", "L", "XL"],
       "short": "Jacket jean cổ điển, càng mặc càng lên màu đẹp.",
       "description": "Denim 13oz dày, wash nhẹ.\nHai túi ngực nắp cài, cúc đồng.\nLộn trái khi giặt, giặt riêng vài lần đầu."},
      {"code": "LXS-KH05", "slug": "ao-khoac-varsity", "name": "Áo khoác varsity", "category": "ao-khoac", "brand": "indigo-lab", "gender": "UNISEX",
       "material": "Thân nỉ dạ, tay giả da", "price": 790000, "compare": null, "images": [14474177], "colors": ["Đỏ"], "sizes": ["M", "L", "XL"],
       "short": "Bomber kiểu varsity thêu chữ, phong cách đường phố.",
       "description": "Thân nỉ dạ ấm, tay giả da.\nCổ, tay và lai bo dệt sọc.\nGiặt khô hoặc giặt tay nhẹ."},
      {"code": "LXS-KH06", "slug": "ao-blazer-nu-dang-suong", "name": "Áo blazer nữ dáng suông", "category": "ao-khoac", "brand": "northline", "gender": "WOMEN",
       "material": "Polyester pha viscose", "price": 990000, "compare": null, "images": [15345385, 7202773, 7959652], "colors": ["Đỏ", "Trắng"], "sizes": ["S", "M", "L"],
       "short": "Blazer một cúc dáng suông, mặc đi làm hay phối với quần jean.",
       "description": "Vải pha viscose đứng form, có lót.\nVai độn mỏng, một cúc.\nGiặt khô."},
      {"code": "LXS-KH07", "slug": "ao-phao-long-vu", "name": "Áo phao lông vũ", "category": "ao-khoac", "brand": "northline", "gender": "UNISEX",
       "material": "Vỏ nylon chống nước, ruột 80% lông vũ", "price": 1390000, "compare": 1590000, "images": [7026775, 14469824, 11274805], "colors": ["Xám", "Đen"], "sizes": ["S", "M", "L", "XL"],
       "short": "Phao lông vũ nhẹ mà ấm, mang đi du lịch xứ lạnh.",
       "description": "Ruột lông vũ 80/20, vỏ nylon cản gió.\nMũ liền, khóa kéo hai chiều.\nGiặt máy chế độ nhẹ, sấy nhiệt thấp với bóng giặt."},
      {"code": "LXS-KH08", "slug": "ao-khoac-da-biker-nu", "name": "Áo khoác da biker nữ", "category": "ao-khoac", "brand": "indigo-lab", "gender": "WOMEN",
       "material": "Da PU cao cấp", "price": 1590000, "compare": null, "images": [15161529, 4355355, 11555859], "colors": ["Đen"], "sizes": ["S", "M", "L"],
       "short": "Biker jacket khóa lệch, dáng ngắn ôm eo.",
       "description": "Da PU mềm, không bong tróc.\nKhóa kéo lệch, ve áo bấm cúc.\nLau bằng khăn ẩm, không giặt máy."},
      {"code": "LXS-KH09", "slug": "ao-mang-to-da-nu", "name": "Áo măng tô dạ nữ", "category": "ao-khoac", "brand": "saigon-atelier", "gender": "WOMEN",
       "material": "70% len, 30% polyester", "price": 1690000, "compare": null, "images": [15957884], "colors": ["Be"], "sizes": ["S", "M", "L"],
       "short": "Măng tô dạ dáng dài, cổ ve, khoác ngoài váy hay quần đều đẹp.",
       "description": "Dạ pha len dày, lót satin.\nMột hàng cúc, túi chéo hai bên.\nGiặt khô."},
      {"code": "LXS-JN03", "slug": "quan-jean-nu-mom-fit", "name": "Quần jean nữ mom fit", "category": "quan-jeans", "brand": "indigo-lab", "gender": "WOMEN",
       "material": "100% cotton denim", "price": 559000, "compare": null, "images": [6769359, 17745134], "colors": ["Xanh denim"], "sizes": ["S", "M", "L"],
       "short": "Cạp cao, ống suông côn nhẹ, hack dáng chân dài.",
       "description": "Denim cotton không co giãn, đứng form.\nCạp cao qua rốn, 5 túi.\nLộn trái khi giặt."},
      {"code": "LXS-JN04", "slug": "quan-jean-rach-goi-nu", "name": "Quần jean rách gối nữ", "category": "quan-jeans", "brand": "indigo-lab", "gender": "WOMEN",
       "material": "98% cotton, 2% spandex", "price": 589000, "compare": 659000, "images": [2319130], "colors": ["Xanh denim"], "sizes": ["S", "M", "L"],
       "short": "Jean skinny rách gối, co giãn ôm chân.",
       "description": "Denim pha spandex co giãn.\nRách gối xử lý chống tưa sợi.\nGiặt tay, không vắt mạnh."},
      {"code": "LXS-KK02", "slug": "quan-kaki-ong-suong-nam", "name": "Quần kaki ống suông nam", "category": "quan-kaki", "brand": "lemonadex-basics", "gender": "MEN",
       "material": "100% cotton twill", "price": 449000, "compare": null, "images": [11734417, 12154890], "colors": ["Be"], "sizes": ["M", "L", "XL"],
       "short": "Kaki ống suông thoải mái, mặc cả ngày không bí.",
       "description": "Cotton twill dày vừa.\nỐng suông, cạp có đỉa.\nGiặt máy, ủi nhiệt độ trung bình."},
      {"code": "LXS-KK03", "slug": "quan-cargo-tui-hop", "name": "Quần cargo túi hộp", "category": "quan-kaki", "brand": "indigo-lab", "gender": "MEN",
       "material": "Kaki cotton ripstop", "price": 529000, "compare": null, "images": [18036895, 19392459], "colors": ["Xanh rêu", "Đen"], "sizes": ["M", "L", "XL"],
       "short": "Cargo túi hộp hai bên, gấu rút dây điều chỉnh.",
       "description": "Vải ripstop chống rách.\nSáu túi, gấu có dây rút.\nGiặt máy chế độ thường."},
      {"code": "LXS-QT01", "slug": "quan-ong-rong-nu", "name": "Quần ống rộng nữ", "category": "quan-tay", "brand": "saigon-atelier", "gender": "WOMEN",
       "material": "Polyester pha rayon", "price": 459000, "compare": null, "images": [11545747], "colors": ["Đen"], "sizes": ["S", "M", "L"],
       "short": "Quần tây ống rộng cạp cao, rủ đẹp khi bước đi.",
       "description": "Vải pha rayon rủ, ít nhăn.\nCạp cao có ly trước.\nGiặt máy chế độ nhẹ."},
      {"code": "LXS-QT02", "slug": "bo-do-linen-ong-rong", "name": "Bộ đồ linen ống rộng", "category": "quan-tay", "brand": "saigon-atelier", "gender": "WOMEN",
       "material": "55% linen, 45% viscose", "price": 890000, "compare": 990000, "images": [27098937, 11511219], "colors": ["Be"], "sizes": ["S", "M", "L"],
       "short": "Set áo và quần ống rộng linen, mặc đi làm hay du lịch.",
       "description": "Linen pha viscose mềm, ít nhăn.\nÁo cổ tròn tay lửng, quần cạp chun sau.\nGiặt tay, phơi trong bóng râm."},
      {"code": "LXS-VD04", "slug": "dam-hoa-dang-midi", "name": "Đầm hoa dáng midi", "category": "vay-dam", "brand": "saigon-atelier", "gender": "WOMEN",
       "material": "Voan hoa có lót", "price": 659000, "compare": 759000, "images": [2328154, 26835798, 2813692], "colors": ["Xanh navy"], "sizes": ["S", "M", "L"],
       "short": "Đầm hoa dài qua gối, tay phồng nhẹ, đi chơi cuối tuần.",
       "description": "Voan in hoa, lót cotton bên trong.\nThắt eo, chân váy xòe nhẹ.\nGiặt tay nước lạnh."},
      {"code": "LXS-CV01", "slug": "chan-vay-xep-ly", "name": "Chân váy xếp ly", "category": "chan-vay", "brand": "saigon-atelier", "gender": "WOMEN",
       "material": "Polyester giữ ly", "price": 359000, "compare": null, "images": [9178855], "colors": ["Nâu"], "sizes": ["S", "M"],
       "short": "Chân váy ngắn xếp ly, ly giữ nếp sau khi giặt.",
       "description": "Polyester ép ly bền nếp.\nCạp chun sau, khóa kéo bên hông.\nGiặt tay, treo phơi để giữ ly."},
      {"code": "LXS-CV02", "slug": "chan-vay-jean-chu-a", "name": "Chân váy jean chữ A", "category": "chan-vay", "brand": "indigo-lab", "gender": "WOMEN",
       "material": "100% cotton denim", "price": 389000, "compare": null, "images": [8775020, 19038021], "colors": ["Xanh denim"], "sizes": ["S", "M", "L"],
       "short": "Chân váy jean chữ A ngắn, cạp cao, dễ phối áo thun.",
       "description": "Denim cotton đứng form.\nDáng chữ A, 4 túi.\nLộn trái khi giặt."},
      {"code": "LXS-CV03", "slug": "chan-vay-ke-caro", "name": "Chân váy kẻ caro", "category": "chan-vay", "brand": "northline", "gender": "WOMEN",
       "material": "Dạ tweed mỏng", "price": 429000, "compare": null, "images": [1007019], "colors": ["Xám"], "sizes": ["S", "M", "L"],
       "short": "Chân váy caro dài qua gối, xẻ trước, mặc công sở.",
       "description": "Dạ tweed mỏng, có lót.\nCạp cao, xẻ trước, cúc trang trí.\nGiặt khô hoặc giặt tay nhẹ."},
      {"code": "LXS-TT03", "slug": "ao-tank-top-tap-gym", "name": "Áo tank top tập gym", "category": "do-the-thao", "brand": "motion-lab", "gender": "MEN",
       "material": "90% polyester, 10% spandex", "price": 189000, "compare": null, "images": [5327532, 7673598], "colors": ["Trắng", "Xám"], "sizes": ["M", "L", "XL"],
       "short": "Tank top khoét nách sâu, thoáng khí khi tập nặng.",
       "description": "Vải mè thấm hút, khô nhanh.\nKhoét nách sâu, vạt sau dài hơn.\nGiặt máy, không dùng nước xả."},
      {"code": "LXS-TT04", "slug": "quan-short-chay-bo", "name": "Quần short chạy bộ", "category": "do-the-thao", "brand": "motion-lab", "gender": "MEN",
       "material": "100% polyester", "price": 229000, "compare": null, "images": [4719931, 936094], "colors": ["Đen"], "sizes": ["M", "L", "XL"],
       "short": "Short 5 inch có lót trong, túi khóa kéo đựng chìa khóa.",
       "description": "Polyester nhẹ, khô nhanh.\nLót lưới bên trong, xẻ hai bên gấu.\nGiặt máy chế độ nhẹ."},
      {"code": "LXS-TT05", "slug": "ao-khoac-gio-chong-nuoc", "name": "Áo khoác gió chống nước", "category": "do-the-thao", "brand": "motion-lab", "gender": "UNISEX",
       "material": "Nylon phủ chống nước", "price": 590000, "compare": null, "images": [8497715, 17167935, 3214752], "colors": ["Xám", "Đen", "Trắng"], "sizes": ["M", "L", "XL"],
       "short": "Khoác gió mỏng nhẹ, cản mưa phùn, gấp gọn bỏ túi.",
       "description": "Nylon phủ lớp chống nước nhẹ.\nMũ có dây rút, khóa kéo chống nước.\nGiặt tay, không ủi."},
      {"code": "LXS-PK03", "slug": "mu-luoi-trai-theu", "name": "Mũ lưỡi trai thêu", "category": "phu-kien", "brand": "indigo-lab", "gender": "UNISEX",
       "material": "100% cotton kaki", "price": 189000, "compare": null, "images": [18509591, 13265200, 844867], "colors": ["Đen", "Xanh navy"], "sizes": ["Free size"],
       "short": "Mũ lưỡi trai thêu chữ, khóa sau điều chỉnh vòng đầu.",
       "description": "Kaki cotton, lưỡi trai cong.\nKhóa kim loại phía sau.\nGiặt tay, không giặt máy."},
      {"code": "LXS-PK04", "slug": "khan-len-quang-co", "name": "Khăn len quàng cổ", "category": "phu-kien", "brand": "northline", "gender": "UNISEX",
       "material": "100% acrylic", "price": 199000, "compare": null, "images": [3566870, 7820684], "colors": ["Hồng", "Đỏ"], "sizes": ["Free size"],
       "short": "Khăn len dài dệt dày, quàng nhiều vòng vẫn mềm.",
       "description": "Sợi acrylic mềm, không ngứa.\nDài 180cm, rộng 30cm.\nGiặt tay nước lạnh."},
      {"code": "LXS-PK05", "slug": "mu-bucket-kaki", "name": "Mũ bucket kaki", "category": "phu-kien", "brand": "lemonadex-basics", "gender": "UNISEX",
       "material": "100% cotton kaki", "price": 159000, "compare": null, "images": [15035198, 5429325], "colors": ["Be", "Xám"], "sizes": ["Free size"],
       "short": "Mũ bucket vành rộng che nắng, gấp gọn được.",
       "description": "Kaki cotton, vành rộng 6cm.\nLỗ thoáng khí hai bên.\nGiặt tay."},
      {"code": "LXS-PK06", "slug": "mu-da-nu", "name": "Mũ dạ nữ", "category": "phu-kien", "brand": "saigon-atelier", "gender": "WOMEN",
       "material": "Dạ len", "price": 259000, "compare": null, "images": [11964380], "colors": ["Nâu"], "sizes": ["Free size"],
       "short": "Mũ dạ vành cụp, phụ kiện cho mùa thu đông.",
       "description": "Dạ len giữ form.\nVành cụp mềm, quai trong chống trượt.\nLau bằng bàn chải mềm."},
      {"code": "LXS-PK07", "slug": "kinh-mat-gong-tron", "name": "Kính mát gọng tròn", "category": "phu-kien", "brand": "northline", "gender": "UNISEX",
       "material": "Gọng kim loại, tròng polycarbonate", "price": 349000, "compare": 399000, "images": [1926768, 3008144, 26892329], "colors": ["Đen", "Nâu"], "sizes": ["Free size"],
       "short": "Kính mát gọng mảnh, tròng chống tia UV400.",
       "description": "Tròng polycarbonate UV400.\nGọng kim loại nhẹ, đệm mũi silicon.\nKèm hộp và khăn lau."},
      {"code": "LXS-KD02", "slug": "ao-khoac-tre-em", "name": "Áo khoác trẻ em", "category": "ao-khoac", "brand": "lemonadex-basics", "gender": "KIDS",
       "material": "Vỏ polyester, lót nỉ", "price": 359000, "compare": null, "images": [14316314, 3147216, 5792943], "colors": ["Xanh dương", "Đỏ"], "sizes": ["S", "M"],
       "short": "Áo khoác có mũ, lót nỉ ấm cho bé đi học.",
       "description": "Vỏ polyester cản gió, lót nỉ mềm.\nSize S cho bé 4-5 tuổi, M cho bé 6-8 tuổi.\nGiặt máy chế độ nhẹ."},
      {"code": "LXS-KD03", "slug": "dam-be-gai", "name": "Đầm bé gái", "category": "vay-dam", "brand": "saigon-atelier", "gender": "KIDS",
       "material": "100% cotton", "price": 299000, "compare": null, "images": [12994603, 17839400], "colors": ["Trắng", "Xanh denim"], "sizes": ["S", "M"],
       "short": "Đầm cotton mềm cho bé gái 2-6 tuổi, mặc đi chơi.",
       "description": "Cotton mềm, không gây kích ứng.\nSize S cho bé 2-3 tuổi, M cho bé 4-6 tuổi.\nGiặt máy chế độ nhẹ."}
    ]';

    -- Variants out of stock everywhere, so the shop shows sold-out sizes.
    c_sold_out CONSTANT TEXT[] := ARRAY['LXS-SM01-BEIGE-L', 'LXS-TH01-GREY-XXL', 'LXS-HD02-BLACK-S', 'LXS-KH08-BLACK-S', 'LXS-AL03-RED-L', 'LXS-PK06-BROWN-FREE'];

    c_customers CONSTANT JSONB := '[
      {"email": "minhthu.nguyen@example.com", "name": "Nguyễn Minh Thư", "phone": "0903418772", "gender": "FEMALE", "dob": "1996-04-12",
       "address": {"line": "214 Nguyễn Trãi", "ward": "Phường 7", "district": "Quận 5", "province": "TP. Hồ Chí Minh"}},
      {"email": "hoangphuc.le@example.com", "name": "Lê Hoàng Phúc", "phone": "0938112905", "gender": "MALE", "dob": "1992-11-03",
       "address": {"line": "58 Nguyễn Thị Thập", "ward": "Phường Tân Phú", "district": "Quận 7", "province": "TP. Hồ Chí Minh"}},
      {"email": "giahan.pham@example.com", "name": "Phạm Gia Hân", "phone": "0977640213", "gender": "FEMALE", "dob": "1999-08-21",
       "address": {"line": "12 Lê Lợi", "ward": "Phường Bến Nghé", "district": "Quận 1", "province": "TP. Hồ Chí Minh"}},
      {"email": "quocbao.tran@example.com", "name": "Trần Quốc Bảo", "phone": "0912557031", "gender": "MALE", "dob": "1990-02-17",
       "address": {"line": "112 Trần Thái Tông", "ward": "Phường Dịch Vọng Hậu", "district": "Quận Cầu Giấy", "province": "Hà Nội"}}
    ]';

    -- status: trạng thái cuối của đơn; days: số ngày trước khi đặt; review: số sao cho từng dòng (null = không đánh giá).
    c_orders CONSTANT JSONB := '[
      {"code": "LX-SEED0001", "customer": "minhthu.nguyen@example.com", "status": "COMPLETED", "days": 40, "items": [
        {"sku": "LXS-SM01-WHITE-M", "qty": 1, "rating": 5, "comment": "Vải mát, mặc cả ngày không bí. Size M vừa với mình cao 1m60, nặng 50kg."},
        {"sku": "LXS-VD01-WHITE-M", "qty": 1, "rating": 5, "comment": "Đầm đẹp hơn hình, lụa mềm và có lót nên không lo xuyên thấu."}]},
      {"code": "LX-SEED0002", "customer": "hoangphuc.le@example.com", "status": "COMPLETED", "days": 33, "items": [
        {"sku": "LXS-TH01-BLACK-L", "qty": 2, "rating": 4, "comment": "Áo dày dặn, giặt 5 lần chưa thấy giãn cổ. Màu đen hơi phai nhẹ sau lần giặt đầu."},
        {"sku": "LXS-JN01-DENIM-L", "qty": 1, "rating": 5, "comment": "Form ống đứng chuẩn, có co giãn nên ngồi làm việc cả ngày vẫn thoải mái."}]},
      {"code": "LX-SEED0003", "customer": "giahan.pham@example.com", "status": "COMPLETED", "days": 28, "items": [
        {"sku": "LXS-VD02-BLACK-S", "qty": 1, "rating": 5, "comment": "Mặc đi tiệc công ty được khen nhiều. Vải ít nhăn, mang đi xa tiện."},
        {"sku": "LXS-KH01-BEIGE-S", "qty": 1, "rating": 4, "comment": "Áo đứng form, màu be dễ phối. Tay hơi dài một chút so với mình."}]},
      {"code": "LX-SEED0004", "customer": "quocbao.tran@example.com", "status": "COMPLETED", "days": 21, "items": [
        {"sku": "LXS-HD01-GREY-L", "qty": 1, "rating": 5, "comment": "Nỉ dày và ấm, Hà Nội trời lạnh mặc một lớp là đủ."},
        {"sku": "LXS-PL01-NAVY-L", "qty": 1, "rating": 4, "comment": "Polo đẹp, cổ không bị quăn. Giao hàng ra Hà Nội mất 3 ngày."}]},
      {"code": "LX-SEED0005", "customer": "minhthu.nguyen@example.com", "status": "DELIVERED", "days": 12, "items": [
        {"sku": "LXS-TT01-BLACK-S", "qty": 1, "rating": 5, "comment": "Co giãn tốt, tập yoga và chạy bộ đều ổn, không bị tuột cạp."}]},
      {"code": "LX-SEED0006", "customer": "hoangphuc.le@example.com", "status": "SHIPPED", "days": 3, "items": [
        {"sku": "LXS-KK01-BEIGE-L", "qty": 1}]},
      {"code": "LX-SEED0007", "customer": "giahan.pham@example.com", "status": "PLACED", "days": 1, "items": [
        {"sku": "LXS-VD03-WHITE-S", "qty": 1},
        {"sku": "LXS-PK01-BEIGE-FREE", "qty": 1}]}
    ]';
BEGIN
    SELECT id INTO v_admin FROM accounts WHERE lower(email) = 'admin@example.com' AND NOT deleted;
    IF v_admin IS NULL THEN
        RAISE EXCEPTION 'Chưa có tài khoản admin@example.com (migration V3). Hãy chạy các migration trong db/migration trước khi seed.';
    END IF;
    SELECT id INTO v_customer_role FROM roles WHERE code = 'CUSTOMER';

    ---------------------------------------------------------------------------------------------
    -- Cửa hàng, cài đặt, phương thức giao hàng và thanh toán
    ---------------------------------------------------------------------------------------------
    -- Store details only while they are still the migration defaults.
    SELECT setting_value::jsonb, revision INTO v_value, v_rev FROM system_settings WHERE setting_group = 'STORE' AND setting_key = 'CONFIG';
    IF v_value IS NOT NULL AND v_value->>'supportEmail' IS NULL AND v_value->>'address' IS NULL THEN
        UPDATE system_settings SET setting_value = jsonb_build_object(
                'storeName', 'LemonadeX', 'legalName', 'Công ty TNHH Thời trang LemonadeX', 'taxCode', '0318294417',
                'supportEmail', 'hotro@lemonadex.vn', 'supportPhone', '028 3920 4417',
                'address', '214 Nguyễn Trãi, Phường 7, Quận 5, TP. Hồ Chí Minh', 'logoUrl', NULL,
                'websiteUrl', 'https://lemonadex.vn', 'businessHours', '9:00 - 21:30 hằng ngày')::text,
            revision = revision + 1, updated_by = v_admin, updated_at = v_now
        WHERE setting_group = 'STORE' AND setting_key = 'CONFIG';
    END IF;

    -- Configured shipping fees with a free-shipping threshold, only while shipping still has the defaults.
    SELECT setting_value::jsonb INTO v_value FROM system_settings WHERE setting_group = 'SHIPPING' AND setting_key = 'CONFIG';
    IF v_value IS NOT NULL AND (v_value->>'useConfiguredFees')::boolean = false AND coalesce((v_value->>'defaultBaseFee')::numeric, 0) = 0 THEN
        UPDATE system_settings SET setting_value = jsonb_build_object('shippingEnabled', true, 'useConfiguredFees', true,
                'defaultBaseFee', 30000, 'freeShippingThreshold', 499000)::text,
            revision = revision + 1, updated_by = v_admin, updated_at = v_now
        WHERE setting_group = 'SHIPPING' AND setting_key = 'CONFIG';
    END IF;

    INSERT INTO shipping_methods(code, name, provider, base_fee, estimated_days, is_enabled) VALUES
        ('STANDARD', 'Giao hàng tiêu chuẩn', 'Giao Hàng Nhanh', 30000, 3, TRUE),
        ('EXPRESS', 'Giao hỏa tốc nội thành', 'Ahamove', 55000, 1, TRUE)
    ON CONFLICT (code) DO NOTHING;

    -- Vietnamese names for the payment methods seeded by V7, if still in English.
    UPDATE payment_methods SET name = 'Thanh toán khi nhận hàng (COD)' WHERE code = 'COD' AND name = 'Cash on delivery';
    UPDATE payment_methods SET name = 'Chuyển khoản ngân hàng',
            configuration = jsonb_build_object('kind', 'BANK_TRANSFER',
                'bankDetails', jsonb_build_object('bankName', 'Vietcombank', 'accountNumber', '0071000428813', 'accountHolder', 'CONG TY TNHH THOI TRANG LEMONADEX'),
                'instructions', 'Ghi mã đơn hàng trong nội dung chuyển khoản. Đơn được xác nhận khi cửa hàng nhận được tiền.'),
            revision = revision + 1
        WHERE code = 'BANK_TRANSFER' AND name = 'Bank transfer';

    ---------------------------------------------------------------------------------------------
    -- Thương hiệu, danh mục, màu, size
    ---------------------------------------------------------------------------------------------
    FOR v_item IN SELECT * FROM jsonb_array_elements(c_brands) LOOP
        INSERT INTO brands(name, slug, description, status) VALUES (v_item->>'name', v_item->>'slug', v_item->>'description', 'ACTIVE')
        ON CONFLICT (slug) DO NOTHING;
    END LOOP;

    FOR v_item IN SELECT * FROM jsonb_array_elements(c_categories) LOOP
        INSERT INTO categories(name, slug, status) VALUES (v_item->>'name', v_item->>'slug', 'ACTIVE')
        ON CONFLICT (slug) DO NOTHING;
    END LOOP;

    -- Colors and sizes: reuse one with the same name; otherwise create it. A code already taken by
    -- another color/size (codes stay unique even after deletion) gets a suffix instead of borrowing it.
    FOR v_item IN SELECT * FROM jsonb_array_elements(c_colors) LOOP
        SELECT id INTO v_id FROM colors WHERE lower(name) = lower(v_item->>'name') AND NOT deleted ORDER BY created_at, id LIMIT 1;
        IF v_id IS NULL THEN
            v_code := v_item->>'code';
            v_idx := 1;
            WHILE EXISTS (SELECT 1 FROM colors WHERE code = v_code) LOOP
                v_code := (v_item->>'code') || '_LX' || CASE WHEN v_idx > 1 THEN v_idx::text ELSE '' END;
                v_idx := v_idx + 1;
            END LOOP;
            INSERT INTO colors(name, code, hex_code, status) VALUES (v_item->>'name', v_code, v_item->>'hex', 'ACTIVE') RETURNING id INTO v_id;
        END IF;
        v_color_ids := v_color_ids || jsonb_build_object(lower(v_item->>'name'), v_id);
    END LOOP;

    FOR v_item IN SELECT * FROM jsonb_array_elements(c_sizes) LOOP
        SELECT id INTO v_id FROM sizes WHERE lower(name) = lower(v_item->>'name') AND NOT deleted ORDER BY created_at, id LIMIT 1;
        IF v_id IS NULL THEN
            v_code := v_item->>'code';
            v_idx := 1;
            WHILE EXISTS (SELECT 1 FROM sizes WHERE code = v_code) LOOP
                v_code := (v_item->>'code') || '_LX' || CASE WHEN v_idx > 1 THEN v_idx::text ELSE '' END;
                v_idx := v_idx + 1;
            END LOOP;
            INSERT INTO sizes(name, code, sort_order, status) VALUES (v_item->>'name', v_code, (v_item->>'sort')::int, 'ACTIVE') RETURNING id INTO v_id;
        END IF;
        v_size_ids := v_size_ids || jsonb_build_object(lower(v_item->>'name'), v_id);
    END LOOP;

    ---------------------------------------------------------------------------------------------
    -- Kho
    ---------------------------------------------------------------------------------------------
    SELECT id INTO v_wh_hcm FROM warehouses WHERE name = 'Kho Quận 7 - TP. Hồ Chí Minh' AND NOT deleted;
    IF v_wh_hcm IS NULL THEN
        INSERT INTO warehouses(name, address, status) VALUES ('Kho Quận 7 - TP. Hồ Chí Minh', '58 Nguyễn Thị Thập, Phường Tân Phú, Quận 7, TP. Hồ Chí Minh', 'ACTIVE')
        RETURNING id INTO v_wh_hcm;
    END IF;
    SELECT id INTO v_wh_hn FROM warehouses WHERE name = 'Kho Cầu Giấy - Hà Nội' AND NOT deleted;
    IF v_wh_hn IS NULL THEN
        INSERT INTO warehouses(name, address, status) VALUES ('Kho Cầu Giấy - Hà Nội', '112 Trần Thái Tông, Phường Dịch Vọng Hậu, Quận Cầu Giấy, Hà Nội', 'ACTIVE')
        RETURNING id INTO v_wh_hn;
    END IF;

    ---------------------------------------------------------------------------------------------
    -- Sản phẩm, ảnh, biến thể và tồn kho đầu kỳ
    ---------------------------------------------------------------------------------------------
    FOR v_item IN SELECT * FROM jsonb_array_elements(c_products) LOOP
        SELECT id INTO v_product FROM products WHERE product_code = v_item->>'code';
        v_new := v_product IS NULL;
        IF v_new THEN
            INSERT INTO products(product_code, name, slug, description, short_description, brand_id, category_id, material, gender, base_price, status,
                                 created_at, updated_at)
            VALUES (v_item->>'code', v_item->>'name', v_item->>'slug', v_item->>'description', v_item->>'short',
                    (SELECT id FROM brands WHERE slug = v_item->>'brand'),
                    (SELECT id FROM categories WHERE slug = v_item->>'category'),
                    v_item->>'material', v_item->>'gender', (v_item->>'price')::numeric, 'ACTIVE',
                    -- Spread creation dates so "newest" sorting looks natural.
                    -- One day apart, never in the future however many products the list grows to.
                    v_now - make_interval(days => greatest(0, 70 - (SELECT count(*)::int FROM products WHERE product_code LIKE 'LXS-%'))),
                    v_now)
            RETURNING id INTO v_product;

            v_idx := 0;
            FOR v_sub IN SELECT * FROM jsonb_array_elements(v_item->'images') LOOP
                INSERT INTO product_images(product_id, image_url, alt_text, is_primary, sort_order)
                VALUES (v_product, format(c_img, v_sub #>> '{}', v_sub #>> '{}'),
                        CASE WHEN v_idx = 0 THEN v_item->>'name' ELSE v_item->>'name' || ' - ảnh ' || (v_idx + 1) END,
                        v_idx = 0, v_idx);
                v_idx := v_idx + 1;
            END LOOP;
        END IF;

        FOR v_sub IN SELECT * FROM jsonb_array_elements(v_item->'colors') LOOP
            v_color := (v_color_ids->>lower(v_sub #>> '{}'))::uuid;
            IF v_color IS NULL THEN
                RAISE EXCEPTION 'Sản phẩm % dùng màu "%" không có trong danh sách màu của seed.', v_item->>'code', v_sub #>> '{}';
            END IF;
            FOR v_line IN SELECT sz AS name FROM jsonb_array_elements_text(v_item->'sizes') sz LOOP
                v_size := (v_size_ids->>lower(v_line.name))::uuid;
                IF v_size IS NULL THEN
                    RAISE EXCEPTION 'Sản phẩm % dùng size "%" không có trong danh sách size của seed.', v_item->>'code', v_line.name;
                END IF;
                -- SKUs use the seed's own codes, so they are the same in every database.
                v_sku := (v_item->>'code') || '-'
                    || (SELECT c->>'code' FROM jsonb_array_elements(c_colors) c WHERE lower(c->>'name') = lower(v_sub #>> '{}')) || '-'
                    || (SELECT z->>'code' FROM jsonb_array_elements(c_sizes) z WHERE lower(z->>'name') = lower(v_line.name));
                INSERT INTO product_variants(product_id, color_id, size_id, sku, price, compare_at_price, status)
                VALUES (v_product, v_color, v_size, v_sku, (v_item->>'price')::numeric, (v_item->>'compare')::numeric, 'ACTIVE')
                ON CONFLICT (product_id, color_id, size_id) DO NOTHING;
                SELECT id, sku INTO v_variant, v_sku FROM product_variants WHERE product_id = v_product AND color_id = v_color AND size_id = v_size;

                -- Opening stock: HCM holds every variant, Hà Nội about two thirds; a few variants are sold out.
                FOR v_stock IN SELECT * FROM (VALUES (v_wh_hcm, 6 + abs(hashtext(v_sku)) % 28), (v_wh_hn, CASE WHEN abs(hashtext(v_sku || 'HN')) % 3 = 0 THEN 0 ELSE 3 + abs(hashtext(v_sku || 'HN')) % 15 END)) AS s(warehouse, qty) LOOP
                    IF NOT EXISTS (SELECT 1 FROM inventories WHERE warehouse_id = v_stock.warehouse AND product_variant_id = v_variant) THEN
                        v_qty := CASE WHEN v_sku = ANY (c_sold_out) THEN 0 ELSE v_stock.qty END;
                        INSERT INTO inventories(warehouse_id, product_variant_id, quantity_on_hand, quantity_reserved, created_at, updated_at)
                        VALUES (v_stock.warehouse, v_variant, v_qty, 0, v_now - interval '60 days', v_now - interval '60 days');
                        IF v_qty > 0 THEN
                            INSERT INTO inventory_transactions(warehouse_id, product_variant_id, transaction_type, quantity, quantity_before, quantity_after,
                                                               reserved_before, reserved_after, reference_type, note, created_by, created_at)
                            VALUES (v_stock.warehouse, v_variant, 'RECEIPT', v_qty, 0, v_qty, 0, 0, 'MANUAL', 'Nhập hàng đầu kỳ (dữ liệu mẫu)', v_admin, v_now - interval '60 days');
                        END IF;
                    END IF;
                END LOOP;
            END LOOP;
        END LOOP;
    END LOOP;

    ---------------------------------------------------------------------------------------------
    -- Bộ sưu tập
    ---------------------------------------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM collections WHERE slug = 'thu-dong-2026') THEN
        INSERT INTO collections(name, slug, description, image_url, start_at, end_at, status)
        VALUES ('Thu đông 2026', 'thu-dong-2026', 'Áo khoác, hoodie và đầm cho những ngày se lạnh.', format(c_img, 9968414, 9968414),
                v_now - interval '20 days', v_now + interval '120 days', 'ACTIVE')
        RETURNING id INTO v_id;
        INSERT INTO collection_products(collection_id, product_id, sort_order)
        SELECT v_id, p.id, x.ord - 1
        FROM unnest(ARRAY['LXS-KH01', 'LXS-KH02', 'LXS-KH03', 'LXS-HD01', 'LXS-HD02', 'LXS-VD02']) WITH ORDINALITY AS x(code, ord)
        JOIN products p ON p.product_code = x.code;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM collections WHERE slug = 'tu-do-basic') THEN
        INSERT INTO collections(name, slug, description, image_url, status)
        VALUES ('Tủ đồ basic', 'tu-do-basic', 'Những món mặc được mỗi ngày, dễ phối với nhau.', format(c_img, 8217536, 8217536), 'ACTIVE')
        RETURNING id INTO v_id;
        INSERT INTO collection_products(collection_id, product_id, sort_order)
        SELECT v_id, p.id, x.ord - 1
        FROM unnest(ARRAY['LXS-TH01', 'LXS-TH03', 'LXS-SM02', 'LXS-PL03', 'LXS-JN01', 'LXS-KK01']) WITH ORDINALITY AS x(code, ord)
        JOIN products p ON p.product_code = x.code;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM collections WHERE slug = 'do-cong-so') THEN
        INSERT INTO collections(name, slug, description, image_url, status)
        VALUES ('Đồ công sở', 'do-cong-so', 'Sơ mi, blazer, quần tây và chân váy cho tuần làm việc.', format(c_img, 15345385, 15345385), 'ACTIVE')
        RETURNING id INTO v_id;
        INSERT INTO collection_products(collection_id, product_id, sort_order)
        SELECT v_id, p.id, x.ord - 1
        FROM unnest(ARRAY['LXS-KH06', 'LXS-SM04', 'LXS-QT01', 'LXS-CV03', 'LXS-AL03', 'LXS-SM02', 'LXS-KK02']) WITH ORDINALITY AS x(code, ord)
        JOIN products p ON p.product_code = x.code;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM collections WHERE slug = 'phu-kien-mua-lanh') THEN
        INSERT INTO collections(name, slug, description, image_url, status)
        VALUES ('Phụ kiện mùa lạnh', 'phu-kien-mua-lanh', 'Khăn len, mũ dạ và áo len để giữ ấm.', format(c_img, 7820684, 7820684), 'ACTIVE')
        RETURNING id INTO v_id;
        INSERT INTO collection_products(collection_id, product_id, sort_order)
        SELECT v_id, p.id, x.ord - 1
        FROM unnest(ARRAY['LXS-PK04', 'LXS-PK06', 'LXS-AL01', 'LXS-AL04', 'LXS-KH07']) WITH ORDINALITY AS x(code, ord)
        JOIN products p ON p.product_code = x.code;
    END IF;

    ---------------------------------------------------------------------------------------------
    -- Banner: trang chủ cửa hàng (HOME_HERO) và trang giới thiệu (INTRO_HERO, INTRO_STORY)
    ---------------------------------------------------------------------------------------------
    INSERT INTO banners(title, image_url, link_url, position, sort_order, start_at, end_at, status)
    SELECT b.title, format(c_img, b.photo, b.photo), b.link, b.position, b.sort, NULL::timestamptz, NULL::timestamptz, 'ACTIVE'
    FROM (VALUES
        ('Thu đông 2026', 9968414, '/shop/products?sort=newest', 'HOME_HERO', 0),
        ('LemonadeX - thời trang mặc mỗi ngày', 10057713, '/shop', 'INTRO_HERO', 0),
        ('Cửa hàng LemonadeX Quận 5', 5424922, NULL, 'INTRO_STORY', 0),
        ('Kệ hàng mới về mỗi tuần', 4857762, NULL, 'INTRO_STORY', 1),
        ('Không gian thử đồ', 19599223, NULL, 'INTRO_STORY', 2)
    ) AS b(title, photo, link, position, sort)
    WHERE NOT EXISTS (SELECT 1 FROM banners x WHERE x.title = b.title AND x.position = b.position AND NOT x.deleted);

    ---------------------------------------------------------------------------------------------
    -- Lookbook và bài viết
    ---------------------------------------------------------------------------------------------
    FOR v_item IN SELECT * FROM jsonb_array_elements('[
        {"slug": "phoi-do-trench-coat-mua-thu", "title": "Phối trench coat cho mùa thu", "type": "LOOKBOOK", "days": 9, "thumb": 9968540, "images": [9968540, 10057713, 20867424],
         "content": "Trench coat màu be là món đầu tư đáng giá nhất mùa thu.\nMặc với áo len cổ tròn và quần jean ống đứng cho ngày đi làm, hoặc khoác ngoài đầm đen dài cho buổi tối.\nThắt đai hờ phía sau để dáng áo gọn hơn khi không cài cúc."},
        {"slug": "tu-do-basic-5-mon-cho-nam", "title": "Tủ đồ basic 5 món cho nam", "type": "LOOKBOOK", "days": 16, "thumb": 17630522, "images": [17630522, 775771, 9775489],
         "content": "Áo thun trắng, áo thun đen, sơ mi oxford, quần jean ống đứng và quần kaki chino.\nNăm món này phối được với nhau thành hơn mười bộ cho cả tuần.\nChọn màu trung tính để món nào cũng hợp với món nào."},
        {"slug": "dam-trang-toi-gian-cho-tiec-toi", "title": "Đầm trắng tối giản cho tiệc tối", "type": "LOOKBOOK", "days": 24, "thumb": 30736118, "images": [30736118, 30736117, 30736113],
         "content": "Đầm suông lụa trắng đi cùng giày cao gót mảnh và túi nhỏ.\nGiữ phụ kiện tối thiểu để đường cắt của đầm là điểm nhấn.\nNếu trời lạnh, khoác thêm áo dạ dáng dài màu nâu."},
        {"slug": "cach-chon-size-ao-so-mi", "title": "Cách chọn size áo sơ mi vừa người", "type": "ARTICLE", "days": 30, "thumb": 4036896, "images": [],
         "content": "Đo vòng ngực ở chỗ rộng nhất, giữ thước dây ngang và vừa sát người.\nSo với bảng size của từng sản phẩm, nếu nằm giữa hai size thì chọn size lớn hơn với áo form rộng.\nVai áo nên kết thúc đúng xương vai, tay áo chạm cổ tay khi buông thẳng."}
    ]'::jsonb) LOOP
        IF NOT EXISTS (SELECT 1 FROM articles WHERE slug = v_item->>'slug') THEN
            INSERT INTO articles(title, slug, thumbnail_url, content, article_type, author_id, status, published_at, created_at, updated_at)
            VALUES (v_item->>'title', v_item->>'slug', format(c_img, v_item->>'thumb', v_item->>'thumb'), v_item->>'content', v_item->>'type',
                    v_admin, 'PUBLISHED', v_now - make_interval(days => (v_item->>'days')::int),
                    v_now - make_interval(days => (v_item->>'days')::int), v_now - make_interval(days => (v_item->>'days')::int))
            RETURNING id INTO v_id;
            v_idx := 0;
            FOR v_sub IN SELECT * FROM jsonb_array_elements(v_item->'images') LOOP
                INSERT INTO article_images(article_id, image_url, sort_order) VALUES (v_id, format(c_img, v_sub #>> '{}', v_sub #>> '{}'), v_idx);
                v_idx := v_idx + 1;
            END LOOP;
        END IF;
    END LOOP;

    ---------------------------------------------------------------------------------------------
    -- Chính sách cửa hàng: chỉ thêm khi loại đó chưa có phiên bản đang áp dụng
    ---------------------------------------------------------------------------------------------
    FOR v_item IN SELECT * FROM jsonb_array_elements('[
        {"type": "SHIPPING", "title": "Chính sách giao hàng", "content": "Giao hàng toàn quốc qua Giao Hàng Nhanh, thời gian 2-4 ngày làm việc. Nội thành TP. Hồ Chí Minh có giao hỏa tốc trong ngày.\n\nPhí giao hàng tiêu chuẩn 30.000 ₫, miễn phí cho đơn từ 499.000 ₫ sau giảm giá.\n\nĐơn được đóng gói và bàn giao cho đơn vị vận chuyển trong 24 giờ sau khi cửa hàng xác nhận. Khách nhận được mã vận đơn để theo dõi trong trang Đơn hàng."},
        {"type": "RETURN_EXCHANGE", "title": "Chính sách đổi trả", "content": "Đổi size, đổi màu hoặc trả hàng trong 7 ngày kể từ khi nhận hàng.\n\nSản phẩm cần còn nguyên tem, chưa giặt và chưa qua sử dụng. Đồ lót, đồ bơi và hàng giảm giá trên 50% không áp dụng đổi trả.\n\nCửa hàng hỗ trợ phí giao hàng khi sản phẩm lỗi do sản xuất hoặc giao nhầm. Tiền hoàn được chuyển trong 3-5 ngày làm việc sau khi cửa hàng nhận lại hàng."},
        {"type": "PAYMENT", "title": "Chính sách thanh toán", "content": "Cửa hàng nhận thanh toán khi nhận hàng (COD) và chuyển khoản ngân hàng.\n\nVới chuyển khoản, ghi mã đơn hàng trong nội dung chuyển khoản. Đơn được xác nhận khi cửa hàng nhận được tiền."},
        {"type": "PRIVACY", "title": "Chính sách bảo mật", "content": "LemonadeX chỉ dùng thông tin của khách để xử lý đơn hàng, giao hàng và chăm sóc khách hàng.\n\nThông tin không được bán hay chia sẻ cho bên thứ ba, trừ đơn vị vận chuyển cần địa chỉ và số điện thoại để giao hàng.\n\nKhách có thể yêu cầu xem, sửa hoặc xóa thông tin cá nhân qua email hotro@lemonadex.vn."},
        {"type": "TERMS_OF_SERVICE", "title": "Điều khoản dịch vụ", "content": "Khi đặt hàng tại LemonadeX, khách đồng ý với giá và khuyến mãi hiển thị tại thời điểm đặt.\n\nCửa hàng có quyền hủy đơn khi sản phẩm hết hàng ngoài dự kiến và sẽ thông báo cho khách. Khoản đã thanh toán được hoàn đầy đủ."},
        {"type": "WARRANTY", "title": "Chính sách bảo hành", "content": "Bảo hành đường may và khóa kéo trong 30 ngày kể từ ngày nhận hàng.\n\nMang sản phẩm đến cửa hàng hoặc gửi về kho Quận 7 kèm mã đơn hàng để được sửa miễn phí."}
    ]'::jsonb) LOOP
        IF NOT EXISTS (SELECT 1 FROM store_policies WHERE policy_type = v_item->>'type' AND status = 'ACTIVE' AND NOT deleted) THEN
            INSERT INTO store_policies(policy_type, title, content, version, status, updated_by, updated_at, created_at, activated_at)
            VALUES (v_item->>'type', v_item->>'title', v_item->>'content',
                    coalesce((SELECT max(version) FROM store_policies WHERE policy_type = v_item->>'type'), 0) + 1,
                    'ACTIVE', v_admin, v_now - interval '45 days', v_now - interval '45 days', v_now - interval '45 days');
        END IF;
    END LOOP;

    ---------------------------------------------------------------------------------------------
    -- Mã giảm giá và chương trình khuyến mãi
    ---------------------------------------------------------------------------------------------
    INSERT INTO coupons(code, name, discount_type, discount_value, max_discount, minimum_order_value, usage_limit, usage_limit_per_customer,
                        used_count, start_at, end_at, status)
    SELECT c.code, c.name, c.kind, c.value, c.max_discount, c.minimum, c.usage_limit, c.per_customer, 0,
           v_now - interval '1 day', v_now + interval '365 days', 'ACTIVE'
    FROM (VALUES
        ('WELCOME10', 'Giảm 10% cho đơn đầu tiên', 'PERCENTAGE', 10::numeric, 100000::numeric, 300000::numeric, NULL::int, 1),
        ('FREESHIP30K', 'Giảm 30.000 ₫ phí giao hàng', 'FIXED_AMOUNT', 30000, NULL, 499000, 500, 3),
        ('LXMEMBER50K', 'Giảm 50.000 ₫ cho đơn từ 800.000 ₫', 'FIXED_AMOUNT', 50000, NULL, 800000, 300, 2)
    ) AS c(code, name, kind, value, max_discount, minimum, usage_limit, per_customer)
    WHERE NOT EXISTS (SELECT 1 FROM coupons x WHERE lower(x.code) = lower(c.code));

    IF NOT EXISTS (SELECT 1 FROM promotions WHERE name = 'Tuần lễ áo khoác: giảm 15%' AND NOT deleted) THEN
        INSERT INTO promotions(name, description, promotion_type, discount_type, discount_value, start_at, end_at, priority, status)
        VALUES ('Tuần lễ áo khoác: giảm 15%', 'Giảm 15% mọi mẫu áo khoác khi chương trình khuyến mãi được áp dụng cho đơn.',
                'CATEGORY_DISCOUNT', 'PERCENTAGE', 15, v_now - interval '2 days', v_now + interval '30 days', 1, 'ACTIVE')
        RETURNING id INTO v_id;
        INSERT INTO promotion_categories(promotion_id, category_id) SELECT v_id, id FROM categories WHERE slug = 'ao-khoac';
    END IF;

    ---------------------------------------------------------------------------------------------
    -- Khách hàng có tài khoản đăng nhập (mật khẩu: Khachhang@2026)
    ---------------------------------------------------------------------------------------------
    FOR v_item IN SELECT * FROM jsonb_array_elements(c_customers) LOOP
        SELECT id INTO v_account FROM accounts WHERE lower(email) = lower(v_item->>'email');
        IF v_account IS NULL THEN
            INSERT INTO accounts(email, password_hash, status, deleted, created_at, updated_at)
            VALUES (v_item->>'email', '$2a$12$xrQj3rZZa17ZJHtfRJTdu.eRWmtTy48VkIrTrI46s8KPX3eOtHmAC', 'ACTIVE', FALSE,
                    v_now - interval '50 days', v_now - interval '50 days')
            RETURNING id INTO v_account;
            INSERT INTO account_roles(account_id, role_id) VALUES (v_account, v_customer_role) ON CONFLICT DO NOTHING;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM customers WHERE account_id = v_account) THEN
            INSERT INTO customers(account_id, full_name, phone, gender, date_of_birth, status, created_at, updated_at)
            VALUES (v_account, v_item->>'name', v_item->>'phone', v_item->>'gender', (v_item->>'dob')::date, 'ACTIVE',
                    v_now - interval '50 days', v_now - interval '50 days')
            RETURNING id INTO v_customer;
            INSERT INTO customer_addresses(customer_id, recipient_name, phone, province, district, ward, address_line, is_default)
            VALUES (v_customer, v_item->>'name', v_item->>'phone', v_item #>> '{address,province}', v_item #>> '{address,district}',
                    v_item #>> '{address,ward}', v_item #>> '{address,line}', TRUE);
        END IF;
    END LOOP;

    ---------------------------------------------------------------------------------------------
    -- Đơn hàng mẫu: ghi đủ sổ kho (giữ hàng, xuất giao), lịch sử đơn, vận đơn, thanh toán, đánh giá.
    -- Chỉ tạo một lần (theo mã đơn LX-SEED...).
    ---------------------------------------------------------------------------------------------
    FOR v_item IN SELECT * FROM jsonb_array_elements(c_orders) LOOP
        CONTINUE WHEN EXISTS (SELECT 1 FROM orders WHERE order_code = v_item->>'code');
        SELECT c.id, c.account_id INTO v_customer, v_account FROM customers c JOIN accounts a ON a.id = c.account_id
        WHERE lower(a.email) = lower(v_item->>'customer') AND NOT c.deleted;
        CONTINUE WHEN v_customer IS NULL;
        -- Every line must be in stock at the HCM warehouse, otherwise the order is skipped.
        CONTINUE WHEN EXISTS (
            SELECT 1 FROM jsonb_array_elements(v_item->'items') li
            LEFT JOIN product_variants v ON v.sku = li->>'sku'
            LEFT JOIN inventories i ON i.product_variant_id = v.id AND i.warehouse_id = v_wh_hcm
            WHERE i.id IS NULL OR i.quantity_on_hand - i.quantity_reserved < (li->>'qty')::int);

        v_status := v_item->>'status';
        v_placed := v_now - make_interval(days => (v_item->>'days')::int);
        SELECT sum(v.price * (li->>'qty')::int) INTO v_subtotal
        FROM jsonb_array_elements(v_item->'items') li JOIN product_variants v ON v.sku = li->>'sku';
        v_total := v_subtotal + CASE WHEN v_subtotal >= 499000 THEN 0 ELSE 30000 END;

        INSERT INTO orders(order_code, customer_id, warehouse_id, order_status, payment_status, shipping_status, subtotal, discount_amount,
                           shipping_fee, total_amount, recipient_name, recipient_phone, shipping_address, placed_at, created_at, updated_at,
                           shipping_method_code, shipping_fee_configured)
        SELECT v_item->>'code', v_customer, v_wh_hcm, 'PLACED', 'UNPAID', 'NOT_SHIPPED', v_subtotal, 0, v_total - v_subtotal, v_total,
               c.full_name, c.phone,
               concat_ws(', ', a.address_line, a.ward, a.district, a.province), v_placed, v_placed, v_placed, 'STANDARD', TRUE
        FROM customers c JOIN customer_addresses a ON a.customer_id = c.id AND a.is_default WHERE c.id = v_customer
        RETURNING id INTO v_order;
        INSERT INTO order_status_history(order_id, from_status, to_status, note, changed_by, created_at)
        VALUES (v_order, NULL, 'PLACED', 'Order created and stock reserved', v_account, v_placed);

        -- Lines and reservations.
        FOR v_line IN
            SELECT li, v.id AS variant_id, v.sku, v.price, p.id AS product_id, p.name AS product_name, co.name AS color_name, s.name AS size_name,
                   i.id AS stock_id, (li->>'qty')::int AS qty
            FROM jsonb_array_elements(v_item->'items') li
            JOIN product_variants v ON v.sku = li->>'sku' JOIN products p ON p.id = v.product_id
            JOIN colors co ON co.id = v.color_id JOIN sizes s ON s.id = v.size_id
            JOIN inventories i ON i.product_variant_id = v.id AND i.warehouse_id = v_wh_hcm
        LOOP
            INSERT INTO order_items(order_id, product_variant_id, inventory_id, product_name, sku, color_name, size_name, unit_price, quantity,
                                    discount_amount, total_amount)
            VALUES (v_order, v_line.variant_id, v_line.stock_id, v_line.product_name, v_line.sku, v_line.color_name, v_line.size_name,
                    v_line.price, v_line.qty, 0, v_line.price * v_line.qty);
            INSERT INTO inventory_transactions(warehouse_id, product_variant_id, transaction_type, quantity, quantity_before, quantity_after,
                                               reserved_before, reserved_after, reference_type, reference_id, note, created_by, created_at)
            SELECT warehouse_id, product_variant_id, 'RESERVE', v_line.qty, quantity_on_hand, quantity_on_hand, quantity_reserved,
                   quantity_reserved + v_line.qty, 'ORDER', v_order, 'Stock reserved', v_account, v_placed
            FROM inventories WHERE id = v_line.stock_id;
            UPDATE inventories SET quantity_reserved = quantity_reserved + v_line.qty, updated_at = v_placed WHERE id = v_line.stock_id;
        END LOOP;

        -- Cash on delivery, recorded as pending at checkout.
        INSERT INTO payments(order_id, payment_method, amount, status, created_at) VALUES (v_order, 'COD', v_total, 'PENDING', v_placed)
        RETURNING id INTO v_payment;
        INSERT INTO payment_transactions(payment_id, transaction_code, provider, amount, status, created_at)
        VALUES (v_payment, 'PT-' || gen_random_uuid(), 'MANUAL', v_total, 'PENDING', v_placed);

        IF v_status IN ('SHIPPED', 'DELIVERED', 'COMPLETED') THEN
            UPDATE orders SET order_status = 'CONFIRMED', confirmed_at = v_placed + interval '2 hours' WHERE id = v_order;
            INSERT INTO order_status_history(order_id, from_status, to_status, note, changed_by, created_at)
            VALUES (v_order, 'PLACED', 'CONFIRMED', 'Đã gọi xác nhận với khách', v_admin, v_placed + interval '2 hours');

            INSERT INTO shipments(order_id, shipping_provider, tracking_code, shipping_fee, status, shipped_at, created_at)
            VALUES (v_order, 'Giao Hàng Nhanh', 'GHN' || right(replace(v_item->>'code', 'LX-SEED', ''), 4) || to_char(v_placed, 'MMDD'),
                    25000, 'SHIPPED', v_placed + interval '1 day', v_placed + interval '20 hours')
            RETURNING id INTO v_shipment;
            INSERT INTO shipment_status_history(shipment_id, status, description, created_at) VALUES
                (v_shipment, 'PENDING', 'Đã tạo vận đơn', v_placed + interval '20 hours'),
                (v_shipment, 'SHIPPED', 'Đã bàn giao cho đơn vị vận chuyển', v_placed + interval '1 day');
            -- Dispatch: reserved units leave the warehouse.
            FOR v_line IN SELECT inventory_id, quantity FROM order_items WHERE order_id = v_order LOOP
                INSERT INTO inventory_transactions(warehouse_id, product_variant_id, transaction_type, quantity, quantity_before, quantity_after,
                                                   reserved_before, reserved_after, reference_type, reference_id, note, created_by, created_at)
                SELECT warehouse_id, product_variant_id, 'SHIPMENT', -v_line.quantity, quantity_on_hand, quantity_on_hand - v_line.quantity,
                       quantity_reserved, quantity_reserved - v_line.quantity, 'ORDER', v_order, 'Reserved stock dispatched', v_admin,
                       v_placed + interval '1 day'
                FROM inventories WHERE id = v_line.inventory_id;
                UPDATE inventories SET quantity_on_hand = quantity_on_hand - v_line.quantity, quantity_reserved = quantity_reserved - v_line.quantity,
                       updated_at = v_placed + interval '1 day' WHERE id = v_line.inventory_id;
            END LOOP;
            UPDATE orders SET order_status = 'SHIPPED', shipping_status = 'SHIPPED' WHERE id = v_order;
            INSERT INTO order_status_history(order_id, from_status, to_status, note, changed_by, created_at)
            VALUES (v_order, 'CONFIRMED', 'SHIPPED', 'Shipment dispatched', v_admin, v_placed + interval '1 day');
        END IF;

        IF v_status IN ('DELIVERED', 'COMPLETED') THEN
            UPDATE shipments SET status = 'DELIVERED', delivered_at = v_placed + interval '3 days' WHERE id = v_shipment;
            INSERT INTO shipment_status_history(shipment_id, status, description, created_at)
            VALUES (v_shipment, 'DELIVERED', 'Giao hàng thành công', v_placed + interval '3 days');
            UPDATE orders SET order_status = 'DELIVERED', shipping_status = 'DELIVERED' WHERE id = v_order;
            INSERT INTO order_status_history(order_id, from_status, to_status, note, changed_by, created_at)
            VALUES (v_order, 'SHIPPED', 'DELIVERED', 'Shipment delivered', v_admin, v_placed + interval '3 days');
            -- The courier collected the cash.
            UPDATE payments SET status = 'PAID', paid_at = v_placed + interval '3 days' WHERE id = v_payment;
            INSERT INTO payment_transactions(payment_id, transaction_code, provider, amount, status, created_at)
            VALUES (v_payment, 'PT-' || gen_random_uuid(), 'MANUAL', v_total, 'PAID', v_placed + interval '3 days');
            UPDATE orders SET payment_status = 'PAID' WHERE id = v_order;
        END IF;

        IF v_status = 'COMPLETED' THEN
            UPDATE orders SET order_status = 'COMPLETED', completed_at = v_placed + interval '4 days' WHERE id = v_order;
            INSERT INTO order_status_history(order_id, from_status, to_status, note, changed_by, created_at)
            VALUES (v_order, 'DELIVERED', 'COMPLETED', NULL, v_admin, v_placed + interval '4 days');
        END IF;

        UPDATE orders SET updated_at = v_placed + interval '4 days' WHERE id = v_order AND v_status <> 'PLACED';

        -- Approved reviews from delivered orders.
        IF v_status IN ('DELIVERED', 'COMPLETED') THEN
            FOR v_line IN
                SELECT oi.id AS order_item_id, v.product_id, (li->>'rating')::smallint AS rating, li->>'comment' AS comment
                FROM jsonb_array_elements(v_item->'items') li
                JOIN order_items oi ON oi.order_id = v_order AND oi.sku = li->>'sku'
                JOIN product_variants v ON v.id = oi.product_variant_id
                WHERE li ? 'rating'
            LOOP
                INSERT INTO product_reviews(product_id, customer_id, order_item_id, rating, comment, status, moderation_note, moderated_by,
                                            moderated_at, created_at, updated_at)
                VALUES (v_line.product_id, v_customer, v_line.order_item_id, v_line.rating, v_line.comment, 'APPROVED', 'Nội dung phù hợp',
                        v_admin, v_placed + interval '6 days', v_placed + interval '5 days', v_placed + interval '6 days');
            END LOOP;
        END IF;
    END LOOP;

    RAISE NOTICE 'Seed LemonadeX hoàn tất: % sản phẩm mẫu, % biến thể, % đơn mẫu.',
        (SELECT count(*) FROM products WHERE product_code LIKE 'LXS-%'),
        (SELECT count(*) FROM product_variants v JOIN products p ON p.id = v.product_id WHERE p.product_code LIKE 'LXS-%'),
        (SELECT count(*) FROM orders WHERE order_code LIKE 'LX-SEED%');
END
$seed$;
