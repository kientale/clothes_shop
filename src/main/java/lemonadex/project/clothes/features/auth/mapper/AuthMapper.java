package lemonadex.project.clothes.features.auth.mapper;

import lemonadex.project.clothes.features.auth.dto.*;
import lemonadex.project.clothes.features.account.dto.*;
import lemonadex.project.clothes.features.account.model.*;
import lemonadex.project.clothes.features.customer.model.Customer;
import lemonadex.project.clothes.common.util.DateTimeUtils;
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
    @Mapping(target = "emailVerifiedAt", ignore = true)
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
    @Mapping(target = "emailVerified", expression = "java(account.getEmailVerifiedAt() != null)")
    @Mapping(target = "status", source = "account.status")
    @Mapping(target = "roles", source = "roleCodes")
    @Mapping(target = "permissions", source = "permissionCodes")
    @Mapping(target = "customer", source = "customer")
    @Mapping(target = "avatarUrl", source = "avatarUrl")
    @Mapping(target = "lastLoginAt", source = "account.lastLoginAt")
    @Mapping(target = "createdAt", source = "account.createdAt")
    @Mapping(target = "updatedAt", source = "account.updatedAt")
    AccountResponse toResponse(Account account, Customer customer, String avatarUrl, List<String> roleCodes, List<String> permissionCodes);

    CustomerProfileResponse toProfile(Customer customer);
}
