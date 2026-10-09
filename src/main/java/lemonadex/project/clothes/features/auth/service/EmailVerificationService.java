package lemonadex.project.clothes.features.auth.service;

import lemonadex.project.clothes.common.exception.BadRequestException;
import lemonadex.project.clothes.common.exception.ConflictException;
import lemonadex.project.clothes.common.exception.ResourceNotFoundException;
import lemonadex.project.clothes.features.account.model.Account;
import lemonadex.project.clothes.features.account.repository.AccountRepository;
import lemonadex.project.clothes.features.auth.repository.EmailVerificationRepository;
import lemonadex.project.clothes.features.customer.model.Customer;
import lemonadex.project.clothes.features.customer.repository.CustomerRepository;
import lemonadex.project.clothes.features.mail.service.MailService;
import lemonadex.project.clothes.features.mail.service.MailTemplates;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Confirms that a customer owns the email they registered with. Unverified accounts can still shop; the
 * shop reminds them and offers to resend the link.
 */
@Service @RequiredArgsConstructor
public class EmailVerificationService {
    static final Duration LINK_LIFETIME = Duration.ofHours(24);
    private static final int MAX_LINKS_PER_HOUR = 3;

    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final EmailVerificationRepository links;
    private final MailService mail;
    private final Clock clock;

    /** Emails a fresh link; earlier links stop working. */
    @Transactional
    public void sendLink(Account account) {
        Instant now = clock.instant();
        links.purge(now.minus(Duration.ofDays(2)));
        String token = OneTimeLinks.newToken();
        links.revokeOpen(account.getId(), now);
        links.create(account.getId(), OneTimeLinks.hash(token), now.plus(LINK_LIFETIME));
        String name = customers.findByAccountIdAndDeletedFalse(account.getId()).map(Customer::getFullName).orElse("bạn");
        String link = mail.storefront() + "/shop/verify-email?token=" + token;
        mail.send(account.getEmail(), "Xác nhận email tài khoản LemonadeX", MailTemplates.emailVerification(name, link, LINK_LIFETIME.toHours()));
    }

    /** "Send the link again" for the signed-in account, at most a few times an hour. */
    @Transactional
    public void resend(UUID accountId) {
        Account account = accounts.findByIdAndDeletedFalse(accountId).orElseThrow(() -> new ResourceNotFoundException("Account"));
        if (account.getEmailVerifiedAt() != null) throw new ConflictException("EMAIL_ALREADY_VERIFIED", "The email is already verified");
        if (links.countSince(accountId, clock.instant().minus(Duration.ofHours(1))) >= MAX_LINKS_PER_HOUR)
            throw new ConflictException("TOO_MANY_VERIFICATION_EMAILS", "Several links were sent recently; check the inbox or try again later");
        sendLink(account);
    }

    @Transactional
    public void verify(String token) {
        Instant now = clock.instant();
        UUID accountId = links.consume(OneTimeLinks.hash(token.strip()), now)
                .orElseThrow(() -> new BadRequestException("INVALID_VERIFICATION_TOKEN", "The verification link is invalid or has expired"));
        Account account = accounts.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new BadRequestException("INVALID_VERIFICATION_TOKEN", "The verification link is invalid or has expired"));
        if (account.getEmailVerifiedAt() == null) account.setEmailVerifiedAt(now);
        links.revokeOpen(accountId, now);
    }
}
