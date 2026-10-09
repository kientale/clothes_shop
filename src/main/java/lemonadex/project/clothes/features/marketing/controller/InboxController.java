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
import lemonadex.project.clothes.features.marketing.dto.MarketingResponses.*;
import lemonadex.project.clothes.features.marketing.service.NotificationService;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/me") @PreAuthorize("hasRole('CUSTOMER')")
public class InboxController {
    private final NotificationService service;
    @GetMapping("/notifications")
    ResponseEntity<ApiResponse<PageResponse<InboxResponse>>> inbox(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Boolean read, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.inbox(UUID.fromString(jwt.getSubject()), read, page, size)); }
    @PutMapping("/notifications/{id}/read")
    ResponseEntity<ApiResponse<InboxResponse>> markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return ok(service.markRead(UUID.fromString(jwt.getSubject()), id)); }
    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("MANAGEMENT_SUCCESS", "Success", data)); }
    private static <T> ResponseEntity<ApiResponse<T>> created(T data) { return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success("CREATE_SUCCESS", "Created", data)); }
}
