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
import lemonadex.project.clothes.features.content.dto.ContentResponses.*;
import lemonadex.project.clothes.features.content.model.*;
import lemonadex.project.clothes.features.content.service.ContentService;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/content")
public class PublishedContentController {
    private final ContentService service;
    @GetMapping("/articles")
    ResponseEntity<ApiResponse<PageResponse<ArticleSummaryResponse>>> articles(@RequestParam(required = false) @Size(max = 254) String search,
            @RequestParam(required = false) ArticleType articleType, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.publishedArticles(search, articleType, page, size)); }
    @GetMapping("/articles/{slug}")
    ResponseEntity<ApiResponse<PublishedArticleResponse>> article(@PathVariable @Size(max = 300) String slug) { return ok(service.publishedArticle(slug)); }
    @GetMapping("/store-policies/{policyType}")
    ResponseEntity<ApiResponse<ActivePolicyResponse>> policy(@PathVariable PolicyType policyType) { return ok(service.activePolicy(policyType)); }
    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("MANAGEMENT_SUCCESS", "Success", data)); }
    private static <T> ResponseEntity<ApiResponse<T>> created(T data) { return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success("CREATE_SUCCESS", "Created", data)); }
}
