package lemonadex.project.clothes.service;

import lemonadex.project.clothes.dto.auth.*;
import lemonadex.project.clothes.exception.*;
import lemonadex.project.clothes.mapper.AuthMapper;
import lemonadex.project.clothes.model.*;
import lemonadex.project.clothes.repository.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class AuthService {
    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final RoleRepository roles;
    private final AuthMapper mapper;
    private final PasswordEncoder passwords;
    private final AccountAccessService access;
    private final AuthTokenService tokens;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(AccountRepository accounts, CustomerRepository customers, RoleRepository roles,
                       AuthMapper mapper, PasswordEncoder passwords, AccountAccessService access,
                       AuthTokenService tokens, Clock clock) {
        this.accounts = accounts;
        this.customers = customers;
        this.roles = roles;
        this.mapper = mapper;
        this.passwords = passwords;
        this.access = access;
        this.tokens = tokens;
        this.clock = clock;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        validatePassword(request.password());
        if (!Objects.equals(request.password(), request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords must match");
        }
        String email = normalize(request.email());
        if (accounts.existsByEmailIgnoreCaseAndDeletedFalse(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        Role customerRole = roles.findByCodeAndDeletedFalse("CUSTOMER")
                .orElseThrow(() -> new ResourceNotFoundException("Customer role"));
        Account account = mapper.toAccount(request);
        account.setEmail(email);
        account.setPasswordHash(passwords.encode(request.password()));
        account.setStatus(AccountStatus.ACTIVE);
        account.getRoles().add(customerRole);
        accounts.saveAndFlush(account);
        Customer customer = mapper.toCustomer(request);
        customer.setFullName(request.fullName().strip());
        customer.setAccount(account);
        customers.saveAndFlush(customer);
        return tokens.issue(response(account, customer));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        validatePassword(request.password());
        Account account = accounts.findByEmailIgnoreCaseAndDeletedFalse(normalize(request.email())).orElse(null);
        boolean matches = passwords.matches(request.password(), account == null ? dummyHash : account.getPasswordHash());
        if (!matches || account == null || account.getStatus() != AccountStatus.ACTIVE
                || account.getRoles().isEmpty()) {
            throw new BadCredentialsException("Invalid credentials");
        }
        account.setLastLoginAt(clock.instant());
        accounts.flush();
        Customer customer = customers.findByAccountIdAndDeletedFalse(account.getId()).orElse(null);
        return tokens.issue(response(account, customer));
    }

    public AccountResponse me(UUID id) {
        Account account = accounts.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account"));
        return response(account, customers.findByAccountIdAndDeletedFalse(id).orElse(null));
    }

    private AccountResponse response(Account account, Customer customer) {
        AccountIdentity identity = access.identity(account);
        return mapper.toResponse(account, customer, identity.roles(), identity.permissions());
    }

    private String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes");
        }
    }
}
