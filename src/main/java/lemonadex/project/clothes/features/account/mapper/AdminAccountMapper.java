package lemonadex.project.clothes.features.account.mapper;

import lemonadex.project.clothes.common.util.DateTimeUtils;
import lemonadex.project.clothes.features.account.dto.*;
import lemonadex.project.clothes.features.account.model.*;
import org.mapstruct.*;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", uses = DateTimeUtils.class, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AdminAccountMapper {
    @Mapping(target = "id", source = "account.id")
    @Mapping(target = "email", source = "account.email")
    @Mapping(target = "status", source = "account.status")
    @Mapping(target = "fullName", source = "profile.fullName")
    @Mapping(target = "phone", source = "profile.phone")
    @Mapping(target = "avatarUrl", source = "profile.avatarUrl")
    @Mapping(target = "roles", source = "roleCodes")
    @Mapping(target = "roleIds", source = "roleIds")
    @Mapping(target = "lastLoginAt", source = "account.lastLoginAt")
    @Mapping(target = "createdAt", source = "account.createdAt")
    @Mapping(target = "updatedAt", source = "account.updatedAt")
    AdminAccountResponse toResponse(Account account, AdminProfile profile, List<String> roleCodes, List<UUID> roleIds);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "account", ignore = true)
    AdminProfile toProfile(AdminAccountCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "account", ignore = true)
    void updateProfile(AdminAccountUpdateRequest request, @MappingTarget AdminProfile profile);
}
