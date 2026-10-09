package lemonadex.project.clothes.features.catalog.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lemonadex.project.clothes.common.util.DateTimeUtils;
import lemonadex.project.clothes.features.catalog.model.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Response bodies of the catalog management API. Counts only include rows that are not deleted. */
public final class CatalogResponses {
    private CatalogResponses() {}

    public record Ref(UUID id, String name) {}

    public record CategoryResponse(UUID id, UUID parentId, String parentName, String name, String slug, String description,
            CatalogStatus status, long childCount, long productCount,
            @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime createdAt,
            @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime updatedAt) {}

    public record BrandResponse(UUID id, String name, String slug, String logoUrl, String description, CatalogStatus status,
            long productCount,
            @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime createdAt,
            @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime updatedAt) {}

    public record ColorResponse(UUID id, String name, String code, String hexCode, CatalogStatus status, long variantCount,
            @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime createdAt,
            @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime updatedAt) {}

    public record SizeResponse(UUID id, String name, String code, int sortOrder, CatalogStatus status, long variantCount,
            @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime createdAt,
            @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime updatedAt) {}

    public record CollectionProductResponse(UUID id, String productCode, String name, String imageUrl, ProductStatus status) {}

    /** {@code products} is filled by the detail endpoint only; lists carry {@code productCount}. */
    public record CollectionResponse(UUID id, String name, String slug, String description, String imageUrl,
            @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime startAt,
            @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime endAt,
            CatalogStatus status, long productCount, List<CollectionProductResponse> products,
            @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime createdAt,
            @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime updatedAt) {}

    public record ProductImageResponse(UUID id, String url, String altText, boolean primary) {}

    /** {@code minPrice}/{@code maxPrice} span the variant prices and are null without variants. */
    public record ProductResponse(UUID id, String productCode, String name, String slug, String description,
            String shortDescription, Ref brand, Ref category, String material, ProductGender gender, BigDecimal basePrice,
            ProductStatus status, List<ProductImageResponse> images, long variantCount, BigDecimal minPrice, BigDecimal maxPrice,
            @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime createdAt,
            @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime updatedAt) {}

    public record VariantProduct(UUID id, String productCode, String name) {}

    public record VariantColor(UUID id, String name, String code, String hexCode) {}

    public record VariantSize(UUID id, String name, String code) {}

    public record VariantResponse(UUID id, VariantProduct product, VariantColor color, VariantSize size, String sku,
            BigDecimal price, BigDecimal compareAtPrice, CatalogStatus status,
            @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime createdAt,
            @JsonFormat(pattern = DateTimeUtils.DATE_TIME_PATTERN) OffsetDateTime updatedAt) {}

    public record CategoryOption(UUID id, String name, UUID parentId, CatalogStatus status) {}

    public record BrandOption(UUID id, String name, CatalogStatus status) {}

    public record ColorOption(UUID id, String name, String code, String hexCode, CatalogStatus status) {}

    public record SizeOption(UUID id, String name, String code, CatalogStatus status) {}

    /** Every non-deleted category, brand, color and size, for pickers that must not be paginated. */
    public record CatalogOptionsResponse(List<CategoryOption> categories, List<BrandOption> brands,
            List<ColorOption> colors, List<SizeOption> sizes) {}
}
