package lemonadex.project.clothes.features.auth.service;

import lemonadex.project.clothes.features.account.service.AccountAccessService;

import lemonadex.project.clothes.features.auth.dto.*;
import lemonadex.project.clothes.features.account.dto.*;
import lemonadex.project.clothes.features.auth.exception.*;
import lemonadex.project.clothes.common.exception.*;
import lemonadex.project.clothes.features.auth.mapper.AuthMapper;
import lemonadex.project.clothes.features.account.model.*;
import lemonadex.project.clothes.features.customer.model.Customer;
import lemonadex.project.clothes.features.role.model.Role;
import lemonadex.project.clothes.features.account.repository.*;
import lemonadex.project.clothes.features.customer.repository.CustomerRepository;
import lemonadex.project.clothes.features.role.repository.RoleRepository;
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
    private final AdminProfileRepository adminProfiles;
    private final AuthMapper mapper;
    private final PasswordEncoder passwords;
    private final AccountAccessService access;
    private final AuthTokenService tokens;
    private final EmailVerificationService verification;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(AccountRepository accounts, CustomerRepository customers, RoleRepository roles,
                       AdminProfileRepository adminProfiles,
                       AuthMapper mapper, PasswordEncoder passwords, AccountAccessService access,
                       AuthTokenService tokens, EmailVerificationService verification, Clock clock) {
        this.accounts = accounts;
        this.customers = customers;
        this.roles = roles;
        this.adminProfiles = adminProfiles;
        this.mapper = mapper;
        this.passwords = passwords;
        this.access = access;
        this.tokens = tokens;
        this.verification = verification;
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
        verification.sendLink(account);
        return tokens.issue(response(account, customer));
    }

    /**
     * New customer account for an email that Google or Facebook has verified. It gets a random password
     * nobody knows; the owner can set one later through "forgot password".
     */
    @Transactional
    public Account createSocialCustomer(String email, String fullName) {
        Role customerRole = roles.findByCodeAndDeletedFalse("CUSTOMER")
                .orElseThrow(() -> new ResourceNotFoundException("Customer role"));
        Account account = new Account();
        account.setEmail(email);
        account.setPasswordHash(passwords.encode(UUID.randomUUID().toString()));
        account.setStatus(AccountStatus.ACTIVE);
        account.setEmailVerifiedAt(clock.instant());
        account.getRoles().add(customerRole);
        accounts.saveAndFlush(account);
        Customer customer = new Customer();
        String name = fullName == null || fullName.isBlank() ? email.substring(0, email.indexOf('@')) : fullName.strip();
        customer.setFullName(name.length() > 150 ? name.substring(0, 150) : name);
        customer.setAccount(account);
        customers.saveAndFlush(customer);
        return account;
    }

    /** Signs in a shopper whose identity Google or Facebook has vouched for. Staff accounts must use their password. */
    @Transactional
    public AuthResponse signInVerified(Account account) {
        boolean staff = account.getRoles().stream().anyMatch(role -> !"CUSTOMER".equals(role.getCode()));
        if (account.getStatus() != AccountStatus.ACTIVE || account.getRoles().isEmpty() || staff) {
            throw new BadCredentialsException("Invalid credentials");
        }
        // The provider has confirmed the address, which is at least as good as our own link.
        if (account.getEmailVerifiedAt() == null) account.setEmailVerifiedAt(clock.instant());
        account.setLastLoginAt(clock.instant());
        accounts.flush();
        return tokens.issue(response(account, customers.findByAccountIdAndDeletedFalse(account.getId()).orElse(null)));
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
        String avatarUrl = customer != null ? customer.getAvatarUrl()
                : adminProfiles.findByAccountIdAndDeletedFalse(account.getId())
                        .map(AdminProfile::getAvatarUrl).orElse(null);
        return mapper.toResponse(account, customer, avatarUrl, identity.roles(), identity.permissions());
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
