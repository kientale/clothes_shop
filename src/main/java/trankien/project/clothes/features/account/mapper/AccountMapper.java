package trankien.project.clothes.features.account.mapper;

import trankien.project.clothes.common.util.DateTimeUtils;
import trankien.project.clothes.features.account.dto.AccountSummaryResponse;
import trankien.project.clothes.features.account.model.Account;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = DateTimeUtils.class, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AccountMapper {
    AccountSummaryResponse toSummary(Account account);
}
