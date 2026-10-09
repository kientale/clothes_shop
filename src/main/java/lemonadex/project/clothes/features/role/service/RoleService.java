package lemonadex.project.clothes.features.role.service;

import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.common.exception.*;
import lemonadex.project.clothes.features.role.dto.*;
import lemonadex.project.clothes.features.role.mapper.RoleMapper;
import lemonadex.project.clothes.features.role.model.*;
import lemonadex.project.clothes.features.role.repository.*;
import lemonadex.project.clothes.features.role.repository.specification.RoleSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final RoleMapper mapper;

    public PageResponse<RoleResponse> list(String search, int page, int size) {
        return PageResponse.from(roles.findAll(RoleSpecification.visible(search),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")))).map(this::response));
    }

    public RoleResponse get(UUID id) {
        return response(required(id));
    }

    public List<PermissionResponse> permissions(String module) {
        return permissions.findAll(Sort.by("module", "code")).stream().filter(permission -> !permission.isDeleted())
                .filter(permission -> module == null || module.isBlank() || permission.getModule().equalsIgnoreCase(module.strip()))
                .map(mapper::toPermission).toList();
    }

    @Transactional
    public RoleResponse create(RoleCreateRequest request) {
        Role role = mapper.toEntity(request);
        role.setName(request.name().strip());
        role.setPermissions(assignedPermissions(request.permissionIds()));
        return response(roles.saveAndFlush(role));
    }

    @Transactional
    public RoleResponse update(UUID id, RoleUpdateRequest request) {
        Role role = roles.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Role"));
        protectSystemRole(role);
        role.setName(request.name().strip());
        role.setPermissions(assignedPermissions(request.permissionIds()));
        roles.flush();
        return response(role);
    }

    @Transactional
    public void delete(UUID id) {
        Role role = roles.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Role"));
        protectSystemRole(role);
        if (roles.isAssigned(id)) throw new ConflictException("ROLE_IN_USE", "Role is assigned to an existing account");
        roles.delete(role);
        roles.flush();
    }

    private Role required(UUID id) {
        return roles.findByIdAndDeletedFalse(id).orElseThrow(() -> new ResourceNotFoundException("Role"));
    }

    private Set<Permission> assignedPermissions(Set<UUID> ids) {
        List<Permission> found = permissions.findAllByIdInAndDeletedFalse(ids);
        if (found.size() != ids.size()) throw new ResourceNotFoundException("Permission");
        return new LinkedHashSet<>(found);
    }

    private void protectSystemRole(Role role) {
        if (Set.of("ADMIN", "CUSTOMER").contains(role.getCode())) {
            throw new ConflictException("SYSTEM_ROLE_PROTECTED", "System roles cannot be updated or deleted");
        }
    }

    private RoleResponse response(Role role) {
        return mapper.toResponse(role, role.getPermissions().stream().filter(permission -> !permission.isDeleted())
                .sorted(Comparator.comparing(Permission::getCode)).toList());
    }
}
