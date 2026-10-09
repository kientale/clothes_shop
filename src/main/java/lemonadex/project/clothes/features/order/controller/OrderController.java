package lemonadex.project.clothes.features.order.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.common.dto.*;
import lemonadex.project.clothes.features.order.dto.OrderRequests.*;
import lemonadex.project.clothes.features.order.dto.OrderResponses.*;
import lemonadex.project.clothes.features.order.model.*;
import lemonadex.project.clothes.features.order.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin")
public class OrderController {
    private final OrderService service;
    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> orders(@RequestParam(required = false) @Size(max = 254) String search, @RequestParam(required = false) OrderStatus status, @RequestParam(required = false) OrderPaymentStatus paymentStatus, @RequestParam(required = false) ShippingStatus shippingStatus, @RequestParam(required = false) UUID customerId, @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok("ORDERS_SUCCESS", service.list(search, status, paymentStatus, shippingStatus, customerId, from, to, page, size));
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    ResponseEntity<ApiResponse<OrderResponse>> order(@PathVariable UUID id) {
        return ok("ORDER_SUCCESS", service.get(id));
    }

    @PostMapping("/orders")
    @PreAuthorize("hasAuthority('ORDER_WRITE')")
    ResponseEntity<ApiResponse<OrderResponse>> createOrder(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateOrderRequest request) {
        return created("CREATE_ORDER_SUCCESS", service.create(request, UUID.fromString(jwt.getSubject())));
    }

    @PutMapping("/orders/{id}")
    @PreAuthorize("hasAuthority('ORDER_WRITE')")
    ResponseEntity<ApiResponse<OrderResponse>> updateOrder(@PathVariable UUID id, @Valid @RequestBody UpdateOrderRequest request) {
        return ok("UPDATE_ORDER_SUCCESS", service.update(id, request));
    }

    @PutMapping("/orders/{id}/status")
    @PreAuthorize("hasAuthority('ORDER_WRITE')")
    ResponseEntity<ApiResponse<OrderResponse>> orderStatus(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OrderStatusRequest request) {
        return ok("ORDER_STATUS_SUCCESS", service.status(id, request, UUID.fromString(jwt.getSubject())));
    }

    @GetMapping("/orders/{id}/history")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    ResponseEntity<ApiResponse<PageResponse<OrderHistoryResponse>>> orderHistory(@PathVariable UUID id, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok("ORDER_HISTORY_SUCCESS", service.history(id, page, size));
    }


    private static <T> ResponseEntity<ApiResponse<T>> ok(String code, T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(code, "Success", data));
    }
    private static <T> ResponseEntity<ApiResponse<T>> created(String code, T data) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(code, "Created", data));
    }
}
