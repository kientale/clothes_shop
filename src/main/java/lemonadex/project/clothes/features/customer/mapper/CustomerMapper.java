package lemonadex.project.clothes.features.customer.mapper;

import lemonadex.project.clothes.common.util.DateTimeUtils;
import lemonadex.project.clothes.features.customer.dto.*;
import lemonadex.project.clothes.features.customer.model.Customer;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateTimeUtils.class, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CustomerMapper {
    @Mapping(target = "accountId", source = "customer.account.id")
    @Mapping(target = "email", source = "email")
    CustomerResponse toResponse(Customer customer, String email);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "account", ignore = true)
    Customer toEntity(CustomerCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "account", ignore = true)
    void update(CustomerUpdateRequest request, @MappingTarget Customer customer);
}
