package lemonadex.project.clothes.features.storefront.controller;

import jakarta.validation.constraints.*;
import lemonadex.project.clothes.common.dto.ApiResponse;
import lemonadex.project.clothes.features.storefront.dto.StorefrontResponses.StockAlertSummary;
import lemonadex.project.clothes.features.storefront.service.StockAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Which sold-out sizes shoppers are waiting for, so the shop knows what to restock first. */
@RestController @RequiredArgsConstructor @Validated @RequestMapping("/api/v1/admin")
public class StockAlertAdminController {
    private final StockAlertService alerts;

    @GetMapping("/stock-alerts")
    @PreAuthorize("hasAuthority('STOCK_ALERT_READ')")
    ResponseEntity<ApiResponse<List<StockAlertSummary>>> summary(@RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("SUCCESS", "Open back-in-stock requests", alerts.summary(limit)));
    }
}
