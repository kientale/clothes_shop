package lemonadex.project.clothes.features.catalog.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.common.dto.*;
import lemonadex.project.clothes.features.catalog.dto.CatalogRequests.*;
import lemonadex.project.clothes.features.catalog.dto.CatalogResponses.*;
import lemonadex.project.clothes.features.catalog.dto.SizeChartDtos.SizeChart;
import lemonadex.project.clothes.features.catalog.model.*;
import lemonadex.project.clothes.features.catalog.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

import static lemonadex.project.clothes.features.catalog.controller.TaxonomyController.created;
import static lemonadex.project.clothes.features.catalog.controller.TaxonomyController.ok;

/** Products, product variants and collections. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService products;
    private final VariantService variants;
    private final SizeChartService sizeCharts;

    // Products

    @GetMapping("/products")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> products(
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 254) String search,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) UUID brandId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) ProductGender gender,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok("PRODUCTS_SUCCESS", "Product list", products.products(search, status, brandId, categoryId, gender, page, size));
    }

    @GetMapping("/products/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    ResponseEntity<ApiResponse<ProductResponse>> product(@PathVariable UUID id) {
        return ok("PRODUCT_SUCCESS", "Product details", products.product(id));
    }

    @PostMapping("/products")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request) {
        return created("PRODUCT_CREATED", "Product created", products.create(request));
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<ProductResponse>> updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return ok("PRODUCT_UPDATED", "Product updated", products.update(id, request));
    }

    /** Soft-deletes the product together with its variants. */
    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable UUID id) {
        products.delete(id);
        return ok("PRODUCT_DELETED", "Product deleted", null);
    }

    /** The product's size chart; data is null when it has none. */
    @GetMapping("/products/{id}/size-chart")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    ResponseEntity<ApiResponse<SizeChart>> sizeChart(@PathVariable UUID id) {
        return ok("SIZE_CHART_SUCCESS", "Size chart", sizeCharts.get(id));
    }

    @PutMapping("/products/{id}/size-chart")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<SizeChart>> saveSizeChart(@PathVariable UUID id, @Valid @RequestBody SizeChart request) {
        return ok("SIZE_CHART_SAVED", "Size chart saved", sizeCharts.save(id, request));
    }

    @DeleteMapping("/products/{id}/size-chart")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<Void>> deleteSizeChart(@PathVariable UUID id) {
        sizeCharts.delete(id);
        return ok("SIZE_CHART_DELETED", "Size chart removed", null);
    }

    // Variants

    @GetMapping("/product-variants")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    ResponseEntity<ApiResponse<PageResponse<VariantResponse>>> variants(
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 254) String search,
            @RequestParam(required = false) CatalogStatus status,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID colorId,
            @RequestParam(required = false) UUID sizeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok("VARIANTS_SUCCESS", "Variant list", variants.list(search, status, productId, colorId, sizeId, page, size));
    }

    @GetMapping("/product-variants/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    ResponseEntity<ApiResponse<VariantResponse>> variant(@PathVariable UUID id) {
        return ok("VARIANT_SUCCESS", "Variant details", variants.get(id));
    }

    @PostMapping("/product-variants")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<VariantResponse>> createVariant(@Valid @RequestBody VariantCreateRequest request) {
        return created("VARIANT_CREATED", "Variant created", variants.create(request));
    }

    @PostMapping("/product-variants/bulk")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<List<VariantResponse>>> bulkCreateVariants(@Valid @RequestBody VariantBulkRequest request) {
        return created("VARIANTS_CREATED", "Missing variants created", variants.bulkCreate(request));
    }

    @PutMapping("/product-variants/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<VariantResponse>> updateVariant(@PathVariable UUID id, @Valid @RequestBody VariantUpdateRequest request) {
        return ok("VARIANT_UPDATED", "Variant updated", variants.update(id, request));
    }

    @DeleteMapping("/product-variants/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<Void>> deleteVariant(@PathVariable UUID id) {
        variants.delete(id);
        return ok("VARIANT_DELETED", "Variant deleted", null);
    }

    // Collections

    @GetMapping("/collections")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    ResponseEntity<ApiResponse<PageResponse<CollectionResponse>>> collections(
            @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 254) String search,
            @RequestParam(required = false) CatalogStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ok("COLLECTIONS_SUCCESS", "Collection list", products.collections(search, status, page, size));
    }

    @GetMapping("/collections/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    ResponseEntity<ApiResponse<CollectionResponse>> collection(@PathVariable UUID id) {
        return ok("COLLECTION_SUCCESS", "Collection details", products.collection(id));
    }

    @PostMapping("/collections")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<CollectionResponse>> createCollection(@Valid @RequestBody CollectionRequest request) {
        return created("COLLECTION_CREATED", "Collection created", products.createCollection(request));
    }

    @PutMapping("/collections/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<CollectionResponse>> updateCollection(@PathVariable UUID id, @Valid @RequestBody CollectionRequest request) {
        return ok("COLLECTION_UPDATED", "Collection updated", products.updateCollection(id, request));
    }

    @DeleteMapping("/collections/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    ResponseEntity<ApiResponse<Void>> deleteCollection(@PathVariable UUID id) {
        products.deleteCollection(id);
        return ok("COLLECTION_DELETED", "Collection deleted", null);
    }
}
