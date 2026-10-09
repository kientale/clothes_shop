package lemonadex.project.clothes.features.auth.service;

import lemonadex.project.clothes.common.exception.BadRequestException;
import lemonadex.project.clothes.common.util.PasswordPolicy;
import lemonadex.project.clothes.features.account.model.Account;
import lemonadex.project.clothes.features.account.model.AccountStatus;
import lemonadex.project.clothes.features.account.repository.AccountRepository;
import lemonadex.project.clothes.features.auth.dto.PasswordResetRequests.*;
import lemonadex.project.clothes.features.auth.repository.PasswordResetRepository;
import lemonadex.project.clothes.features.customer.model.Customer;
import lemonadex.project.clothes.features.customer.repository.CustomerRepository;
import lemonadex.project.clothes.features.mail.service.MailService;
import lemonadex.project.clothes.features.mail.service.MailTemplates;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * "Forgot password": emails a one-time link, then sets the new password. The request answers the same
 * whether or not the email is registered, so it cannot be used to find out who has an account.
 */
@Service @RequiredArgsConstructor
public class PasswordResetService {
    static final Duration LINK_LIFETIME = Duration.ofMinutes(30);
    private static final int MAX_LINKS_PER_HOUR = 3;

    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final PasswordResetRepository links;
    private final PasswordEncoder passwords;
    private final MailService mail;
    private final Clock clock;

    @Transactional
    public void requestLink(ForgotPasswordRequest request) {
        Instant now = clock.instant();
        links.purge(now.minus(Duration.ofDays(1)));
        Account account = accounts.findByEmailIgnoreCaseAndDeletedFalse(request.email().strip().toLowerCase(Locale.ROOT)).orElse(null);
        if (account == null || account.getStatus() != AccountStatus.ACTIVE) return;
        if (links.countSince(account.getId(), now.minus(Duration.ofHours(1))) >= MAX_LINKS_PER_HOUR) return;

        String token = OneTimeLinks.newToken();
        links.revokeOpen(account.getId(), now);
        links.create(account.getId(), hash(token), now.plus(LINK_LIFETIME));

        String name = customers.findByAccountIdAndDeletedFalse(account.getId()).map(Customer::getFullName).orElse("bạn");
        String link = mail.storefront() + "/shop/reset-password?token=" + token;
        mail.send(account.getEmail(), "Đặt lại mật khẩu LemonadeX", MailTemplates.passwordReset(name, link, LINK_LIFETIME.toMinutes()));
    }

    @Transactional
    public void reset(ResetPasswordRequest request) {
        PasswordPolicy.validate(request.password());
        Instant now = clock.instant();
        var accountId = links.consume(hash(request.token().strip()), now)
                .orElseThrow(() -> new BadRequestException("INVALID_RESET_TOKEN", "The reset link is invalid or has expired"));
        Account account = accounts.findByIdAndDeletedFalse(accountId)
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new BadRequestException("INVALID_RESET_TOKEN", "The reset link is invalid or has expired"));
        account.setPasswordHash(passwords.encode(request.password()));
        links.revokeOpen(accountId, now);
    }

    static String hash(String token) {
        return OneTimeLinks.hash(token);
    }
}
