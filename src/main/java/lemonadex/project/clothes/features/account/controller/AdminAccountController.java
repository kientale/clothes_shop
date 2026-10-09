package lemonadex.project.clothes.features.account.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.common.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import lemonadex.project.clothes.features.account.dto.*;
import lemonadex.project.clothes.features.account.model.AccountStatus;
import lemonadex.project.clothes.features.account.service.AdminAccountService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/v1/admin/admin-accounts")
@RequiredArgsConstructor
public class AdminAccountController {
    private final AdminAccountService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ACCOUNT_READ')")
    ResponseEntity<ApiResponse<PageResponse<AdminAccountResponse>>> list(
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) @Size(max = 254) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("ADMIN_ACCOUNTS_SUCCESS", "Administrator list", service.list(status, search, page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ACCOUNT_READ')")
    ResponseEntity<ApiResponse<AdminAccountResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("ADMIN_ACCOUNT_SUCCESS", "Administrator details", service.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ACCOUNT_WRITE')")
    ResponseEntity<ApiResponse<AdminAccountResponse>> create(@Valid @RequestBody AdminAccountCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success("ADMIN_ACCOUNT_CREATED", "Administrator created", service.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ACCOUNT_WRITE')")
    ResponseEntity<ApiResponse<AdminAccountResponse>> update(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AdminAccountUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("ADMIN_ACCOUNT_UPDATED", "Administrator updated", service.update(id, UUID.fromString(jwt.getSubject()), request)));
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('ACCOUNT_WRITE')")
    ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable UUID id, @Valid @RequestBody AdminPasswordRequest request) {
        service.resetPassword(id, request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("ADMIN_PASSWORD_UPDATED", "Administrator password updated", null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ACCOUNT_WRITE')")
    ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        service.delete(id, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("ADMIN_ACCOUNT_DELETED", "Administrator deleted", null));
    }
}
