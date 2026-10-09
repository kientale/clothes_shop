package lemonadex.project.clothes.features.storefront.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read models returned by the storefront SQL; the service maps them to public DTOs. */
public final class StorefrontRows {
    private StorefrontRows() {}

    public record CategoryRow(UUID id, UUID parentId, String name, String slug) {}
    public record BrandRow(UUID id, String name, String slug, String logoUrl) {}
    public record ColorRow(UUID id, String name, String code, String hexCode) {}
    public record SizeRow(UUID id, String name, String code, int sortOrder) {}

    /** Product list filters; null means "any". */
    public record ProductQuery(String search, UUID categoryId, UUID brandId, String gender, UUID colorId, UUID sizeId,
            BigDecimal minPrice, BigDecimal maxPrice, boolean inStock, String sort, int page, int size,
            UUID collectionId, UUID wishlistOf) {
        public ProductQuery(String search, UUID categoryId, UUID brandId, String gender, UUID colorId, UUID sizeId,
                BigDecimal minPrice, BigDecimal maxPrice, boolean inStock, String sort, int page, int size) {
            this(search, categoryId, brandId, gender, colorId, sizeId, minPrice, maxPrice, inStock, sort, page, size, null, null);
        }

        public static ProductQuery collection(UUID collectionId, int page, int size) {
            return new ProductQuery(null, null, null, null, null, null, null, null, false, "collection", page, size, collectionId, null);
        }

        public static ProductQuery wishlist(UUID customerId, int page, int size) {
            return new ProductQuery(null, null, null, null, null, null, null, null, false, "wishlist", page, size, null, customerId);
        }
    }

    public record SwatchRow(String name, String hexCode) {}
    public record CardRow(UUID id, String slug, String name, String brandName, String categoryName, String gender, Instant createdAt,
            BigDecimal minPrice, BigDecimal maxPrice, BigDecimal compareAtPrice, long available, String imageUrl, String hoverImageUrl,
            List<SwatchRow> colors) {}
    public record CardPage(List<CardRow> content, long total) {}

    public record NamedRow(UUID id, String name, String slug) {}
    public record ProductHead(UUID id, String slug, String productCode, String name, String shortDescription, String description,
            String material, String gender, NamedRow brand, NamedRow category) {}
    public record ImageRow(String url, String altText) {}
    public record VariantRow(UUID id, String sku, UUID colorId, String colorName, String hexCode, UUID sizeId, String sizeName,
            int sizeSortOrder, BigDecimal price, BigDecimal compareAtPrice, long available) {}
    public record RatingSummary(double average, long count) {}
    public record BannerRow(UUID id, String title, String imageUrl, String linkUrl, String position, int sortOrder) {}
    public record ReviewRow(UUID id, int rating, String comment, String customerName, List<String> images, Instant createdAt) {}

    public record ProfileRow(UUID accountId, UUID customerId, String email, String passwordHash, String fullName, String phone,
            String gender, java.time.LocalDate dateOfBirth) {}
    public record AddressRow(UUID id, String recipientName, String phone, String addressLine, String ward, String district,
            String province, boolean isDefault) {}
    public record CollectionRow(UUID id, String slug, String name, String description, String imageUrl, long productCount,
            Instant startAt, Instant endAt) {}
    public record SitemapEntry(String path, Instant updatedAt) {}
    public record ExchangeRow(UUID variantId, String colorName, String sizeName, long available) {}
    public record CartRow(UUID variantId, UUID productId, String slug, String name, String imageUrl, String colorName, String sizeName,
            String sku, BigDecimal price, int quantity, long available, boolean sellable) {}
    public record AlertRow(UUID id, String email, UUID productVariantId, String productName, String slug, String colorName, String sizeName) {}
    public record AlertSummaryRow(UUID productVariantId, UUID productId, String productName, String sku, String colorName, String sizeName,
            long waiting, long available, Instant latestAt) {}
}
