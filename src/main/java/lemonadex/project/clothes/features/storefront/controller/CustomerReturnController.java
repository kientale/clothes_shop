package lemonadex.project.clothes.features.storefront.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.common.dto.ApiResponse;
import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.features.order.dto.OrderResponses.ReturnResponse;
import lemonadex.project.clothes.features.storefront.dto.StorefrontRequests.*;
import lemonadex.project.clothes.features.storefront.dto.StorefrontResponses.*;
import lemonadex.project.clothes.features.storefront.service.CartService;
import lemonadex.project.clothes.features.storefront.service.CustomerReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** The signed-in customer's returns/exchanges and saved cart; the customer always comes from the JWT. */
@RestController @RequiredArgsConstructor @Validated @RequestMapping("/api/v1/me") @PreAuthorize("hasRole('CUSTOMER')")
public class CustomerReturnController {
    private final CustomerReturnService returns;
    private final CartService cart;

    @GetMapping("/returns")
    ResponseEntity<ApiResponse<PageResponse<ReturnResponse>>> returns(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok(returns.list(account(jwt), orderId, page, size));
    }

    @GetMapping("/returns/{id}")
    ResponseEntity<ApiResponse<ReturnResponse>> returnRequest(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ok(returns.get(account(jwt), id));
    }

    @PostMapping("/returns")
    ResponseEntity<ApiResponse<ReturnResponse>> requestReturn(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CustomerReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("CREATE_RETURN_SUCCESS", "Created", returns.create(account(jwt), request)));
    }

    @PostMapping("/returns/{id}/cancel")
    ResponseEntity<ApiResponse<ReturnResponse>> cancelReturn(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ok(returns.cancel(account(jwt), id));
    }

    @GetMapping("/orders/{orderId}/items/{itemId}/exchange-options")
    ResponseEntity<ApiResponse<List<ExchangeOption>>> exchangeOptions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId,
                                                                      @PathVariable UUID itemId) {
        return ok(returns.exchangeOptions(account(jwt), orderId, itemId));
    }

    @GetMapping("/cart")
    ResponseEntity<ApiResponse<List<CartLine>>> cart(@AuthenticationPrincipal Jwt jwt) {
        return ok(cart.get(account(jwt)));
    }

    @PutMapping("/cart")
    ResponseEntity<ApiResponse<List<CartLine>>> saveCart(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CartRequest request) {
        return ok(cart.replace(account(jwt), request));
    }

    private static UUID account(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("SUCCESS", "Success", data));
    }
}
