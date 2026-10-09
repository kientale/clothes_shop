package lemonadex.project.clothes.features.marketing.controller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.common.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import lemonadex.project.clothes.features.marketing.dto.MarketingRequests.*;
import lemonadex.project.clothes.features.marketing.dto.MarketingResponses.*;
import lemonadex.project.clothes.features.marketing.model.*;
import lemonadex.project.clothes.features.marketing.service.MarketingService;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin")
public class MarketingController {
    private final MarketingService service;
    @GetMapping("/coupons") @PreAuthorize("hasAuthority('COUPON_READ')")
    ResponseEntity<ApiResponse<PageResponse<CouponResponse>>> coupons(@RequestParam(required = false) @Size(max = 254) String search,
            @RequestParam(required = false) MarketingStatus status, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.coupons(search, status, page, size)); }
    @GetMapping("/coupons/{id}") @PreAuthorize("hasAuthority('COUPON_READ')")
    ResponseEntity<ApiResponse<CouponResponse>> coupon(@PathVariable UUID id) { return ok(service.coupon(id)); }
    @PostMapping("/coupons") @PreAuthorize("hasAuthority('COUPON_WRITE')")
    ResponseEntity<ApiResponse<CouponResponse>> createCoupon(@Valid @RequestBody CouponRequest request) { return created(service.createCoupon(request)); }
    @PutMapping("/coupons/{id}") @PreAuthorize("hasAuthority('COUPON_WRITE')")
    ResponseEntity<ApiResponse<CouponResponse>> updateCoupon(@PathVariable UUID id, @Valid @RequestBody CouponRequest request) { return ok(service.updateCoupon(id, request)); }
    @DeleteMapping("/coupons/{id}") @PreAuthorize("hasAuthority('COUPON_WRITE')")
    ResponseEntity<ApiResponse<Void>> deleteCoupon(@PathVariable UUID id) { service.deleteCoupon(id); return ok(null); }
    @GetMapping("/promotions") @PreAuthorize("hasAuthority('PROMOTION_READ')")
    ResponseEntity<ApiResponse<PageResponse<PromotionResponse>>> promotions(@RequestParam(required = false) @Size(max = 254) String search,
            @RequestParam(required = false) MarketingStatus status, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.promotions(search, status, page, size)); }
    @GetMapping("/promotions/{id}") @PreAuthorize("hasAuthority('PROMOTION_READ')")
    ResponseEntity<ApiResponse<PromotionResponse>> promotion(@PathVariable UUID id) { return ok(service.promotion(id)); }
    @PostMapping("/promotions") @PreAuthorize("hasAuthority('PROMOTION_WRITE')")
    ResponseEntity<ApiResponse<PromotionResponse>> createPromotion(@Valid @RequestBody PromotionRequest request) { return created(service.createPromotion(request)); }
    @PutMapping("/promotions/{id}") @PreAuthorize("hasAuthority('PROMOTION_WRITE')")
    ResponseEntity<ApiResponse<PromotionResponse>> updatePromotion(@PathVariable UUID id, @Valid @RequestBody PromotionRequest request) { return ok(service.updatePromotion(id, request)); }
    @DeleteMapping("/promotions/{id}") @PreAuthorize("hasAuthority('PROMOTION_WRITE')")
    ResponseEntity<ApiResponse<Void>> deletePromotion(@PathVariable UUID id) { service.deletePromotion(id); return ok(null); }
    @GetMapping("/flash-sales") @PreAuthorize("hasAuthority('FLASH_SALE_READ')")
    ResponseEntity<ApiResponse<PageResponse<FlashSaleResponse>>> flashes(@RequestParam(required = false) @Size(max = 254) String search,
            @RequestParam(required = false) MarketingStatus status, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.flashes(search, status, page, size)); }
    @GetMapping("/flash-sales/{id}") @PreAuthorize("hasAuthority('FLASH_SALE_READ')")
    ResponseEntity<ApiResponse<FlashSaleResponse>> flash(@PathVariable UUID id) { return ok(service.flash(id)); }
    @PostMapping("/flash-sales") @PreAuthorize("hasAuthority('FLASH_SALE_WRITE')")
    ResponseEntity<ApiResponse<FlashSaleResponse>> createFlash(@Valid @RequestBody FlashSaleRequest request) { return created(service.createFlash(request)); }
    @PutMapping("/flash-sales/{id}") @PreAuthorize("hasAuthority('FLASH_SALE_WRITE')")
    ResponseEntity<ApiResponse<FlashSaleResponse>> updateFlash(@PathVariable UUID id, @Valid @RequestBody FlashSaleRequest request) { return ok(service.updateFlash(id, request)); }
    @DeleteMapping("/flash-sales/{id}") @PreAuthorize("hasAuthority('FLASH_SALE_WRITE')")
    ResponseEntity<ApiResponse<Void>> deleteFlash(@PathVariable UUID id) { service.deleteFlash(id); return ok(null); }
    @GetMapping("/banners") @PreAuthorize("hasAuthority('BANNER_READ')")
    ResponseEntity<ApiResponse<PageResponse<BannerResponse>>> banners(@RequestParam(required = false) @Size(max = 254) String search,
            @RequestParam(required = false) MarketingStatus status, @RequestParam(required = false) @Size(max = 50) String position, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.banners(search, status, position == null ? null : position.toUpperCase(Locale.ROOT), page, size)); }
    @GetMapping("/banners/{id}") @PreAuthorize("hasAuthority('BANNER_READ')")
    ResponseEntity<ApiResponse<BannerResponse>> banner(@PathVariable UUID id) { return ok(service.banner(id)); }
    @PostMapping("/banners") @PreAuthorize("hasAuthority('BANNER_WRITE')")
    ResponseEntity<ApiResponse<BannerResponse>> createBanner(@Valid @RequestBody BannerRequest request) { return created(service.createBanner(request)); }
    @PutMapping("/banners/{id}") @PreAuthorize("hasAuthority('BANNER_WRITE')")
    ResponseEntity<ApiResponse<BannerResponse>> updateBanner(@PathVariable UUID id, @Valid @RequestBody BannerRequest request) { return ok(service.updateBanner(id, request)); }
    @DeleteMapping("/banners/{id}") @PreAuthorize("hasAuthority('BANNER_WRITE')")
    ResponseEntity<ApiResponse<Void>> deleteBanner(@PathVariable UUID id) { service.deleteBanner(id); return ok(null); }
    @GetMapping("/coupons/{id}/usages") @PreAuthorize("hasAuthority('COUPON_READ')")
    ResponseEntity<ApiResponse<PageResponse<CouponUsageResponse>>> usages(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.usages(id, page, size)); }
    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("MANAGEMENT_SUCCESS", "Success", data)); }
    private static <T> ResponseEntity<ApiResponse<T>> created(T data) { return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success("CREATE_SUCCESS", "Created", data)); }
}
