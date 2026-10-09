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
public class FinanceController {
    private final PaymentService payments;
    private final RefundService refunds;
    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>> payments(@RequestParam(required = false) UUID orderId, @RequestParam(required = false) PaymentStatus status, @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok("PAYMENTS_SUCCESS", payments.list(orderId, status, from, to, page, size));
    }

    @GetMapping("/payments/methods")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    ResponseEntity<ApiResponse<List<PaymentMethodResponse>>> paymentMethods() {
        return ok("PAYMENT_METHODS_SUCCESS", payments.methods());
    }

    @GetMapping("/payments/{id}")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    ResponseEntity<ApiResponse<PaymentResponse>> payment(@PathVariable UUID id) {
        return ok("PAYMENT_SUCCESS", payments.get(id));
    }

    @PostMapping("/payments")
    @PreAuthorize("hasAuthority('PAYMENT_WRITE')")
    ResponseEntity<ApiResponse<PaymentResponse>> createPayment(@Valid @RequestBody PaymentRequest request) {
        return created("CREATE_PAYMENT_SUCCESS", payments.create(request));
    }

    @PutMapping("/payments/{id}/status")
    @PreAuthorize("hasAuthority('PAYMENT_WRITE')")
    ResponseEntity<ApiResponse<PaymentResponse>> paymentStatus(@PathVariable UUID id, @Valid @RequestBody PaymentStatusRequest request) {
        return ok("PAYMENT_STATUS_SUCCESS", payments.status(id, request));
    }

    @GetMapping("/payments/{id}/transactions")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    ResponseEntity<ApiResponse<PageResponse<PaymentTransactionResponse>>> paymentTransactions(@PathVariable UUID id, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok("PAYMENT_TRANSACTIONS_SUCCESS", payments.history(id, page, size));
    }

    @GetMapping("/refunds")
    @PreAuthorize("hasAuthority('REFUND_READ')")
    ResponseEntity<ApiResponse<PageResponse<RefundResponse>>> refunds(@RequestParam(required = false) UUID paymentId, @RequestParam(required = false) UUID returnRequestId, @RequestParam(required = false) RefundStatus status, @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok("REFUNDS_SUCCESS", refunds.list(paymentId, returnRequestId, status, from, to, page, size));
    }

    @GetMapping("/refunds/{id}")
    @PreAuthorize("hasAuthority('REFUND_READ')")
    ResponseEntity<ApiResponse<RefundResponse>> refund(@PathVariable UUID id) {
        return ok("REFUND_SUCCESS", refunds.get(id));
    }

    @PostMapping("/refunds")
    @PreAuthorize("hasAuthority('REFUND_WRITE')")
    ResponseEntity<ApiResponse<RefundResponse>> createRefund(@Valid @RequestBody RefundRequest request) {
        return created("CREATE_REFUND_SUCCESS", refunds.create(request));
    }

    @PutMapping("/refunds/{id}/status")
    @PreAuthorize("hasAuthority('REFUND_WRITE')")
    ResponseEntity<ApiResponse<RefundResponse>> refundStatus(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RefundStatusRequest request) {
        return ok("REFUND_STATUS_SUCCESS", refunds.status(id, request, UUID.fromString(jwt.getSubject())));
    }


    private static <T> ResponseEntity<ApiResponse<T>> ok(String code, T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(code, "Success", data));
    }
    private static <T> ResponseEntity<ApiResponse<T>> created(String code, T data) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(code, "Created", data));
    }
}
