package lemonadex.project.clothes.features.account.service;

import lemonadex.project.clothes.features.account.dto.AccountSummaryResponse;
import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.common.exception.ResourceNotFoundException;
import lemonadex.project.clothes.features.account.mapper.AccountMapper;
import lemonadex.project.clothes.features.account.model.AccountStatus;
import lemonadex.project.clothes.features.account.repository.AccountRepository;
import lemonadex.project.clothes.features.account.repository.specification.AccountSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountQueryService {
    private final AccountRepository accounts;
    private final AccountMapper mapper;

    public PageResponse<AccountSummaryResponse> list(AccountStatus status, String search, int page, int size) {
        Pageable pagination = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        return PageResponse.from(accounts.findAll(AccountSpecification.visible(status, search), pagination).map(mapper::toSummary));
    }

    public AccountSummaryResponse get(UUID id) {
        return mapper.toSummary(accounts.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account")));
    }
}
