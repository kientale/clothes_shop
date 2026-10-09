package lemonadex.project.clothes.features.settings.service;

import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.common.exception.*;
import lemonadex.project.clothes.features.settings.dto.SettingsRequests.*;
import lemonadex.project.clothes.features.settings.dto.SettingsResponses.*;
import lemonadex.project.clothes.features.settings.repository.SettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class SettingsService {
    private final SettingsRepository repository;
    private final ObjectMapper json;
    private final Clock clock;

    public StoreSettings store() { return configuration("STORE", StoreSettings.class); }
    public PaymentSettings payment() { return configuration("PAYMENT", PaymentSettings.class); }
    public ShippingSettings shipping() { return configuration("SHIPPING", ShippingSettings.class); }
    public OrderSettings order() { return configuration("ORDER", OrderSettings.class); }
    public NotificationSettings notification() { return configuration("NOTIFICATION", NotificationSettings.class); }
    public GeneralSettings general() { return configuration("GENERAL", GeneralSettings.class); }
    private <T> T configuration(String group, Class<T> type) { return json.readValue((String) repository.setting(group).get("setting_value"), type); }
    public StoreSettingsResponse getStore() { var r = repository.setting("STORE"); return new StoreSettingsResponse(revision(r), instant(r, "updated_at"), uuid(r, "updated_by"), read(r, StoreSettings.class)); }
    public PaymentSettingsResponse getPayment() { var r = repository.setting("PAYMENT"); return new PaymentSettingsResponse(revision(r), instant(r, "updated_at"), uuid(r, "updated_by"), read(r, PaymentSettings.class)); }
    public ShippingSettingsResponse getShipping() { var r = repository.setting("SHIPPING"); return new ShippingSettingsResponse(revision(r), instant(r, "updated_at"), uuid(r, "updated_by"), read(r, ShippingSettings.class)); }
    public OrderSettingsResponse getOrder() { var r = repository.setting("ORDER"); return new OrderSettingsResponse(revision(r), instant(r, "updated_at"), uuid(r, "updated_by"), read(r, OrderSettings.class)); }
    public NotificationSettingsResponse getNotification() { var r = repository.setting("NOTIFICATION"); return new NotificationSettingsResponse(revision(r), instant(r, "updated_at"), uuid(r, "updated_by"), read(r, NotificationSettings.class)); }
    public GeneralSettingsResponse getGeneral() { var r = repository.setting("GENERAL"); return new GeneralSettingsResponse(revision(r), instant(r, "updated_at"), uuid(r, "updated_by"), read(r, GeneralSettings.class)); }
    private <T> T read(Map<String, Object> row, Class<T> type) { return json.readValue((String) row.get("setting_value"), type); }

    @Transactional public StoreSettingsResponse updateStore(UpdateStoreSettings r, UUID actor) { update("STORE", r.expectedRevision(), r.configuration(), actor); return getStore(); }
    @Transactional public PaymentSettingsResponse updatePayment(UpdatePaymentSettings r, UUID actor) { update("PAYMENT", r.expectedRevision(), r.configuration(), actor); return getPayment(); }
    @Transactional public ShippingSettingsResponse updateShipping(UpdateShippingSettings r, UUID actor) { update("SHIPPING", r.expectedRevision(), r.configuration(), actor); return getShipping(); }
    @Transactional public OrderSettingsResponse updateOrder(UpdateOrderSettings r, UUID actor) { update("ORDER", r.expectedRevision(), r.configuration(), actor); return getOrder(); }
    @Transactional public NotificationSettingsResponse updateNotification(UpdateNotificationSettings r, UUID actor) { update("NOTIFICATION", r.expectedRevision(), r.configuration(), actor); return getNotification(); }
    @Transactional public GeneralSettingsResponse updateGeneral(UpdateGeneralSettings r, UUID actor) {
        var c = r.configuration();
        if (!ZoneId.getAvailableZoneIds().contains(c.timezone()) || !repository.timezoneExists(c.timezone()))
            throw new BadRequestException("INVALID_TIMEZONE", "Use a supported IANA timezone");
        if (c.maintenanceMode() && (c.maintenanceMessage() == null || c.maintenanceMessage().isBlank()))
            throw new BadRequestException("MAINTENANCE_MESSAGE_REQUIRED", "Provide a message when maintenance is enabled");
        update("GENERAL", r.expectedRevision(), c, actor); return getGeneral();
    }
    private void update(String group, long expected, Object value, UUID actor) {
        repository.lock(); var before = repository.setting(group); checkRevision(before, expected);
        var after = repository.updateSetting(group, json.writeValueAsString(value), expected, actor);
        repository.audit("SETTINGS_UPDATED", "SETTINGS_" + group, uuid(before, "id"), actor, settingAudit(before), settingAudit(after));
    }
    private String settingAudit(Map<String, Object> r) {
        return json.writeValueAsString(Map.of("revision", revision(r), "configuration", json.readTree((String) r.get("setting_value"))));
    }
    public PageResponse<SettingsHistoryResponse> history(String group, int page, int size) {
        String type = "SETTINGS_" + group; long total = repository.historyCount(type);
        var rows = repository.history(type, page, size).stream().map(r -> new SettingsHistoryResponse(uuid(r, "id"), (String) r.get("action"),
                (String) r.get("entity_type"), uuid(r, "entity_id"), uuid(r, "account_id"), string(r, "old_data"), string(r, "new_data"), instant(r, "created_at"))).toList();
        return new PageResponse<>(rows, page, size, total, (int) ((total + size - 1) / size));
    }
    public List<SettingsPaymentMethodResponse> paymentMethods() { return repository.methods(true).stream().map(this::paymentMethod).toList(); }
    public SettingsPaymentMethodResponse paymentMethod(UUID id) { return paymentMethod(required(true, id)); }
    public List<SettingsShippingMethodResponse> shippingMethods() { return repository.methods(false).stream().map(this::shippingMethod).toList(); }
    public SettingsShippingMethodResponse shippingMethod(UUID id) { return shippingMethod(required(false, id)); }

    @Transactional public SettingsPaymentMethodResponse createPaymentMethod(CreatePaymentMethod r, UUID actor) {
        repository.lock(); validatePayment(r.configuration()); uniqueCode(true, r.code());
        var p = paymentParameters(r.name(), r.enabled(), r.configuration()); p.put("code", r.code());
        var after = repository.createPayment(p);
        auditMethod(true, "CREATED", null, after, actor); return paymentMethod(after);
    }
    @Transactional public SettingsPaymentMethodResponse updatePaymentMethod(UUID id, UpdatePaymentMethod r, UUID actor) {
        repository.lock(); var before = required(true, id); checkRevision(before, r.expectedRevision()); validatePayment(r.configuration());
        var p = paymentParameters(r.name(), r.enabled(), r.configuration()); p.put("id", id); p.put("revision", r.expectedRevision());
        var after = repository.updatePayment(p); auditMethod(true, "UPDATED", before, after, actor); return paymentMethod(after);
    }
    @Transactional public SettingsShippingMethodResponse createShippingMethod(CreateShippingMethod r, UUID actor) {
        repository.lock(); uniqueCode(false, r.code());
        var p = shippingParameters(r.name(), r.provider(), r.baseFee(), r.estimatedDays(), r.enabled()); p.put("code", r.code());
        var after = repository.createShipping(p); auditMethod(false, "CREATED", null, after, actor); return shippingMethod(after);
    }
    @Transactional public SettingsShippingMethodResponse updateShippingMethod(UUID id, UpdateShippingMethod r, UUID actor) {
        repository.lock(); var before = required(false, id); checkRevision(before, r.expectedRevision());
        var p = shippingParameters(r.name(), r.provider(), r.baseFee(), r.estimatedDays(), r.enabled()); p.put("id", id); p.put("revision", r.expectedRevision());
        var after = repository.updateShipping(p); auditMethod(false, "UPDATED", before, after, actor); return shippingMethod(after);
    }
    @Transactional public void deletePaymentMethod(UUID id, long revision, UUID actor) { archive(true, id, revision, actor); }
    @Transactional public void deleteShippingMethod(UUID id, long revision, UUID actor) { archive(false, id, revision, actor); }
    private void archive(boolean payment, UUID id, long revision, UUID actor) {
        repository.lock(); var before = required(payment, id); checkRevision(before, revision);
        var after = repository.archive(payment, id); auditMethod(payment, "ARCHIVED", before, after, actor);
    }
    private void auditMethod(boolean payment, String action, Map<String, Object> before, Map<String, Object> after, UUID actor) {
        repository.audit("METHOD_" + action, payment ? "SETTINGS_PAYMENT" : "SETTINGS_SHIPPING", uuid(after, "id"), actor,
                before == null ? null : methodAudit(payment, before), methodAudit(payment, after));
    }
    private String methodAudit(boolean payment, Map<String, Object> row) {
        return json.writeValueAsString(Map.of("method", payment ? paymentMethod(row) : shippingMethod(row), "deleted", row.get("deleted")));
    }
    private void uniqueCode(boolean payment, String code) { if (repository.codeExists(payment, code)) throw new ConflictException("METHOD_CODE_EXISTS", "The code is already used, including archived methods"); }
    private void validatePayment(PaymentMethodConfiguration c) {
        if ("BANK_TRANSFER".equals(c.kind()) && c.bankDetails() == null) throw new BadRequestException("BANK_DETAILS_REQUIRED", "Bank transfers require bank details");
        if (!"BANK_TRANSFER".equals(c.kind()) && c.bankDetails() != null) throw new BadRequestException("INVALID_BANK_DETAILS", "Only bank transfers accept bank details");
    }
    private Map<String, Object> paymentParameters(String name, boolean enabled, PaymentMethodConfiguration c) {
        var p = new HashMap<String, Object>(); p.put("name", name.strip()); p.put("enabled", enabled); p.put("configuration", json.writeValueAsString(c)); return p;
    }
    private Map<String, Object> shippingParameters(String name, String provider, BigDecimal fee, Integer days, boolean enabled) {
        var p = new HashMap<String, Object>(); p.put("name", name.strip()); p.put("provider", provider.strip()); p.put("fee", fee); p.put("days", days); p.put("enabled", enabled); return p;
    }
    private Map<String, Object> required(boolean payment, UUID id) { return repository.method(payment, id).orElseThrow(() -> new ResourceNotFoundException("Method")); }
    private SettingsPaymentMethodResponse paymentMethod(Map<String, Object> r) {
        return new SettingsPaymentMethodResponse(uuid(r, "id"), (String) r.get("code"), (String) r.get("name"), (String) r.get("provider"),
                (boolean) r.get("is_enabled"), json.readValue(string(r, "configuration"), PaymentMethodConfiguration.class), revision(r), instant(r, "created_at"), instant(r, "updated_at"));
    }
    private SettingsShippingMethodResponse shippingMethod(Map<String, Object> r) {
        return new SettingsShippingMethodResponse(uuid(r, "id"), (String) r.get("code"), (String) r.get("name"), (String) r.get("provider"), (BigDecimal) r.get("base_fee"),
                (Integer) r.get("estimated_days"), (boolean) r.get("is_enabled"), revision(r), instant(r, "created_at"), instant(r, "updated_at"));
    }
    private PublicPaymentOption publicPayment(Map<String, Object> r) {
        var c = json.readValue(string(r, "configuration"), PaymentMethodConfiguration.class);
        return new PublicPaymentOption((String) r.get("code"), (String) r.get("name"), (String) r.get("provider"),
                c == null ? null : c.kind(), c == null ? null : c.bankDetails(), c == null ? null : c.instructions());
    }
    public StoreConfigurationResponse publicConfiguration() {
        var pay = payment(); var ship = shipping(); var general = general();
        return new StoreConfigurationResponse(store(), general, order().ordersEnabled() && !general.maintenanceMode(),
                pay.paymentsEnabled() ? repository.methods(true).stream().filter(r -> (boolean) r.get("is_enabled"))
                        .map(this::publicPayment).toList() : List.of(),
                ship.shippingEnabled() ? shippingMethods().stream().filter(SettingsShippingMethodResponse::enabled).map(m -> new PublicShippingOption(m.code(), m.name(), m.provider(), m.baseFee(), m.estimatedDays())).toList() : List.of());
    }
    public ShippingQuoteResponse quote(String code, BigDecimal merchandiseAmount) {
        return quote(code, merchandiseAmount, null);
    }
    /** Carrier behind an enabled shipping method (for example GHTK), empty for unknown or disabled methods. */
    public Optional<String> shippingProvider(String code) {
        return code == null ? Optional.empty() : repository.shippingMethod(code.toUpperCase(Locale.ROOT)).map(m -> (String) m.get("provider"));
    }
    /** @param carrierFee live carrier price for the address, used instead of the method's base fee; null when there is none */
    public ShippingQuoteResponse quote(String code, BigDecimal merchandiseAmount, BigDecimal carrierFee) {
        var settings = shipping();
        if (!settings.shippingEnabled()) throw new ConflictException("SHIPPING_DISABLED", "New shipments are disabled");
        var method = code == null ? null : repository.shippingMethod(code.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BadRequestException("SHIPPING_METHOD_UNAVAILABLE", "The shipping method is not enabled"));
        boolean free = settings.freeShippingThreshold() != null && merchandiseAmount.compareTo(settings.freeShippingThreshold()) >= 0;
        BigDecimal fee = free ? BigDecimal.ZERO : method == null ? settings.defaultBaseFee() : carrierFee != null ? carrierFee : (BigDecimal) method.get("base_fee");
        return new ShippingQuoteResponse(method == null ? null : (String) method.get("code"), method == null ? null : (String) method.get("provider"), fee, free);
    }
    public void requireReturns(UUID orderId) {
        var c = order();
        if (!c.allowReturns()) throw new ConflictException("RETURNS_DISABLED", "New return requests are disabled");
        if (c.returnWindowDays() != null) {
            Instant delivered = repository.deliveredAt(orderId);
            if (delivered == null || clock.instant().isAfter(delivered.plus(Duration.ofDays(c.returnWindowDays()))))
                throw new ConflictException("RETURN_WINDOW_EXPIRED", "The return window has expired");
        }
    }
    private void checkRevision(Map<String, Object> row, long expected) { if (revision(row) != expected) throw new ConflictException("SETTINGS_REVISION_CONFLICT", "Settings changed; reload before saving"); }
    private long revision(Map<String, Object> r) { return ((Number) r.get("revision")).longValue(); }
    private UUID uuid(Map<String, Object> r, String key) { return (UUID) r.get(key); }
    private String string(Map<String, Object> r, String key) { return r.get(key) == null ? null : r.get(key).toString(); }
    private Instant instant(Map<String, Object> r, String key) { return ((java.sql.Timestamp) r.get(key)).toInstant(); }
}
