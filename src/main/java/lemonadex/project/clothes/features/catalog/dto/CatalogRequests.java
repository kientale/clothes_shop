package lemonadex.project.clothes.features.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.features.catalog.model.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Request bodies of the catalog management API. A blank slug is generated from the name;
 * codes and SKUs are stored upper-case and slugs lower-case.
 */
public final class CatalogRequests {
    private CatalogRequests() {}

    private static final String URL = "https?://[^\\s]+";
    private static final String CODE = "[A-Za-z0-9_-]+";
    private static final String SLUG = "[A-Za-z0-9-]*";

    public record CategoryRequest(UUID parentId,
            @NotBlank @jakarta.validation.constraints.Size(max = 150) String name,
            @jakarta.validation.constraints.Size(max = 200) @Pattern(regexp = SLUG) String slug,
            @jakarta.validation.constraints.Size(max = 5000) String description,
            @NotNull CatalogStatus status) {}

    public record BrandRequest(@NotBlank @jakarta.validation.constraints.Size(max = 150) String name,
            @jakarta.validation.constraints.Size(max = 200) @Pattern(regexp = SLUG) String slug,
            @jakarta.validation.constraints.Size(max = 2048) @Pattern(regexp = URL) String logoUrl,
            @jakarta.validation.constraints.Size(max = 5000) String description,
            @NotNull CatalogStatus status) {}

    public record ColorRequest(@NotBlank @jakarta.validation.constraints.Size(max = 100) String name,
            @NotBlank @jakarta.validation.constraints.Size(max = 50) @Pattern(regexp = CODE) String code,
            @Pattern(regexp = "#[0-9A-Fa-f]{6}") String hexCode,
            @NotNull CatalogStatus status) {}

    public record SizeRequest(@NotBlank @jakarta.validation.constraints.Size(max = 50) String name,
            @NotBlank @jakarta.validation.constraints.Size(max = 50) @Pattern(regexp = CODE) String code,
            @NotNull @Min(0) @Max(100000) Integer sortOrder,
            @NotNull CatalogStatus status) {}

    public record CollectionRequest(@NotBlank @jakarta.validation.constraints.Size(max = 150) String name,
            @jakarta.validation.constraints.Size(max = 200) @Pattern(regexp = SLUG) String slug,
            @jakarta.validation.constraints.Size(max = 5000) String description,
            @jakarta.validation.constraints.Size(max = 2048) @Pattern(regexp = URL) String imageUrl,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            @NotNull CatalogStatus status,
            @NotNull @jakarta.validation.constraints.Size(max = 500) List<@NotNull UUID> productIds) {}

    public record ProductImageRequest(@NotBlank @jakarta.validation.constraints.Size(max = 2048) @Pattern(regexp = URL) String url,
            @jakarta.validation.constraints.Size(max = 255) String altText) {}

    public record ProductRequest(@NotBlank @jakarta.validation.constraints.Size(max = 80) @Pattern(regexp = CODE) String productCode,
            @NotBlank @jakarta.validation.constraints.Size(max = 255) String name,
            @jakarta.validation.constraints.Size(max = 300) @Pattern(regexp = SLUG) String slug,
            @jakarta.validation.constraints.Size(max = 20000) String description,
            @jakarta.validation.constraints.Size(max = 1000) String shortDescription,
            @NotNull UUID brandId,
            @NotNull UUID categoryId,
            @jakarta.validation.constraints.Size(max = 255) String material,
            @NotNull ProductGender gender,
            @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal basePrice,
            @NotNull ProductStatus status,
            @NotNull @jakarta.validation.constraints.Size(max = 10) List<@Valid @NotNull ProductImageRequest> images) {}

    /** A blank SKU becomes PRODUCTCODE-COLORCODE-SIZECODE. */
    public record VariantCreateRequest(@NotNull UUID productId,
            @NotNull UUID colorId,
            @NotNull UUID sizeId,
            @jakarta.validation.constraints.Size(max = 100) @Pattern(regexp = "[A-Za-z0-9_-]*") String sku,
            @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal price,
            @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal compareAtPrice,
            @NotNull CatalogStatus status) {}

    /** Product, color and size identify a variant and cannot change; create another variant instead. */
    public record VariantUpdateRequest(@NotBlank @jakarta.validation.constraints.Size(max = 100) @Pattern(regexp = CODE) String sku,
            @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal price,
            @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal compareAtPrice,
            @NotNull CatalogStatus status) {}

    /** Creates every color x size combination of a product that does not exist yet. */
    public record VariantBulkRequest(@NotNull UUID productId,
            @NotEmpty @jakarta.validation.constraints.Size(max = 30) Set<@NotNull UUID> colorIds,
            @NotEmpty @jakarta.validation.constraints.Size(max = 30) Set<@NotNull UUID> sizeIds,
            @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal price,
            @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal compareAtPrice,
            @NotNull CatalogStatus status) {}
}
