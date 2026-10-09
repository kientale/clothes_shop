package lemonadex.project.clothes.features.role.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.common.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import lemonadex.project.clothes.features.role.dto.PermissionResponse;
import lemonadex.project.clothes.features.role.service.RoleService;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/permissions")
@RequiredArgsConstructor
public class PermissionController {
    private final RoleService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_READ')")
    ResponseEntity<ApiResponse<List<PermissionResponse>>> list(@RequestParam(required = false) @Size(max = 80) String module) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("PERMISSIONS_SUCCESS", "Permission catalog", service.permissions(module)));
    }
}
