package lemonadex.project.clothes.features.order.dto;

import lemonadex.project.clothes.features.order.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class OrderResponses {
    private OrderResponses() {}
    public record ItemResponse(UUID id, UUID productVariantId, UUID inventoryId, String productName, String sku, String colorName,
            String sizeName, BigDecimal unitPrice, int quantity, BigDecimal discountAmount, BigDecimal totalAmount) {}
    public record OrderResponse(UUID id, String orderCode, UUID customerId, UUID warehouseId, OrderStatus orderStatus,
            OrderPaymentStatus paymentStatus, ShippingStatus shippingStatus, BigDecimal subtotal, BigDecimal discountAmount,
            BigDecimal shippingFee, BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal refundedAmount,
            String recipientName, String recipientPhone, String shippingAddress, String note, String shippingMethodCode, String contactEmail, List<ItemResponse> items,
            Instant placedAt, Instant confirmedAt, Instant completedAt, Instant cancelledAt, Instant createdAt, Instant updatedAt) {}
    public record OrderHistoryResponse(UUID id, UUID orderId, OrderStatus fromStatus, OrderStatus toStatus, String note, UUID changedBy, Instant createdAt) {}
    public record PaymentResponse(UUID id, UUID orderId, String paymentMethod, BigDecimal amount, PaymentStatus status, Instant paidAt, Instant createdAt) {}
    public record PaymentTransactionResponse(UUID id, UUID paymentId, String transactionCode, String provider, String providerTransactionId,
            BigDecimal amount, PaymentStatus status, Instant createdAt) {}
    public record PaymentMethodResponse(String code, String name, String provider) {}
    public record ShipmentResponse(UUID id, UUID orderId, String shippingProvider, String trackingCode, BigDecimal shippingFee,
            ShipmentStatus status, Instant shippedAt, Instant deliveredAt, Instant createdAt) {}
    public record ShipmentHistoryResponse(UUID id, UUID shipmentId, ShipmentStatus status, String description, Instant createdAt) {}
    public record ReturnItemResponse(UUID id, UUID orderItemId, int quantity, String reason, String conditionNote,
            ReturnType resolution, UUID replacementVariantId, UUID replacementInventoryId) {}
    public record ReturnResponse(UUID id, UUID orderId, UUID customerId, ReturnType requestType, String reason, ReturnStatus status,
            String note, List<ReturnItemResponse> items, List<String> images, BigDecimal refundableAmount,
            Instant requestedAt, Instant approvedAt, Instant rejectedAt, Instant completedAt) {}
    public record RefundResponse(UUID id, UUID returnRequestId, UUID paymentId, BigDecimal amount, String refundMethod,
            RefundStatus status, String reason, UUID processedBy, Instant processedAt, Instant createdAt) {}
}
