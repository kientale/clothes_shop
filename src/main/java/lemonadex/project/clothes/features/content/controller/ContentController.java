package lemonadex.project.clothes.features.content.controller;
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
import lemonadex.project.clothes.features.content.dto.ContentRequests.*;
import lemonadex.project.clothes.features.content.dto.ContentResponses.*;
import lemonadex.project.clothes.features.content.model.*;
import lemonadex.project.clothes.features.content.service.ContentService;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin")
public class ContentController {
    private final ContentService service;
    @GetMapping("/articles") @PreAuthorize("hasAuthority('ARTICLE_READ')")
    ResponseEntity<ApiResponse<PageResponse<ArticleResponse>>> articles(@RequestParam(required = false) @Size(max = 254) String search, @RequestParam(required = false) ArticleType articleType, @RequestParam(required = false) ArticleStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.articles(search, articleType, status, page, size)); }
    @GetMapping("/articles/{id}") @PreAuthorize("hasAuthority('ARTICLE_READ')")
    ResponseEntity<ApiResponse<ArticleResponse>> article(@PathVariable UUID id) { return ok(service.article(id)); }
    @PostMapping("/articles") @PreAuthorize("hasAuthority('ARTICLE_WRITE')")
    ResponseEntity<ApiResponse<ArticleResponse>> createArticle(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ArticleRequest request) { return created(service.createArticle(request, UUID.fromString(jwt.getSubject()))); }
    @PutMapping("/articles/{id}") @PreAuthorize("hasAuthority('ARTICLE_WRITE')")
    ResponseEntity<ApiResponse<ArticleResponse>> updateArticle(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ArticleRequest request) { return ok(service.updateArticle(id, request)); }
    @PutMapping("/articles/{id}/status") @PreAuthorize("hasAuthority('ARTICLE_WRITE')")
    ResponseEntity<ApiResponse<ArticleResponse>> articleStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ArticleStatusRequest request) { return ok(service.articleStatus(id, request)); }
    @DeleteMapping("/articles/{id}") @PreAuthorize("hasAuthority('ARTICLE_WRITE')")
    ResponseEntity<ApiResponse<Void>> deleteArticle(@PathVariable UUID id) { service.deleteArticle(id); return ok(null); }
    @GetMapping("/store-policies") @PreAuthorize("hasAuthority('POLICY_READ')")
    ResponseEntity<ApiResponse<PageResponse<PolicyResponse>>> policies(@RequestParam(required = false) PolicyType policyType, @RequestParam(required = false) PolicyStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.policies(policyType, status, page, size)); }
    @GetMapping("/store-policies/{id}") @PreAuthorize("hasAuthority('POLICY_READ')")
    ResponseEntity<ApiResponse<PolicyResponse>> policy(@PathVariable UUID id) { return ok(service.policy(id)); }
    @PostMapping("/store-policies") @PreAuthorize("hasAuthority('POLICY_WRITE')")
    ResponseEntity<ApiResponse<PolicyResponse>> createPolicy(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PolicyRequest request) { return created(service.createPolicy(request, UUID.fromString(jwt.getSubject()))); }
    @PutMapping("/store-policies/{id}") @PreAuthorize("hasAuthority('POLICY_WRITE')")
    ResponseEntity<ApiResponse<PolicyResponse>> updatePolicy(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody PolicyRequest request) { return ok(service.updatePolicy(id, request, UUID.fromString(jwt.getSubject()))); }
    @PutMapping("/store-policies/{id}/status") @PreAuthorize("hasAuthority('POLICY_WRITE')")
    ResponseEntity<ApiResponse<PolicyResponse>> policyStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody PolicyStatusRequest request) { return ok(service.policyStatus(id, request, UUID.fromString(jwt.getSubject()))); }
    @DeleteMapping("/store-policies/{id}") @PreAuthorize("hasAuthority('POLICY_WRITE')")
    ResponseEntity<ApiResponse<Void>> deletePolicy(@PathVariable UUID id) { service.deletePolicy(id); return ok(null); }
    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("MANAGEMENT_SUCCESS", "Success", data)); }
    private static <T> ResponseEntity<ApiResponse<T>> created(T data) { return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success("CREATE_SUCCESS", "Created", data)); }
}
