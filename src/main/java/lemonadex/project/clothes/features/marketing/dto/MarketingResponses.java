package lemonadex.project.clothes.features.marketing.dto;
import lemonadex.project.clothes.features.marketing.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public final class MarketingResponses {
    private MarketingResponses() {}
    public record CouponResponse(UUID id, String code, String name, DiscountType discountType, BigDecimal discountValue,
            BigDecimal maxDiscount, BigDecimal minimumOrderValue, Integer usageLimit, int usageLimitPerCustomer, int usedCount,
            Instant startAt, Instant endAt, MarketingStatus status, Instant createdAt, Instant updatedAt) {}
    public record CouponUsageResponse(UUID id, UUID couponId, UUID customerId, UUID orderId, BigDecimal discountAmount,
            Instant usedAt, boolean released, Instant releasedAt) {}
    public record PromotionResponse(UUID id, String name, String description, PromotionType promotionType, DiscountType discountType,
            BigDecimal discountValue, Instant startAt, Instant endAt, int priority, MarketingStatus status,
            List<UUID> productIds, List<UUID> categoryIds, Instant createdAt, Instant updatedAt) {}
    public record FlashItemResponse(UUID id, UUID productVariantId, BigDecimal flashPrice, int quantityLimit, int soldQuantity, int remainingQuantity) {}
    public record FlashSaleResponse(UUID id, String name, Instant startAt, Instant endAt, MarketingStatus status,
            List<FlashItemResponse> items, Instant createdAt, Instant updatedAt) {}
    public record BannerResponse(UUID id, String title, String imageUrl, String linkUrl, String position, int sortOrder,
            Instant startAt, Instant endAt, MarketingStatus status, Instant createdAt, Instant updatedAt) {}
    public record NotificationResponse(UUID id, String title, String content, NotificationType notificationType, NotificationTarget targetType,
            NotificationStatus status, List<UUID> customerIds, UUID createdBy, Instant publishedAt, long recipientCount, long readCount,
            Instant createdAt, Instant updatedAt) {}
    public record InboxResponse(UUID id, UUID notificationId, String title, String content, NotificationType notificationType,
            boolean read, Instant readAt, Instant createdAt) {}
    public record RecipientResponse(UUID id, UUID customerId, boolean read, Instant readAt, Instant createdAt) {}
    public record MarketingLineQuote(UUID flashSaleItemId, UUID promotionId, BigDecimal discountAmount) {}
    public record CouponQuote(UUID couponId, BigDecimal discountAmount) {}
}
