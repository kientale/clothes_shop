package lemonadex.project.clothes.features.inventory.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.common.dto.*;
import lemonadex.project.clothes.features.inventory.dto.InventoryRequests.*;
import lemonadex.project.clothes.features.inventory.dto.InventoryResponses.*;
import lemonadex.project.clothes.features.inventory.model.*;
import lemonadex.project.clothes.features.inventory.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin")
public class InventoryController {
    private final InventoryService service;
    @GetMapping("/warehouses")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    ResponseEntity<ApiResponse<PageResponse<WarehouseResponse>>> warehouses(@RequestParam(required = false) @Size(max = 254) String search, @RequestParam(required = false) WarehouseStatus status, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok("WAREHOUSES_SUCCESS", service.warehouses(search, status, page, size));
    }

    @GetMapping("/warehouses/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    ResponseEntity<ApiResponse<WarehouseResponse>> warehouse(@PathVariable UUID id) {
        return ok("WAREHOUSE_SUCCESS", service.warehouse(id));
    }

    @PostMapping("/warehouses")
    @PreAuthorize("hasAuthority('INVENTORY_WRITE')")
    ResponseEntity<ApiResponse<WarehouseResponse>> createWarehouse(@Valid @RequestBody WarehouseRequest request) {
        return created("CREATE_WAREHOUSE_SUCCESS", service.createWarehouse(request));
    }

    @PutMapping("/warehouses/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_WRITE')")
    ResponseEntity<ApiResponse<WarehouseResponse>> updateWarehouse(@PathVariable UUID id, @Valid @RequestBody WarehouseRequest request) {
        return ok("UPDATE_WAREHOUSE_SUCCESS", service.updateWarehouse(id, request));
    }

    @DeleteMapping("/warehouses/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_WRITE')")
    ResponseEntity<ApiResponse<Void>> deleteWarehouse(@PathVariable UUID id) {
        service.deleteWarehouse(id); return ok("WAREHOUSE_DELETED", null);
    }

    @GetMapping("/inventory")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    ResponseEntity<ApiResponse<PageResponse<StockResponse>>> inventory(@RequestParam(required = false) @Size(max = 254) String search, @RequestParam(required = false) UUID warehouseId, @RequestParam(required = false) UUID productVariantId, @RequestParam(required = false) Boolean lowStock, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok("INVENTORY_SUCCESS", service.list(search, warehouseId, productVariantId, lowStock, page, size));
    }

    @GetMapping("/inventory/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    ResponseEntity<ApiResponse<StockResponse>> stock(@PathVariable UUID id) {
        return ok("STOCK_SUCCESS", service.get(id));
    }

    @PostMapping("/inventory")
    @PreAuthorize("hasAuthority('INVENTORY_WRITE')")
    ResponseEntity<ApiResponse<StockResponse>> createStock(@Valid @RequestBody StockRequest request) {
        return created("CREATE_STOCK_SUCCESS", service.create(request));
    }

    @PostMapping("/inventory/{id}/adjustments")
    @PreAuthorize("hasAuthority('INVENTORY_WRITE')")
    ResponseEntity<ApiResponse<StockResponse>> adjustStock(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AdjustmentRequest request) {
        return created("ADJUST_STOCK_SUCCESS", service.adjust(id, request, UUID.fromString(jwt.getSubject())));
    }

    @GetMapping("/inventory/transactions")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    ResponseEntity<ApiResponse<PageResponse<MovementResponse>>> transactions(@RequestParam(required = false) UUID warehouseId, @RequestParam(required = false) UUID productVariantId, @RequestParam(required = false) MovementType transactionType, @RequestParam(required = false) UUID referenceId, @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok("TRANSACTIONS_SUCCESS", service.movements(warehouseId, productVariantId, transactionType, referenceId, from, to, page, size));
    }

    @GetMapping("/inventory/transactions/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    ResponseEntity<ApiResponse<MovementResponse>> transaction(@PathVariable UUID id) {
        return ok("TRANSACTION_SUCCESS", service.movement(id));
    }

    @PostMapping("/inventory/transactions")
    @PreAuthorize("hasAuthority('INVENTORY_WRITE')")
    ResponseEntity<ApiResponse<MovementResponse>> createMovement(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MovementRequest request) {
        return created("CREATE_MOVEMENT_SUCCESS", service.createMovement(request, UUID.fromString(jwt.getSubject())));
    }

    @GetMapping("/inventory/history")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    ResponseEntity<ApiResponse<PageResponse<MovementResponse>>> adjustmentHistory(@RequestParam(required = false) UUID warehouseId, @RequestParam(required = false) UUID productVariantId, @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok("ADJUSTMENT_HISTORY_SUCCESS", service.movements(warehouseId, productVariantId, MovementType.ADJUSTMENT, null, from, to, page, size));
    }


    private static <T> ResponseEntity<ApiResponse<T>> ok(String code, T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(code, "Success", data));
    }
    private static <T> ResponseEntity<ApiResponse<T>> created(String code, T data) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(code, "Created", data));
    }
}
