package lemonadex.project.clothes.features.review.controller;
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
import lemonadex.project.clothes.features.review.dto.ReviewRequests.*;
import lemonadex.project.clothes.features.review.dto.ReviewResponses.*;
import lemonadex.project.clothes.features.review.model.ReviewStatus;
import lemonadex.project.clothes.features.review.service.ReviewService;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin")
public class ReviewController {
    private final ReviewService service;
    @GetMapping("/product-reviews") @PreAuthorize("hasAuthority('REVIEW_READ')")
    ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> reviews(@RequestParam(required = false) @Size(max = 254) String search,
            @RequestParam(required = false) ReviewStatus status, @RequestParam(required = false) UUID productId, @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) @Min(1) @Max(5) Integer rating, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.list(search, status, productId, customerId, rating, page, size)); }
    @GetMapping("/product-reviews/{id}") @PreAuthorize("hasAuthority('REVIEW_READ')")
    ResponseEntity<ApiResponse<ReviewResponse>> review(@PathVariable UUID id) { return ok(service.get(id)); }
    @PostMapping("/product-reviews") @PreAuthorize("hasAuthority('REVIEW_WRITE')")
    ResponseEntity<ApiResponse<ReviewResponse>> create(@RequestParam UUID customerId, @Valid @RequestBody ReviewRequest request) { return created(service.create(customerId, request)); }
    @PutMapping("/product-reviews/{id}/moderation") @PreAuthorize("hasAuthority('REVIEW_WRITE')")
    ResponseEntity<ApiResponse<ReviewResponse>> moderate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ModerationRequest request) { return ok(service.moderate(id, request, UUID.fromString(jwt.getSubject()))); }
    @DeleteMapping("/product-reviews/{id}") @PreAuthorize("hasAuthority('REVIEW_WRITE')")
    ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) { service.delete(id); return ok(null); }
    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("MANAGEMENT_SUCCESS", "Success", data)); }
    private static <T> ResponseEntity<ApiResponse<T>> created(T data) { return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success("CREATE_SUCCESS", "Created", data)); }
}
