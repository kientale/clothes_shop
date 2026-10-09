package lemonadex.project.clothes.features.storefront.dto;

import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.features.order.dto.OrderResponses.OrderResponse;
import lemonadex.project.clothes.features.order.dto.OrderResponses.PaymentResponse;
import lemonadex.project.clothes.features.order.dto.OrderResponses.ShipmentResponse;
import com.fasterxml.jackson.annotation.JsonFormat;
import lemonadex.project.clothes.common.util.DateTimeUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public storefront views: only ACTIVE, non-deleted catalog rows; no internal ids of staff or stock rows. */
public final class StorefrontResponses {
    private StorefrontResponses() {}

    public record CategoryOption(UUID id, UUID parentId, String name, String slug) {}
    public record BrandOption(UUID id, String name, String slug, String logoUrl) {}
    public record ColorOption(UUID id, String name, String code, String hexCode) {}
    public record SizeOption(UUID id, String name, String code, int sortOrder) {}
    public record StoreCatalog(List<CategoryOption> categories, List<BrandOption> brands, List<ColorOption> colors, List<SizeOption> sizes) {}

    public record StoreBanner(UUID id, String title, String imageUrl, String linkUrl, String position) {}

    public record Swatch(String name, String hexCode) {}
    public record ProductCard(UUID id, String slug, String name, String brandName, String categoryName, String gender,
            String imageUrl, String hoverImageUrl, BigDecimal minPrice, BigDecimal maxPrice, BigDecimal compareAtPrice,
            List<Swatch> colors, boolean inStock, Instant createdAt) {}

    public record NamedRef(UUID id, String name, String slug) {}
    public record ProductImage(String url, String altText) {}
    public record VariantOption(UUID id, String sku, UUID colorId, String colorName, String hexCode, UUID sizeId, String sizeName,
            int sizeSortOrder, BigDecimal price, BigDecimal compareAtPrice, int available) {}
    public record ReviewSnippet(UUID id, int rating, String comment, String customerName, List<String> images, Instant createdAt) {}
    public record ProductDetail(UUID id, String slug, String productCode, String name, String shortDescription, String description,
            String material, String gender, NamedRef brand, NamedRef category, List<ProductImage> images, List<VariantOption> variants,
            double ratingAverage, long ratingCount, List<ReviewSnippet> reviews, SizeChart sizeChart) {}

    /** Measurements per size, set by the shop for the product; null when there is none. */
    public record SizeChart(List<String> columns, List<List<String>> rows, String note) {}

    /** A customer's order with the payment and delivery records the customer may see. */
    public record CustomerOrder(OrderResponse order, List<PaymentResponse> payments, List<ShipmentResponse> shipments) {}

    public record Profile(UUID accountId, String email, String fullName, String phone, String gender,
            @JsonFormat(pattern = DateTimeUtils.DATE_PATTERN) LocalDate dateOfBirth) {}
    public record SavedAddress(UUID id, String recipientName, String phone, String addressLine, String ward, String district,
            String province, boolean isDefault) {}

    public record CollectionSummary(UUID id, String slug, String name, String description, String imageUrl, long productCount,
            Instant startAt, Instant endAt) {}
    public record CollectionDetail(CollectionSummary collection, PageResponse<ProductCard> products) {}

    /** Order tracking for someone holding the code and phone: no ids, no full address. */
    public record TrackedLine(String productName, String colorName, String sizeName, int quantity, BigDecimal totalAmount) {}
    public record TrackedShipment(String shippingProvider, String trackingCode, String status, Instant shippedAt, Instant deliveredAt) {}
    public record TrackedOrder(String orderCode, String orderStatus, String paymentStatus, String shippingStatus, Instant placedAt,
            Instant confirmedAt, Instant completedAt, Instant cancelledAt, String recipientName, String shippingArea,
            List<TrackedLine> items, BigDecimal subtotal, BigDecimal discountAmount, BigDecimal shippingFee, BigDecimal totalAmount,
            BigDecimal paidAmount, List<TrackedShipment> shipments, List<TrackedPayment> payments) {}
    public record TrackedPayment(String paymentMethod, BigDecimal amount, String status) {}

    /** A line of the signed-in customer's saved cart, with what the shop needs to show it. */
    public record CartLine(UUID variantId, UUID productId, String slug, String name, String imageUrl, String colorName,
            String sizeName, String sku, BigDecimal price, int quantity, long available, boolean sellable) {}

    /** Search box suggestions: matching products plus categories and brands. */
    public record Suggestions(List<ProductCard> products, List<CategoryOption> categories, List<BrandOption> brands) {}

    /** A size or color the customer may exchange an item for (same product, same price). */
    public record ExchangeOption(UUID variantId, String colorName, String sizeName, long available) {}

    public record ShippingQuote(String shippingMethodCode, BigDecimal shippingFee, boolean freeShipping, boolean live) {}

    /** Open back-in-stock requests per variant, for the shop. */
    public record StockAlertSummary(UUID productVariantId, UUID productId, String productName, String sku, String colorName,
            String sizeName, long waiting, long available, Instant latestAt) {}
}
