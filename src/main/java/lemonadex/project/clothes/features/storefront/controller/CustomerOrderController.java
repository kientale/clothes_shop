package lemonadex.project.clothes.features.storefront.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lemonadex.project.clothes.features.payment.dto.PaymentGatewayResponses.PaymentRedirect;
import lemonadex.project.clothes.features.storefront.dto.StorefrontRequests.PayRequest;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.common.dto.ApiResponse;
import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.features.order.dto.OrderResponses.OrderResponse;
import lemonadex.project.clothes.features.order.model.OrderStatus;
import lemonadex.project.clothes.features.storefront.dto.StorefrontRequests.CheckoutRequest;
import lemonadex.project.clothes.features.storefront.dto.StorefrontResponses.CustomerOrder;
import lemonadex.project.clothes.features.storefront.service.CustomerOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** Checkout and order history of the signed-in customer; the customer always comes from the JWT. */
@RestController @RequiredArgsConstructor @Validated @RequestMapping("/api/v1/me") @PreAuthorize("hasRole('CUSTOMER')")
public class CustomerOrderController {
    private final CustomerOrderService service;

    @GetMapping("/orders")
    ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> orders(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok(service.list(account(jwt), status, page, size));
    }

    @GetMapping("/orders/{id}")
    ResponseEntity<ApiResponse<CustomerOrder>> order(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ok(service.get(account(jwt), id));
    }

    @PostMapping("/orders")
    ResponseEntity<ApiResponse<CustomerOrder>> checkout(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("CREATE_ORDER_SUCCESS", "Created", service.checkout(account(jwt), request)));
    }

    @PostMapping("/orders/{id}/cancel")
    ResponseEntity<ApiResponse<CustomerOrder>> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ok(service.cancel(account(jwt), id));
    }

    /** Starts an online payment (VNPay or MoMo); the shop sends the browser to the returned payUrl. */
    @PostMapping("/orders/{id}/pay")
    ResponseEntity<ApiResponse<PaymentRedirect>> pay(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                     @Valid @RequestBody PayRequest request, HttpServletRequest http) {
        return ok(service.pay(account(jwt), id, request.method(), http.getRemoteAddr()));
    }

    private static UUID account(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("SUCCESS", "Success", data));
    }
}
