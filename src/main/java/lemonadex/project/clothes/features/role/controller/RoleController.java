package lemonadex.project.clothes.features.role.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.common.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import lemonadex.project.clothes.features.role.dto.*;
import lemonadex.project.clothes.features.role.service.RoleService;


@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_READ')")
    ResponseEntity<ApiResponse<PageResponse<RoleResponse>>> list(
            @RequestParam(required = false) @Size(max = 254) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("ROLES_SUCCESS", "Role list", service.list(search, page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    ResponseEntity<ApiResponse<RoleResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("ROLE_SUCCESS", "Role details", service.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    ResponseEntity<ApiResponse<RoleResponse>> create(@Valid @RequestBody RoleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success("ROLE_CREATED", "Role created", service.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    ResponseEntity<ApiResponse<RoleResponse>> update(@PathVariable UUID id, @Valid @RequestBody RoleUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("ROLE_UPDATED", "Role updated", service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("ROLE_DELETED", "Role deleted", null));
    }
}
