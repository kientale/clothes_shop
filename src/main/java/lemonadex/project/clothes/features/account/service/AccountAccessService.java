package lemonadex.project.clothes.features.account.service;

import lemonadex.project.clothes.features.account.dto.AccountIdentity;
import lemonadex.project.clothes.features.account.model.*;
import lemonadex.project.clothes.features.role.model.Permission;
import lemonadex.project.clothes.features.role.model.Role;
import lemonadex.project.clothes.features.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountAccessService {
    private final AccountRepository accounts;

    public AccountIdentity activeIdentity(UUID id) {
        Account account = accounts.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BadCredentialsException("Account unavailable"));
        if (account.getStatus() != AccountStatus.ACTIVE || account.getRoles().isEmpty()) {
            throw new BadCredentialsException("Account unavailable");
        }
        return identity(account);
    }

    public AccountIdentity identity(Account account) {
        return new AccountIdentity(account.getId(), account.getEmail(),
                account.getRoles().stream().filter(role -> !role.isDeleted()).map(Role::getCode).distinct().sorted().toList(),
                account.getRoles().stream().filter(role -> !role.isDeleted())
                        .flatMap(role -> role.getPermissions().stream()).filter(permission -> !permission.isDeleted())
                        .map(Permission::getCode).distinct().sorted().toList());
    }
}
