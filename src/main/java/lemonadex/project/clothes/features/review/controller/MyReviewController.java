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
import lemonadex.project.clothes.features.review.service.ReviewService;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/me") @PreAuthorize("hasRole('CUSTOMER')")
public class MyReviewController {
    private final ReviewService service;
    @GetMapping("/product-reviews")
    ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> reviews(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.own(UUID.fromString(jwt.getSubject()), page, size)); }
    @PostMapping("/product-reviews")
    ResponseEntity<ApiResponse<ReviewResponse>> submit(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReviewRequest request) { return created(service.submit(UUID.fromString(jwt.getSubject()), request)); }
    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("MANAGEMENT_SUCCESS", "Success", data)); }
    private static <T> ResponseEntity<ApiResponse<T>> created(T data) { return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success("CREATE_SUCCESS", "Created", data)); }
}
