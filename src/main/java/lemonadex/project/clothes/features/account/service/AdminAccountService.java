package lemonadex.project.clothes.features.account.service;

import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.common.exception.*;
import lemonadex.project.clothes.common.util.PasswordPolicy;
import lemonadex.project.clothes.features.account.dto.*;
import lemonadex.project.clothes.features.account.mapper.AdminAccountMapper;
import lemonadex.project.clothes.features.account.model.*;
import lemonadex.project.clothes.features.account.repository.*;
import lemonadex.project.clothes.features.account.repository.specification.AdminAccountSpecification;
import lemonadex.project.clothes.features.role.model.Role;
import lemonadex.project.clothes.features.role.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAccountService {
    private final AccountRepository accounts;
    private final AdminProfileRepository profiles;
    private final RoleRepository roles;
    private final AdminAccountMapper mapper;
    private final PasswordEncoder passwords;

    public PageResponse<AdminAccountResponse> list(AccountStatus status, String search, int page, int size) {
        return PageResponse.from(accounts.findAll(AdminAccountSpecification.visible(status, search),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")))).map(this::response));
    }

    public AdminAccountResponse get(UUID id) {
        return response(required(id));
    }

    @Transactional
    public AdminAccountResponse create(AdminAccountCreateRequest request) {
        PasswordPolicy.validate(request.password());
        Set<Role> assigned = assignedRoles(request.roleIds());
        Account account = new Account();
        account.setEmail(email(request.email()));
        account.setPasswordHash(passwords.encode(request.password()));
        // Staff accounts are created by an administrator, who vouches for the address.
        account.setEmailVerifiedAt(java.time.Instant.now());
        account.setRoles(assigned);
        accounts.saveAndFlush(account);
        AdminProfile profile = mapper.toProfile(request);
        profile.setFullName(request.fullName().strip());
        profile.setAccount(account);
        profiles.saveAndFlush(profile);
        return response(account);
    }

    @Transactional
    public AdminAccountResponse update(UUID id, UUID actorId, AdminAccountUpdateRequest request) {
        List<Account> administrators = accounts.lockAdministrators();
        Account account = required(id);
        if (request.status() != AccountStatus.ACTIVE) protect(account, actorId, administrators);
        Set<Role> assigned = assignedRoles(request.roleIds());
        account.setEmail(email(request.email()));
        account.setStatus(request.status());
        account.setRoles(assigned);
        AdminProfile profile = profiles.findByAccountIdAndDeletedFalse(id).orElseGet(AdminProfile::new);
        mapper.updateProfile(request, profile);
        profile.setFullName(request.fullName().strip());
        profile.setAccount(account);
        profiles.saveAndFlush(profile);
        accounts.flush();
        return response(account);
    }

    @Transactional
    public void resetPassword(UUID id, AdminPasswordRequest request) {
        PasswordPolicy.validate(request.password());
        Account account = required(id);
        account.setPasswordHash(passwords.encode(request.password()));
        accounts.flush();
    }

    @Transactional
    public void delete(UUID id, UUID actorId) {
        List<Account> administrators = accounts.lockAdministrators();
        Account account = required(id);
        protect(account, actorId, administrators);
        profiles.findByAccountIdAndDeletedFalse(id).ifPresent(profiles::delete);
        accounts.delete(account);
        accounts.flush();
    }

    private Account required(UUID id) {
        Account account = accounts.findByIdAndDeletedFalse(id).orElseThrow(() -> new ResourceNotFoundException("Admin account"));
        if (account.getRoles().stream().noneMatch(role -> !role.isDeleted() && "ADMIN".equals(role.getCode()))) {
            throw new ResourceNotFoundException("Admin account");
        }
        return account;
    }

    private Set<Role> assignedRoles(Set<UUID> ids) {
        List<Role> found = roles.findAllByIdInAndDeletedFalse(ids);
        if (found.size() != ids.size()) throw new ResourceNotFoundException("Role");
        if (found.stream().noneMatch(role -> "ADMIN".equals(role.getCode()))) {
            throw new IllegalArgumentException("An administrator must retain the ADMIN role");
        }
        return new LinkedHashSet<>(found);
    }

    private void protect(Account target, UUID actorId, List<Account> administrators) {
        if (target.getId().equals(actorId)) {
            throw new ConflictException("SELF_ADMIN_MODIFICATION", "You cannot disable or delete your own administrator account");
        }
        if (target.getStatus() == AccountStatus.ACTIVE
                && administrators.stream().filter(account -> account.getStatus() == AccountStatus.ACTIVE).count() <= 1) {
            throw new ConflictException("LAST_ACTIVE_ADMIN", "At least one active administrator must remain");
        }
    }

    private String email(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private AdminAccountResponse response(Account account) {
        List<Role> visible = account.getRoles().stream().filter(role -> !role.isDeleted()).sorted(Comparator.comparing(Role::getCode)).toList();
        return mapper.toResponse(account, profiles.findByAccountIdAndDeletedFalse(account.getId()).orElse(null),
                visible.stream().map(Role::getCode).toList(), visible.stream().map(Role::getId).toList());
    }
}
