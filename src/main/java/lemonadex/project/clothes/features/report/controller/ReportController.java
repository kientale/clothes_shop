package lemonadex.project.clothes.features.report.controller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.common.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.time.Instant;
import lemonadex.project.clothes.features.report.dto.ReportResponses.*;
import lemonadex.project.clothes.features.report.model.*;
import lemonadex.project.clothes.features.order.model.ReturnStatus;
import lemonadex.project.clothes.features.order.model.ReturnType;
import lemonadex.project.clothes.features.report.service.ReportService;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin")
public class ReportController {
    private final ReportService service;
    @GetMapping("/reports/revenue") @PreAuthorize("hasAuthority('REPORT_REVENUE_READ')")
    ResponseEntity<ApiResponse<RevenueReport>> revenue(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) @Size(max = 50) String timezone, @RequestParam(required = false) UUID warehouseId, @RequestParam(defaultValue = "DAY") ReportBucket groupBy) { return ok(service.revenue(from, to, timezone, groupBy, warehouseId)); }
    @GetMapping("/reports/orders") @PreAuthorize("hasAuthority('REPORT_ORDER_READ')")
    ResponseEntity<ApiResponse<OrderReport>> orders(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) @Size(max = 50) String timezone, @RequestParam(required = false) UUID warehouseId, @RequestParam(defaultValue = "DAY") ReportBucket groupBy) { return ok(service.orders(from, to, timezone, groupBy, warehouseId)); }
    @GetMapping("/reports/bestsellers") @PreAuthorize("hasAuthority('REPORT_PRODUCT_READ')")
    ResponseEntity<ApiResponse<BestsellerReport>> bestsellers(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) @Size(max = 50) String timezone, @RequestParam(required = false) UUID warehouseId, @RequestParam(required = false) UUID productId, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.bestsellers(from, to, timezone, warehouseId, productId, page, size)); }
    @GetMapping("/reports/customers") @PreAuthorize("hasAuthority('REPORT_CUSTOMER_READ')")
    ResponseEntity<ApiResponse<CustomerReport>> customers(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) @Size(max = 50) String timezone, @RequestParam(required = false) UUID warehouseId, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.customers(from, to, timezone, warehouseId, page, size)); }
    @GetMapping("/reports/returns") @PreAuthorize("hasAuthority('REPORT_RETURN_READ')")
    ResponseEntity<ApiResponse<ReturnReport>> returns(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) @Size(max = 50) String timezone, @RequestParam(required = false) UUID warehouseId, @RequestParam(required = false) ReturnStatus status, @RequestParam(required = false) ReturnType requestType, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.returns(from, to, timezone, warehouseId, status, requestType, page, size)); }
    @GetMapping("/reports/promotions") @PreAuthorize("hasAuthority('REPORT_PROMOTION_READ')")
    ResponseEntity<ApiResponse<PromotionReport>> promotions(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) @Size(max = 50) String timezone, @RequestParam(required = false) UUID warehouseId, @RequestParam(required = false) CampaignType campaignType, @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.promotions(from, to, timezone, warehouseId, campaignType, page, size)); }
    @GetMapping("/reports/inventory") @PreAuthorize("hasAuthority('REPORT_INVENTORY_READ')")
    ResponseEntity<ApiResponse<InventoryReport>> inventory(@RequestParam(required = false) UUID warehouseId, @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) @Size(max = 254) String search, @RequestParam(defaultValue = "false") boolean lowStock,
            @RequestParam(defaultValue = "5") @Min(0) @Max(1000000000) int threshold,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.inventory(warehouseId, productId, search, lowStock, threshold, page, size)); }
    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("MANAGEMENT_SUCCESS", "Success", data)); }
    private static <T> ResponseEntity<ApiResponse<T>> created(T data) { return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success("CREATE_SUCCESS", "Created", data)); }
}
