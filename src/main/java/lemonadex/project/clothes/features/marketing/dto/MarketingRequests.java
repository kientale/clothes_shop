package lemonadex.project.clothes.features.marketing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.features.marketing.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class MarketingRequests {
    private MarketingRequests() {}
    public record CouponRequest(@NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
            @NotBlank @Size(max = 150) String name, @NotNull DiscountType discountType,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 16, fraction = 2) BigDecimal discountValue,
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 16, fraction = 2) BigDecimal maxDiscount,
            @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal minimumOrderValue,
            @Min(1) Integer usageLimit, @NotNull @Min(1) Integer usageLimitPerCustomer,
            @NotNull Instant startAt, @NotNull Instant endAt, @NotNull MarketingStatus status) {}
    public record PromotionRequest(@NotBlank @Size(max = 150) String name, @Size(max = 5000) String description,
            @NotNull PromotionType promotionType, @NotNull DiscountType discountType,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 16, fraction = 2) BigDecimal discountValue,
            @NotNull Instant startAt, @NotNull Instant endAt, @NotNull @Min(0) Integer priority, @NotNull MarketingStatus status,
            @NotNull @Size(max = 100) List<@NotNull UUID> productIds, @NotNull @Size(max = 100) List<@NotNull UUID> categoryIds) {}
    public record FlashItemRequest(@NotNull UUID productVariantId,
            @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal flashPrice,
            @NotNull @Min(1) @Max(1000000000) Integer quantityLimit) {}
    public record FlashSaleRequest(@NotBlank @Size(max = 150) String name, @NotNull Instant startAt, @NotNull Instant endAt,
            @NotNull MarketingStatus status, @NotEmpty @Size(max = 100) List<@Valid @NotNull FlashItemRequest> items) {}
    public record BannerRequest(@NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 2048) @Pattern(regexp = "https?://[^\\s]+") String imageUrl,
            @Size(max = 2048) @Pattern(regexp = "https?://[^\\s]+|/[^\\s]*") String linkUrl,
            @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]+") String position,
            @NotNull @Min(0) Integer sortOrder, Instant startAt, Instant endAt, @NotNull MarketingStatus status) {}
    public record NotificationRequest(@NotBlank @Size(max = 255) String title, @NotBlank @Size(max = 10000) String content,
            @NotNull NotificationType notificationType, @NotNull NotificationTarget targetType,
            @NotNull @Size(max = 1000) List<@NotNull UUID> customerIds) {}
}
