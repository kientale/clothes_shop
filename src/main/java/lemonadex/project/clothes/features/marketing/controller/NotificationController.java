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
import lemonadex.project.clothes.features.marketing.service.NotificationService;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin")
public class NotificationController {
    private final NotificationService service;
    @GetMapping("/notifications") @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> notifications(@RequestParam(required = false) @Size(max = 254) String search,
            @RequestParam(required = false) NotificationStatus status, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.list(search, status, page, size)); }
    @GetMapping("/notifications/{id}") @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    ResponseEntity<ApiResponse<NotificationResponse>> notification(@PathVariable UUID id) { return ok(service.get(id)); }
    @PostMapping("/notifications") @PreAuthorize("hasAuthority('NOTIFICATION_WRITE')")
    ResponseEntity<ApiResponse<NotificationResponse>> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NotificationRequest request) { return created(service.create(request, UUID.fromString(jwt.getSubject()))); }
    @PutMapping("/notifications/{id}") @PreAuthorize("hasAuthority('NOTIFICATION_WRITE')")
    ResponseEntity<ApiResponse<NotificationResponse>> update(@PathVariable UUID id, @Valid @RequestBody NotificationRequest request) { return ok(service.update(id, request)); }
    @DeleteMapping("/notifications/{id}") @PreAuthorize("hasAuthority('NOTIFICATION_WRITE')")
    ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) { service.delete(id); return ok(null); }
    @PostMapping("/notifications/{id}/publish") @PreAuthorize("hasAuthority('NOTIFICATION_WRITE')")
    ResponseEntity<ApiResponse<NotificationResponse>> publish(@PathVariable UUID id) { return ok(service.publish(id)); }
    @GetMapping("/notifications/{id}/recipients") @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    ResponseEntity<ApiResponse<PageResponse<RecipientResponse>>> recipients(@PathVariable UUID id, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) { return ok(service.recipients(id, page, size)); }
    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("MANAGEMENT_SUCCESS", "Success", data)); }
    private static <T> ResponseEntity<ApiResponse<T>> created(T data) { return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success("CREATE_SUCCESS", "Created", data)); }
}
