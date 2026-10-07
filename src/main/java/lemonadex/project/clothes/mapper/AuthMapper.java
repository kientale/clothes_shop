package lemonadex.project.clothes.mapper;

import lemonadex.project.clothes.dto.auth.*;
import lemonadex.project.clothes.model.*;
import lemonadex.project.clothes.util.DateTimeUtils;
import org.mapstruct.*;
import java.util.List;

@Mapper(componentModel = "spring", uses = DateTimeUtils.class, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AuthMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "roles", ignore = true)
    Account toAccount(RegisterRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "gender", ignore = true)
    @Mapping(target = "avatarUrl", ignore = true)
    Customer toCustomer(RegisterRequest request);

    @Mapping(target = "id", source = "account.id")
    @Mapping(target = "email", source = "account.email")
    @Mapping(target = "status", source = "account.status")
    @Mapping(target = "roles", source = "roleCodes")
    @Mapping(target = "permissions", source = "permissionCodes")
    @Mapping(target = "customer", source = "customer")
    @Mapping(target = "lastLoginAt", source = "account.lastLoginAt")
    @Mapping(target = "createdAt", source = "account.createdAt")
    @Mapping(target = "updatedAt", source = "account.updatedAt")
    AccountResponse toResponse(Account account, Customer customer, List<String> roleCodes, List<String> permissionCodes);

    CustomerProfileResponse toProfile(Customer customer);
    AccountSummaryResponse toSummary(Account account);
}
