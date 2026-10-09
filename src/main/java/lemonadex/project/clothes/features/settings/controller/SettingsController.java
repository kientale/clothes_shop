package lemonadex.project.clothes.features.settings.controller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.common.dto.*;
import lemonadex.project.clothes.features.settings.dto.SettingsRequests.*;
import lemonadex.project.clothes.features.settings.dto.SettingsResponses.*;
import lemonadex.project.clothes.features.settings.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.math.BigDecimal;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin/settings")
public class SettingsController {
    private final SettingsService service;
    @GetMapping("/store") @PreAuthorize("hasAuthority('SETTINGS_STORE_READ')")
    ResponseEntity<ApiResponse<StoreSettingsResponse>> getStore() { return ok(service.getStore()); }
    @PutMapping("/store") @PreAuthorize("hasAuthority('SETTINGS_STORE_WRITE')")
    ResponseEntity<ApiResponse<StoreSettingsResponse>> updateStore(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateStoreSettings request) { return ok(service.updateStore(request, UUID.fromString(jwt.getSubject()))); }
    @GetMapping("/store/history") @PreAuthorize("hasAuthority('SETTINGS_STORE_READ')")
    ResponseEntity<ApiResponse<PageResponse<SettingsHistoryResponse>>> storeHistory(@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.history("STORE", page, size)); }
    @GetMapping("/payment") @PreAuthorize("hasAuthority('SETTINGS_PAYMENT_READ')")
    ResponseEntity<ApiResponse<PaymentSettingsResponse>> getPayment() { return ok(service.getPayment()); }
    @PutMapping("/payment") @PreAuthorize("hasAuthority('SETTINGS_PAYMENT_WRITE')")
    ResponseEntity<ApiResponse<PaymentSettingsResponse>> updatePayment(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdatePaymentSettings request) { return ok(service.updatePayment(request, UUID.fromString(jwt.getSubject()))); }
    @GetMapping("/payment/history") @PreAuthorize("hasAuthority('SETTINGS_PAYMENT_READ')")
    ResponseEntity<ApiResponse<PageResponse<SettingsHistoryResponse>>> paymentHistory(@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.history("PAYMENT", page, size)); }
    @GetMapping("/shipping") @PreAuthorize("hasAuthority('SETTINGS_SHIPPING_READ')")
    ResponseEntity<ApiResponse<ShippingSettingsResponse>> getShipping() { return ok(service.getShipping()); }
    @PutMapping("/shipping") @PreAuthorize("hasAuthority('SETTINGS_SHIPPING_WRITE')")
    ResponseEntity<ApiResponse<ShippingSettingsResponse>> updateShipping(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateShippingSettings request) { return ok(service.updateShipping(request, UUID.fromString(jwt.getSubject()))); }
    @GetMapping("/shipping/history") @PreAuthorize("hasAuthority('SETTINGS_SHIPPING_READ')")
    ResponseEntity<ApiResponse<PageResponse<SettingsHistoryResponse>>> shippingHistory(@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.history("SHIPPING", page, size)); }
    @GetMapping("/order") @PreAuthorize("hasAuthority('SETTINGS_ORDER_READ')")
    ResponseEntity<ApiResponse<OrderSettingsResponse>> getOrder() { return ok(service.getOrder()); }
    @PutMapping("/order") @PreAuthorize("hasAuthority('SETTINGS_ORDER_WRITE')")
    ResponseEntity<ApiResponse<OrderSettingsResponse>> updateOrder(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateOrderSettings request) { return ok(service.updateOrder(request, UUID.fromString(jwt.getSubject()))); }
    @GetMapping("/order/history") @PreAuthorize("hasAuthority('SETTINGS_ORDER_READ')")
    ResponseEntity<ApiResponse<PageResponse<SettingsHistoryResponse>>> orderHistory(@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.history("ORDER", page, size)); }
    @GetMapping("/notification") @PreAuthorize("hasAuthority('SETTINGS_NOTIFICATION_READ')")
    ResponseEntity<ApiResponse<NotificationSettingsResponse>> getNotification() { return ok(service.getNotification()); }
    @PutMapping("/notification") @PreAuthorize("hasAuthority('SETTINGS_NOTIFICATION_WRITE')")
    ResponseEntity<ApiResponse<NotificationSettingsResponse>> updateNotification(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateNotificationSettings request) { return ok(service.updateNotification(request, UUID.fromString(jwt.getSubject()))); }
    @GetMapping("/notification/history") @PreAuthorize("hasAuthority('SETTINGS_NOTIFICATION_READ')")
    ResponseEntity<ApiResponse<PageResponse<SettingsHistoryResponse>>> notificationHistory(@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.history("NOTIFICATION", page, size)); }
    @GetMapping("/general") @PreAuthorize("hasAuthority('SETTINGS_GENERAL_READ')")
    ResponseEntity<ApiResponse<GeneralSettingsResponse>> getGeneral() { return ok(service.getGeneral()); }
    @PutMapping("/general") @PreAuthorize("hasAuthority('SETTINGS_GENERAL_WRITE')")
    ResponseEntity<ApiResponse<GeneralSettingsResponse>> updateGeneral(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateGeneralSettings request) { return ok(service.updateGeneral(request, UUID.fromString(jwt.getSubject()))); }
    @GetMapping("/general/history") @PreAuthorize("hasAuthority('SETTINGS_GENERAL_READ')")
    ResponseEntity<ApiResponse<PageResponse<SettingsHistoryResponse>>> generalHistory(@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.history("GENERAL", page, size)); }
    @GetMapping("/payment-methods") @PreAuthorize("hasAuthority('SETTINGS_PAYMENT_READ')")
    ResponseEntity<ApiResponse<List<SettingsPaymentMethodResponse>>> paymentMethods() { return ok(service.paymentMethods()); }
    @GetMapping("/payment-methods/{id}") @PreAuthorize("hasAuthority('SETTINGS_PAYMENT_READ')")
    ResponseEntity<ApiResponse<SettingsPaymentMethodResponse>> paymentMethod(@PathVariable UUID id) { return ok(service.paymentMethod(id)); }
    @PostMapping("/payment-methods") @PreAuthorize("hasAuthority('SETTINGS_PAYMENT_WRITE')")
    ResponseEntity<ApiResponse<SettingsPaymentMethodResponse>> createPaymentMethod(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePaymentMethod request) { return created(service.createPaymentMethod(request, UUID.fromString(jwt.getSubject()))); }
    @PutMapping("/payment-methods/{id}") @PreAuthorize("hasAuthority('SETTINGS_PAYMENT_WRITE')")
    ResponseEntity<ApiResponse<SettingsPaymentMethodResponse>> updatePaymentMethod(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody UpdatePaymentMethod request) { return ok(service.updatePaymentMethod(id, request, UUID.fromString(jwt.getSubject()))); }
    @DeleteMapping("/payment-methods/{id}") @PreAuthorize("hasAuthority('SETTINGS_PAYMENT_WRITE')")
    ResponseEntity<ApiResponse<Void>> deletePaymentMethod(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam @Min(0) long expectedRevision) { service.deletePaymentMethod(id, expectedRevision, UUID.fromString(jwt.getSubject())); return ok(null); }
    @GetMapping("/shipping-methods") @PreAuthorize("hasAuthority('SETTINGS_SHIPPING_READ')")
    ResponseEntity<ApiResponse<List<SettingsShippingMethodResponse>>> shippingMethods() { return ok(service.shippingMethods()); }
    @GetMapping("/shipping-methods/{id}") @PreAuthorize("hasAuthority('SETTINGS_SHIPPING_READ')")
    ResponseEntity<ApiResponse<SettingsShippingMethodResponse>> shippingMethod(@PathVariable UUID id) { return ok(service.shippingMethod(id)); }
    @PostMapping("/shipping-methods") @PreAuthorize("hasAuthority('SETTINGS_SHIPPING_WRITE')")
    ResponseEntity<ApiResponse<SettingsShippingMethodResponse>> createShippingMethod(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateShippingMethod request) { return created(service.createShippingMethod(request, UUID.fromString(jwt.getSubject()))); }
    @PutMapping("/shipping-methods/{id}") @PreAuthorize("hasAuthority('SETTINGS_SHIPPING_WRITE')")
    ResponseEntity<ApiResponse<SettingsShippingMethodResponse>> updateShippingMethod(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody UpdateShippingMethod request) { return ok(service.updateShippingMethod(id, request, UUID.fromString(jwt.getSubject()))); }
    @DeleteMapping("/shipping-methods/{id}") @PreAuthorize("hasAuthority('SETTINGS_SHIPPING_WRITE')")
    ResponseEntity<ApiResponse<Void>> deleteShippingMethod(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam @Min(0) long expectedRevision) { service.deleteShippingMethod(id, expectedRevision, UUID.fromString(jwt.getSubject())); return ok(null); }
    @GetMapping("/shipping/quote") @PreAuthorize("hasAuthority('SETTINGS_SHIPPING_READ')")
    ResponseEntity<ApiResponse<ShippingQuoteResponse>> shippingQuote(@RequestParam(required = false) @Pattern(regexp = "[A-Za-z0-9_-]{1,50}") String methodCode,
            @RequestParam @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal merchandiseAmount) { return ok(service.quote(methodCode, merchandiseAmount)); }
    private <T> ResponseEntity<ApiResponse<T>> ok(T value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("SUCCESS", "Success", value)); }
    private <T> ResponseEntity<ApiResponse<T>> created(T value) { return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success("CREATED", "Created", value)); }
}
