package lemonadex.project.clothes.features.customer.service;

import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.common.exception.*;
import lemonadex.project.clothes.common.util.DateTimeUtils;
import lemonadex.project.clothes.common.util.SearchUtils;
import lemonadex.project.clothes.features.account.repository.AccountRepository;
import lemonadex.project.clothes.features.customer.dto.*;
import lemonadex.project.clothes.features.customer.mapper.CustomerMapper;
import lemonadex.project.clothes.features.customer.model.*;
import lemonadex.project.clothes.features.customer.repository.CustomerRepository;
import lemonadex.project.clothes.features.customer.repository.specification.CustomerSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final CustomerMapper mapper;

    public PageResponse<CustomerResponse> list(CustomerStatus status, String search, int page, int size) {
        Page<Customer> found = customers.findAll(CustomerSpecification.visible(status, search),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
        // One query for the whole page instead of loading each linked account.
        Map<UUID, String> emails = emails(found.map(Customer::getId).getContent());
        return PageResponse.from(found.map(customer -> mapper.toResponse(customer, emails.get(customer.getId()))));
    }

    public CustomerResponse get(UUID id) {
        return response(required(id));
    }

    public CustomerSummaryResponse summary() {
        Map<String, Long> counts = customers.countByStatus().stream()
                .collect(Collectors.toMap(CustomerRepository.StatusCount::getStatus, CustomerRepository.StatusCount::getTotal));
        long active = counts.getOrDefault(CustomerStatus.ACTIVE.name(), 0L);
        long inactive = counts.getOrDefault(CustomerStatus.INACTIVE.name(), 0L);
        long blocked = counts.getOrDefault(CustomerStatus.BLOCKED.name(), 0L);
        return new CustomerSummaryResponse(counts.values().stream().mapToLong(Long::longValue).sum(), active, inactive, blocked);
    }

    public List<LinkableAccountResponse> linkableAccounts(String search, int size) {
        String term = search == null || search.isBlank() ? null : SearchUtils.contains(search);
        return customers.findLinkableAccounts(term, size).stream()
                .map(account -> new LinkableAccountResponse(account.getId(), account.getEmail(),
                        DateTimeUtils.toOffsetDateTime(account.getCreatedAt())))
                .toList();
    }

    @Transactional
    public CustomerResponse create(CustomerCreateRequest request) {
        Customer customer = mapper.toEntity(request);
        customer.setFullName(request.fullName().strip());
        if (request.accountId() != null) {
            var account = accounts.findByIdAndDeletedFalse(request.accountId()).orElseThrow(() -> new ResourceNotFoundException("Account"));
            boolean customerRole = account.getRoles().stream().anyMatch(role -> !role.isDeleted() && "CUSTOMER".equals(role.getCode()));
            boolean adminRole = account.getRoles().stream().anyMatch(role -> !role.isDeleted() && "ADMIN".equals(role.getCode()));
            if (!customerRole || adminRole) throw new IllegalArgumentException("Customer profiles can only link to customer accounts");
            customer.setAccount(account);
        }
        return response(customers.saveAndFlush(customer));
    }

    @Transactional
    public CustomerResponse update(UUID id, CustomerUpdateRequest request) {
        Customer customer = required(id);
        mapper.update(request, customer);
        customer.setFullName(request.fullName().strip());
        customers.flush();
        return response(customer);
    }

    @Transactional
    public void delete(UUID id) {
        customers.delete(required(id));
        customers.flush();
    }

    private Customer required(UUID id) {
        return customers.findByIdAndDeletedFalse(id).orElseThrow(() -> new ResourceNotFoundException("Customer"));
    }

    private CustomerResponse response(Customer customer) {
        return mapper.toResponse(customer, emails(List.of(customer.getId())).get(customer.getId()));
    }

    private Map<UUID, String> emails(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return customers.findLinkedEmails(ids).stream()
                .collect(Collectors.toMap(CustomerRepository.LinkedEmail::getCustomerId, CustomerRepository.LinkedEmail::getEmail));
    }
}
