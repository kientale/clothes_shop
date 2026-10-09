package lemonadex.project.clothes.features.settings.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class SettingsRequests {
    private SettingsRequests() {}
    public record StoreSettings(@NotBlank @Size(max = 150) String storeName,
            @Size(max = 255) String legalName, @Size(max = 50) String taxCode,
            @Email @Size(max = 255) String supportEmail,
            @Pattern(regexp = "\\+?[0-9]{8,15}") String supportPhone,
            @Size(max = 2000) String address,
            @Size(max = 2048) @Pattern(regexp = "https?://[^\\s]+") String logoUrl,
            @Size(max = 2048) @Pattern(regexp = "https?://[^\\s]+") String websiteUrl,
            @Size(max = 255) String businessHours,
            @Pattern(regexp = "\\+?[0-9]{8,15}") String zaloPhone,
            @Size(max = 2048) @Pattern(regexp = "https://(m\\.me|www\\.messenger\\.com|messenger\\.com)/[^\\s]+") String messengerUrl) {}
    public record PaymentSettings(@NotNull Boolean paymentsEnabled, @NotNull Boolean allowPartialPayments,
            @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal minimumPaymentAmount) {}
    public record ShippingSettings(@NotNull Boolean shippingEnabled, @NotNull Boolean useConfiguredFees,
            @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal defaultBaseFee,
            @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal freeShippingThreshold) {}
    public record OrderSettings(@NotNull Boolean ordersEnabled, @NotNull Boolean autoConfirm,
            @NotBlank @Pattern(regexp = "[A-Z0-9]{1,12}") String orderCodePrefix,
            @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal minimumOrderAmount,
            @NotNull @Min(1) @Max(100) Integer maxItems,
            @NotNull @Min(1) @Max(1000000) Integer maxQuantityPerItem,
            @NotNull Boolean enableMarketingByDefault, @NotNull Boolean allowCancellation,
            @NotNull Boolean allowReturns, @Min(1) @Max(3650) Integer returnWindowDays) {}
    public record NotificationSettings(@NotNull Boolean inAppEnabled, @NotNull Boolean allowBroadcast,
            @NotNull @Min(1) @Max(1000000) Integer maxRecipients) {}
    public record GeneralSettings(@NotBlank @Size(max = 100) String timezone,
            @NotBlank @Pattern(regexp = "VI|EN") String language, @NotNull Boolean maintenanceMode,
            @Size(max = 2000) String maintenanceMessage) {}
    public record UpdateStoreSettings(@NotNull @Min(0) Long expectedRevision, @NotNull @Valid StoreSettings configuration) {}
    public record UpdatePaymentSettings(@NotNull @Min(0) Long expectedRevision, @NotNull @Valid PaymentSettings configuration) {}
    public record UpdateShippingSettings(@NotNull @Min(0) Long expectedRevision, @NotNull @Valid ShippingSettings configuration) {}
    public record UpdateOrderSettings(@NotNull @Min(0) Long expectedRevision, @NotNull @Valid OrderSettings configuration) {}
    public record UpdateNotificationSettings(@NotNull @Min(0) Long expectedRevision, @NotNull @Valid NotificationSettings configuration) {}
    public record UpdateGeneralSettings(@NotNull @Min(0) Long expectedRevision, @NotNull @Valid GeneralSettings configuration) {}
    public record BankDetails(@NotBlank @Size(max = 150) String bankName,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,50}") String accountNumber,
            @NotBlank @Size(max = 150) String accountHolder) {}
    /** COD and BANK_TRANSFER are settled by staff; VNPAY and MOMO are paid online through the gateway. */
    public record PaymentMethodConfiguration(@NotBlank @Pattern(regexp = "COD|BANK_TRANSFER|VNPAY|MOMO") String kind,
            @Valid BankDetails bankDetails, @Size(max = 2000) String instructions) {}
    public record CreatePaymentMethod(@NotBlank @Pattern(regexp = "[A-Z0-9_-]{1,50}") String code,
            @NotBlank @Size(max = 100) String name, @NotNull Boolean enabled,
            @NotNull @Valid PaymentMethodConfiguration configuration) {}
    public record UpdatePaymentMethod(@NotNull @Min(0) Long expectedRevision,
            @NotBlank @Size(max = 100) String name, @NotNull Boolean enabled,
            @NotNull @Valid PaymentMethodConfiguration configuration) {}
    public record CreateShippingMethod(@NotBlank @Pattern(regexp = "[A-Z0-9_-]{1,50}") String code,
            @NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 100) String provider,
            @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal baseFee,
            @Min(0) @Max(365) Integer estimatedDays, @NotNull Boolean enabled) {}
    public record UpdateShippingMethod(@NotNull @Min(0) Long expectedRevision,
            @NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 100) String provider,
            @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal baseFee,
            @Min(0) @Max(365) Integer estimatedDays, @NotNull Boolean enabled) {}
}
