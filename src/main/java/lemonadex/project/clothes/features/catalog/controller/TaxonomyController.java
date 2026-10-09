package lemonadex.project.clothes.features.catalog.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.common.dto.*;
import lemonadex.project.clothes.features.catalog.dto.CatalogRequests.*;
import lemonadex.project.clothes.features.catalog.dto.CatalogResponses.*;
import lemonadex.project.clothes.features.catalog.model.CatalogStatus;
import lemonadex.project.clothes.features.catalog.service.TaxonomyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** Categories, brands, colors, sizes and the combined picker options. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class TaxonomyController {
    private final TaxonomyService service;

    @GetMapping("/catalog/options")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    ResponseEntity<ApiResponse<CatalogOptionsResponse>> options() {
        return ok("CATALOG_OPTIONS_SUCCESS", "Catalog options", service.options());
    }

    // Categories

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    ResponseEntity<ApiResponse<PageResponse<CategoryResponse>>> categories(
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 254) String search,
            @RequestParam(required = false) CatalogStatus status,
            @RequestParam(required = false) UUID parentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok("CATEGORIES_SUCCESS", "Category list", service.categories(search, status, parentId, page, size));
    }

    @GetMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    ResponseEntity<ApiResponse<CategoryResponse>> category(@PathVariable UUID id) {
        return ok("CATEGORY_SUCCESS", "Category details", service.category(id));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<CategoryResponse>> createCategory(@Valid @RequestBody CategoryRequest request) {
        return created("CATEGORY_CREATED", "Category created", service.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return ok("CATEGORY_UPDATED", "Category updated", service.updateCategory(id, request));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable UUID id) {
        service.deleteCategory(id);
        return ok("CATEGORY_DELETED", "Category deleted", null);
    }

    // Brands

    @GetMapping("/brands")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    ResponseEntity<ApiResponse<PageResponse<BrandResponse>>> brands(
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 254) String search,
            @RequestParam(required = false) CatalogStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok("BRANDS_SUCCESS", "Brand list", service.brands(search, status, page, size));
    }

    @GetMapping("/brands/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    ResponseEntity<ApiResponse<BrandResponse>> brand(@PathVariable UUID id) {
        return ok("BRAND_SUCCESS", "Brand details", service.brand(id));
    }

    @PostMapping("/brands")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<BrandResponse>> createBrand(@Valid @RequestBody BrandRequest request) {
        return created("BRAND_CREATED", "Brand created", service.createBrand(request));
    }

    @PutMapping("/brands/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<BrandResponse>> updateBrand(@PathVariable UUID id, @Valid @RequestBody BrandRequest request) {
        return ok("BRAND_UPDATED", "Brand updated", service.updateBrand(id, request));
    }

    @DeleteMapping("/brands/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<Void>> deleteBrand(@PathVariable UUID id) {
        service.deleteBrand(id);
        return ok("BRAND_DELETED", "Brand deleted", null);
    }

    // Colors

    @GetMapping("/colors")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    ResponseEntity<ApiResponse<PageResponse<ColorResponse>>> colors(
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 254) String search,
            @RequestParam(required = false) CatalogStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok("COLORS_SUCCESS", "Color list", service.colors(search, status, page, size));
    }

    @GetMapping("/colors/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    ResponseEntity<ApiResponse<ColorResponse>> color(@PathVariable UUID id) {
        return ok("COLOR_SUCCESS", "Color details", service.color(id));
    }

    @PostMapping("/colors")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<ColorResponse>> createColor(@Valid @RequestBody ColorRequest request) {
        return created("COLOR_CREATED", "Color created", service.createColor(request));
    }

    @PutMapping("/colors/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<ColorResponse>> updateColor(@PathVariable UUID id, @Valid @RequestBody ColorRequest request) {
        return ok("COLOR_UPDATED", "Color updated", service.updateColor(id, request));
    }

    @DeleteMapping("/colors/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<Void>> deleteColor(@PathVariable UUID id) {
        service.deleteColor(id);
        return ok("COLOR_DELETED", "Color deleted", null);
    }

    // Sizes

    @GetMapping("/sizes")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    ResponseEntity<ApiResponse<PageResponse<SizeResponse>>> sizes(
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 254) String search,
            @RequestParam(required = false) CatalogStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok("SIZES_SUCCESS", "Size list", service.sizes(search, status, page, size));
    }

    @GetMapping("/sizes/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    ResponseEntity<ApiResponse<SizeResponse>> size(@PathVariable UUID id) {
        return ok("SIZE_SUCCESS", "Size details", service.size(id));
    }

    @PostMapping("/sizes")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<SizeResponse>> createSize(@Valid @RequestBody SizeRequest request) {
        return created("SIZE_CREATED", "Size created", service.createSize(request));
    }

    @PutMapping("/sizes/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<SizeResponse>> updateSize(@PathVariable UUID id, @Valid @RequestBody SizeRequest request) {
        return ok("SIZE_UPDATED", "Size updated", service.updateSize(id, request));
    }

    @DeleteMapping("/sizes/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<Void>> deleteSize(@PathVariable UUID id) {
        service.deleteSize(id);
        return ok("SIZE_DELETED", "Size deleted", null);
    }

    static <T> ResponseEntity<ApiResponse<T>> ok(String code, String message, T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(code, message, data));
    }

    static <T> ResponseEntity<ApiResponse<T>> created(String code, String message, T data) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(code, message, data));
    }
}
