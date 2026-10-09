package lemonadex.project.clothes.features.customer.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.common.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import lemonadex.project.clothes.features.customer.dto.*;
import lemonadex.project.clothes.features.customer.service.CustomerService;
import lemonadex.project.clothes.features.customer.model.CustomerStatus;

@RestController
@RequestMapping("/api/v1/admin/customers")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService service;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    ResponseEntity<ApiResponse<PageResponse<CustomerResponse>>> list(
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(required = false) @Size(max = 254) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("CUSTOMERS_SUCCESS", "Customer list", service.list(status, search, page, size)));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    ResponseEntity<ApiResponse<CustomerSummaryResponse>> summary() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("CUSTOMER_SUMMARY_SUCCESS", "Customer counts by status", service.summary()));
    }

    /** Customer accounts without a profile, for linking a new profile; needs write access like POST. */
    @GetMapping("/linkable-accounts")
    @PreAuthorize("hasAuthority('CUSTOMER_WRITE')")
    ResponseEntity<ApiResponse<List<LinkableAccountResponse>>> linkableAccounts(
            @RequestParam(required = false) @Size(max = 254) String search,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("LINKABLE_ACCOUNTS_SUCCESS", "Customer accounts without a profile", service.linkableAccounts(search, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    ResponseEntity<ApiResponse<CustomerResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("CUSTOMER_SUCCESS", "Customer details", service.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_WRITE')")
    ResponseEntity<ApiResponse<CustomerResponse>> create(@Valid @RequestBody CustomerCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success("CUSTOMER_CREATED", "Customer created", service.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_WRITE')")
    ResponseEntity<ApiResponse<CustomerResponse>> update(@PathVariable UUID id, @Valid @RequestBody CustomerUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("CUSTOMER_UPDATED", "Customer updated", service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_WRITE')")
    ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("CUSTOMER_DELETED", "Customer deleted", null));
    }
}
