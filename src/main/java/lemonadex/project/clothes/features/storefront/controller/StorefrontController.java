package lemonadex.project.clothes.features.storefront.controller;

import jakarta.validation.constraints.*;
import lemonadex.project.clothes.common.dto.ApiResponse;
import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.features.storefront.dto.StorefrontResponses.*;
import lemonadex.project.clothes.features.storefront.model.StorefrontRows.ProductQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lemonadex.project.clothes.features.payment.dto.PaymentGatewayResponses.PaymentRedirect;
import lemonadex.project.clothes.features.storefront.dto.StorefrontRequests.*;
import lemonadex.project.clothes.features.storefront.service.CustomerOrderService;
import lemonadex.project.clothes.features.storefront.service.StockAlertService;
import lemonadex.project.clothes.features.storefront.service.StorefrontService;
import org.springframework.http.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Public, read-only catalog for the shop. No authentication. */
@RestController @RequiredArgsConstructor @Validated @RequestMapping("/api/v1/store")
public class StorefrontController {
    private final StorefrontService service;
    private final CustomerOrderService orders;
    private final StockAlertService alerts;

    @GetMapping("/catalog")
    ResponseEntity<ApiResponse<StoreCatalog>> catalog() {
        return ok(service.catalog());
    }

    @GetMapping("/banners")
    ResponseEntity<ApiResponse<java.util.List<StoreBanner>>> banners(@RequestParam(required = false) @Size(max = 50) String position) {
        return ok(service.banners(position));
    }

    @GetMapping("/products")
    ResponseEntity<ApiResponse<PageResponse<ProductCard>>> products(
            @RequestParam(required = false) @Size(max = 254) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID brandId,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) UUID colorId,
            @RequestParam(required = false) UUID sizeId,
            @RequestParam(required = false) @PositiveOrZero BigDecimal minPrice,
            @RequestParam(required = false) @PositiveOrZero BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean inStock,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(60) int size) {
        return ok(service.products(new ProductQuery(search, categoryId, brandId, gender, colorId, sizeId, minPrice, maxPrice, inStock, sort, page, size)));
    }

    @GetMapping("/products/{key}")
    ResponseEntity<ApiResponse<ProductDetail>> product(@PathVariable @Size(max = 300) String key) {
        return ok(service.product(key));
    }

    @GetMapping("/collections")
    ResponseEntity<ApiResponse<java.util.List<CollectionSummary>>> collections() {
        return ok(service.collections());
    }

    @GetMapping("/collections/{slug}")
    ResponseEntity<ApiResponse<CollectionDetail>> collection(@PathVariable @Size(max = 200) String slug,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(60) int size) {
        return ok(service.collection(slug, page, size));
    }

    /** Order tracking for guests: needs the order code and the phone on the order. Rate limited per client. */
    @PostMapping("/orders/lookup")
    ResponseEntity<ApiResponse<TrackedOrder>> track(@Valid @RequestBody OrderLookupRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("SUCCESS", "Success", orders.track(request)));
    }

    /** Checkout without an account; the answer is the tracking view of the new order. Rate limited per client. */
    @PostMapping("/orders")
    ResponseEntity<ApiResponse<TrackedOrder>> guestCheckout(@Valid @RequestBody CheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("CREATE_ORDER_SUCCESS", "Created", orders.guestCheckout(request)));
    }

    /** A guest pays online: order code and phone, as on the tracking page. */
    @PostMapping("/orders/pay")
    ResponseEntity<ApiResponse<PaymentRedirect>> guestPay(@Valid @RequestBody GuestPayRequest request, HttpServletRequest http) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("SUCCESS", "Redirect to the payment gateway", orders.guestPay(request, http.getRemoteAddr())));
    }

    @PostMapping("/shipping/quote")
    ResponseEntity<ApiResponse<ShippingQuote>> shippingQuote(@Valid @RequestBody ShippingQuoteRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("SUCCESS", "Shipping quote", orders.quote(request)));
    }

    @GetMapping("/search/suggest")
    ResponseEntity<ApiResponse<Suggestions>> suggest(@RequestParam(defaultValue = "") @Size(max = 100) String q) {
        return ok(service.suggest(q));
    }

    /** "Email me when it is back" for a sold-out size; a signed-in customer is linked to the request. */
    @PostMapping("/stock-alerts")
    ResponseEntity<ApiResponse<Void>> stockAlert(@Valid @RequestBody StockAlertRequest request, @AuthenticationPrincipal Jwt jwt) {
        alerts.subscribe(request, jwt == null ? null : UUID.fromString(jwt.getSubject()));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("STOCK_ALERT_CREATED", "We will email you when it is back", null));
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    ResponseEntity<String> sitemap() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic()).contentType(MediaType.APPLICATION_XML)
                .body(service.sitemap());
    }

    // Catalog data changes rarely and is not personal, so browsers may reuse it briefly.
    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePublic()).body(ApiResponse.success("SUCCESS", "Success", data));
    }
}
