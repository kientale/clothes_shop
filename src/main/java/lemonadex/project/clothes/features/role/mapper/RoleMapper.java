package lemonadex.project.clothes.features.role.mapper;

import lemonadex.project.clothes.common.util.DateTimeUtils;
import lemonadex.project.clothes.features.role.dto.*;
import lemonadex.project.clothes.features.role.model.*;
import org.mapstruct.*;
import java.util.List;

@Mapper(componentModel = "spring", uses = DateTimeUtils.class, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RoleMapper {
    @Mapping(target = "permissions", source = "permissions")
    RoleResponse toResponse(Role role, List<Permission> permissions);
    PermissionResponse toPermission(Permission permission);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "permissions", ignore = true)
    Role toEntity(RoleCreateRequest request);
}
