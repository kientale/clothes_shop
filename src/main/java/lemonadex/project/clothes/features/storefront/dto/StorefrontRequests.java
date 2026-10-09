package lemonadex.project.clothes.features.storefront.dto;

import com.fasterxml.jackson.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.common.util.DateTimeUtils;
import lemonadex.project.clothes.features.carrier.dto.CarrierDtos.ShippingArea;
import lemonadex.project.clothes.features.order.model.ReturnType;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class StorefrontRequests {
    private StorefrontRequests() {}

    public record CheckoutItem(@NotNull UUID productVariantId, @NotNull @Min(1) @Max(1000) Integer quantity) {}

    /**
     * Checkout by the signed-in customer. The customer comes from the JWT, the warehouse is chosen by the
     * server, and prices, discounts and shipping fees are always computed by the backend.
     */
    public record CheckoutRequest(
            @NotBlank @Size(max = 150) String recipientName,
            @NotBlank @Pattern(regexp = "^\\+?[0-9]{8,15}$") String recipientPhone,
            @NotBlank @Size(max = 500) String shippingAddress,
            @Size(max = 1000) String note,
            @NotEmpty @Size(max = 100) List<@Valid @NotNull CheckoutItem> items,
            @Size(max = 50) String shippingMethodCode,
            @Size(max = 50) String paymentMethod,
            @Size(max = 80) String couponCode,
            @Email @Size(max = 254) String email,
            @Valid ShippingArea area) {}

    public record ProfileRequest(
            @NotBlank @Size(max = 150) String fullName,
            @Pattern(regexp = "^\\+?[0-9]{8,15}$") String phone,
            @Pattern(regexp = "MALE|FEMALE|OTHER") String gender,
            @Past @JsonFormat(pattern = DateTimeUtils.DATE_PATTERN) LocalDate dateOfBirth) {}

    public record PasswordChangeRequest(
            @NotBlank @Size(max = 72) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String currentPassword,
            @NotBlank @Size(min = 8, max = 72) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String newPassword,
            @NotBlank @Size(min = 8, max = 72) @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String confirmPassword) {
        @AssertTrue(message = "Passwords must match")
        @JsonIgnore
        public boolean isPasswordConfirmed() {
            return Objects.equals(newPassword, confirmPassword);
        }
    }

    /** A saved delivery address. Since 2025 most addresses have no district, so it is optional. */
    public record AddressRequest(
            @NotBlank @Size(max = 150) String recipientName,
            @NotBlank @Pattern(regexp = "^\\+?[0-9]{8,15}$") String phone,
            @NotBlank @Size(max = 500) String addressLine,
            @NotBlank @Size(max = 100) String ward,
            @Size(max = 100) String district,
            @NotBlank @Size(max = 100) String province,
            boolean makeDefault) {}

    /** Public order tracking: both the code and the phone on the order must match. */
    public record OrderLookupRequest(
            @NotBlank @Size(max = 80) String orderCode,
            @NotBlank @Pattern(regexp = "^\\+?[0-9 .]{8,20}$") String phone) {}

    /** Pay an order online (VNPAY or MOMO). */
    public record PayRequest(@NotBlank @Pattern(regexp = "VNPAY|MOMO") String method) {}

    /** A guest pays an order online; like tracking, the order code and the phone on the order must match. */
    public record GuestPayRequest(
            @NotBlank @Size(max = 80) String orderCode,
            @NotBlank @Pattern(regexp = "^\\+?[0-9 .]{8,20}$") String phone,
            @NotBlank @Pattern(regexp = "VNPAY|MOMO") String method) {}

    /** The whole cart; it replaces what the server holds. */
    public record CartRequest(@NotNull @Size(max = 100) List<@Valid @NotNull CheckoutItem> items) {}

    /** "Email me when this size is back". */
    public record StockAlertRequest(@NotNull UUID productVariantId, @NotBlank @Email @Size(max = 254) String email) {}

    /** Shipping fee estimate before placing the order. */
    public record ShippingQuoteRequest(@NotBlank @Size(max = 50) String shippingMethodCode, @Valid ShippingArea area,
            @NotEmpty @Size(max = 100) List<@Valid @NotNull CheckoutItem> items) {}

    public record CustomerReturnItem(@NotNull UUID orderItemId, @NotNull @Min(1) @Max(1000) Integer quantity,
            @Size(max = 2000) String reason, UUID replacementVariantId) {}

    /** A return or exchange the customer asks for on one of their delivered orders. */
    public record CustomerReturnRequest(@NotNull UUID orderId, @NotNull ReturnType requestType,
            @NotBlank @Size(max = 5000) String reason,
            @NotEmpty @Size(max = 100) List<@Valid @NotNull CustomerReturnItem> items,
            @NotNull @Size(max = 6) List<@NotNull @Size(max = 2048) @Pattern(regexp = "https?://[^\\s]+") String> images) {}
}
