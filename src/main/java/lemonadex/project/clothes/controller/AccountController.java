package lemonadex.project.clothes.controller;

import jakarta.validation.constraints.*;
import lemonadex.project.clothes.dto.auth.AccountSummaryResponse;
import lemonadex.project.clothes.dto.common.*;
import lemonadex.project.clothes.model.AccountStatus;
import lemonadex.project.clothes.service.AccountQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ACCOUNT_READ')")
public class AccountController {
    private final AccountQueryService accounts;

    @GetMapping
    ResponseEntity<ApiResponse<PageResponse<AccountSummaryResponse>>> list(
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) @Size(max = 254) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("ACCOUNTS_SUCCESS", "Account list", accounts.list(status, search, page, size)));
    }

    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<AccountSummaryResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("ACCOUNT_SUCCESS", "Account details", accounts.get(id)));
    }
}
