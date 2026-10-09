package lemonadex.project.clothes.features.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.features.order.model.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class OrderRequests {
    private OrderRequests() {}
    public record ItemRequest(@NotNull UUID productVariantId, @NotNull @Min(1) @Max(1000000) Integer quantity,
            @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal discountAmount) {}
    public record CreateOrderRequest(@NotNull UUID customerId, @NotNull UUID warehouseId,
            @NotBlank @Size(max = 150) String recipientName,
            @NotBlank @Pattern(regexp = "\\+?[0-9]{8,15}") String recipientPhone,
            @NotBlank @Size(max = 2000) String shippingAddress, @Size(max = 5000) String note,
            @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal discountAmount,
            @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal shippingFee,
            @NotEmpty @Size(max = 100) List<@Valid @NotNull ItemRequest> items,
            @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9_-]+") String couponCode, Boolean applyMarketing,
            @Pattern(regexp = "[A-Za-z0-9_-]{1,50}") String shippingMethodCode) {}
    public record UpdateOrderRequest(@NotBlank @Size(max = 150) String recipientName,
            @NotBlank @Pattern(regexp = "\\+?[0-9]{8,15}") String recipientPhone,
            @NotBlank @Size(max = 2000) String shippingAddress, @Size(max = 5000) String note) {}
    public record OrderStatusRequest(@NotNull OrderStatus status, @Size(max = 2000) String note) {}
    public record PaymentRequest(@NotNull UUID orderId, @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]+") String paymentMethod,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 16, fraction = 2) BigDecimal amount) {}
    public record PaymentStatusRequest(@NotNull PaymentStatus status,
            @Size(max = 255) String providerTransactionId) {}
    public record ShipmentRequest(@NotNull UUID orderId, @NotBlank @Size(max = 100) String shippingProvider,
            @Size(max = 150) String trackingCode,
            @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal shippingFee) {}
    public record ShipmentUpdateRequest(@NotBlank @Size(max = 100) String shippingProvider,
            @Size(max = 150) String trackingCode) {}
    public record ShipmentStatusRequest(@NotNull ShipmentStatus status, @Size(max = 2000) String description) {}
    public record ReturnItemRequest(@NotNull UUID orderItemId, @NotNull @Min(1) @Max(1000000) Integer quantity,
            @Size(max = 2000) String reason, @Size(max = 2000) String conditionNote, UUID replacementVariantId) {}
    public record CreateReturnRequest(@NotNull UUID orderId, @NotNull ReturnType requestType,
            @NotBlank @Size(max = 5000) String reason, @Size(max = 5000) String note,
            @NotEmpty @Size(max = 100) List<@Valid @NotNull ReturnItemRequest> items,
            @NotNull @Size(max = 10) List<@NotNull @Size(max = 2048) @Pattern(regexp = "https?://[^\\s]+") String> images) {}
    public record ReturnStatusRequest(@NotNull ReturnStatus status, @Size(max = 5000) String note) {}
    public record RefundRequest(@NotNull UUID returnRequestId, @NotNull UUID paymentId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 16, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 50) String refundMethod, @NotBlank @Size(max = 5000) String reason) {}
    public record RefundStatusRequest(@NotNull RefundStatus status) {}
}
