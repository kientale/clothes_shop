package lemonadex.project.clothes.features.settings.dto;

import lemonadex.project.clothes.features.settings.dto.SettingsRequests.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class SettingsResponses {
    private SettingsResponses() {}
    public record StoreSettingsResponse(long revision, Instant updatedAt, UUID updatedBy, StoreSettings configuration) {}
    public record PaymentSettingsResponse(long revision, Instant updatedAt, UUID updatedBy, PaymentSettings configuration) {}
    public record ShippingSettingsResponse(long revision, Instant updatedAt, UUID updatedBy, ShippingSettings configuration) {}
    public record OrderSettingsResponse(long revision, Instant updatedAt, UUID updatedBy, OrderSettings configuration) {}
    public record NotificationSettingsResponse(long revision, Instant updatedAt, UUID updatedBy, NotificationSettings configuration) {}
    public record GeneralSettingsResponse(long revision, Instant updatedAt, UUID updatedBy, GeneralSettings configuration) {}
    public record SettingsHistoryResponse(UUID id, String action, String entityType, UUID entityId,
            UUID accountId, String oldData, String newData, Instant createdAt) {}
    public record SettingsPaymentMethodResponse(UUID id, String code, String name, String provider, boolean enabled,
            PaymentMethodConfiguration configuration, long revision, Instant createdAt, Instant updatedAt) {}
    public record SettingsShippingMethodResponse(UUID id, String code, String name, String provider, BigDecimal baseFee,
            Integer estimatedDays, boolean enabled, long revision, Instant createdAt, Instant updatedAt) {}
    /** Bank details are public on purpose: customers need them to pay by transfer (and to build a VietQR code). */
    public record PublicPaymentOption(String code, String name, String provider, String kind, BankDetails bankDetails, String instructions) {}
    public record PublicShippingOption(String code, String name, String provider, BigDecimal baseFee, Integer estimatedDays) {}
    public record StoreConfigurationResponse(StoreSettings store, GeneralSettings general, boolean ordersEnabled,
            List<PublicPaymentOption> paymentMethods, List<PublicShippingOption> shippingMethods) {}
    public record ShippingQuoteResponse(String methodCode, String provider, BigDecimal shippingFee, boolean freeShipping) {}
}
