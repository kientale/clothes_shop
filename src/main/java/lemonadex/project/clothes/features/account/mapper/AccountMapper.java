package lemonadex.project.clothes.features.account.mapper;

import lemonadex.project.clothes.common.util.DateTimeUtils;
import lemonadex.project.clothes.features.account.dto.AccountSummaryResponse;
import lemonadex.project.clothes.features.account.model.Account;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = DateTimeUtils.class, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AccountMapper {
    AccountSummaryResponse toSummary(Account account);
}
