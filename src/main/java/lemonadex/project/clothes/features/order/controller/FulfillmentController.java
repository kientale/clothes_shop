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
public class FulfillmentController {
    private final ShipmentService shipments;
    private final ReturnService returns;
    @GetMapping("/shipments")
    @PreAuthorize("hasAuthority('SHIPMENT_READ')")
    ResponseEntity<ApiResponse<PageResponse<ShipmentResponse>>> shipments(@RequestParam(required = false) @Size(max = 254) String search, @RequestParam(required = false) UUID orderId, @RequestParam(required = false) ShipmentStatus status, @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok("SHIPMENTS_SUCCESS", shipments.list(search, orderId, status, from, to, page, size));
    }

    @GetMapping("/shipments/{id}")
    @PreAuthorize("hasAuthority('SHIPMENT_READ')")
    ResponseEntity<ApiResponse<ShipmentResponse>> shipment(@PathVariable UUID id) {
        return ok("SHIPMENT_SUCCESS", shipments.get(id));
    }

    @PostMapping("/shipments")
    @PreAuthorize("hasAuthority('SHIPMENT_WRITE')")
    ResponseEntity<ApiResponse<ShipmentResponse>> createShipment(@Valid @RequestBody ShipmentRequest request) {
        return created("CREATE_SHIPMENT_SUCCESS", shipments.create(request));
    }

    @PutMapping("/shipments/{id}")
    @PreAuthorize("hasAuthority('SHIPMENT_WRITE')")
    ResponseEntity<ApiResponse<ShipmentResponse>> updateShipment(@PathVariable UUID id, @Valid @RequestBody ShipmentUpdateRequest request) {
        return ok("UPDATE_SHIPMENT_SUCCESS", shipments.update(id, request));
    }

    @PutMapping("/shipments/{id}/status")
    @PreAuthorize("hasAuthority('SHIPMENT_WRITE')")
    ResponseEntity<ApiResponse<ShipmentResponse>> shipmentStatus(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ShipmentStatusRequest request) {
        return ok("SHIPMENT_STATUS_SUCCESS", shipments.status(id, request, UUID.fromString(jwt.getSubject())));
    }

    @GetMapping("/shipments/history")
    @PreAuthorize("hasAuthority('SHIPMENT_READ')")
    ResponseEntity<ApiResponse<PageResponse<ShipmentHistoryResponse>>> shipmentHistory(@RequestParam(required = false) UUID shipmentId, @RequestParam(required = false) ShipmentStatus status, @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok("SHIPMENT_HISTORY_SUCCESS", shipments.history(shipmentId, status, from, to, page, size));
    }

    @GetMapping("/shipments/{id}/history")
    @PreAuthorize("hasAuthority('SHIPMENT_READ')")
    ResponseEntity<ApiResponse<PageResponse<ShipmentHistoryResponse>>> shipmentItemHistory(@PathVariable UUID id, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok("SHIPMENT_ITEM_HISTORY_SUCCESS", shipments.history(id, null, null, null, page, size));
    }

    @GetMapping("/returns")
    @PreAuthorize("hasAuthority('RETURN_READ')")
    ResponseEntity<ApiResponse<PageResponse<ReturnResponse>>> returns(@RequestParam(required = false) UUID orderId, @RequestParam(required = false) UUID customerId, @RequestParam(required = false) ReturnType requestType, @RequestParam(required = false) ReturnStatus status, @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok("RETURNS_SUCCESS", returns.list(orderId, customerId, requestType, status, from, to, page, size));
    }

    @GetMapping("/returns/{id}")
    @PreAuthorize("hasAuthority('RETURN_READ')")
    ResponseEntity<ApiResponse<ReturnResponse>> returnRequest(@PathVariable UUID id) {
        return ok("RETURN_REQUEST_SUCCESS", returns.get(id));
    }

    @PostMapping("/returns")
    @PreAuthorize("hasAuthority('RETURN_WRITE')")
    ResponseEntity<ApiResponse<ReturnResponse>> createReturn(@Valid @RequestBody CreateReturnRequest request) {
        return created("CREATE_RETURN_SUCCESS", returns.create(request));
    }

    @PutMapping("/returns/{id}/status")
    @PreAuthorize("hasAuthority('RETURN_WRITE')")
    ResponseEntity<ApiResponse<ReturnResponse>> returnStatus(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReturnStatusRequest request) {
        return ok("RETURN_STATUS_SUCCESS", returns.status(id, request, UUID.fromString(jwt.getSubject())));
    }


    private static <T> ResponseEntity<ApiResponse<T>> ok(String code, T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(code, "Success", data));
    }
    private static <T> ResponseEntity<ApiResponse<T>> created(String code, T data) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(code, "Created", data));
    }
}
