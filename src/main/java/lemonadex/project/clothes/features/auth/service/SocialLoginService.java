package lemonadex.project.clothes.features.auth.service;

import lemonadex.project.clothes.common.exception.BadRequestException;
import lemonadex.project.clothes.features.account.model.Account;
import lemonadex.project.clothes.features.account.repository.AccountRepository;
import lemonadex.project.clothes.features.auth.config.SocialLoginProperties;
import lemonadex.project.clothes.features.auth.dto.AuthResponse;
import lemonadex.project.clothes.features.auth.dto.SocialLoginRequests.*;
import lemonadex.project.clothes.features.auth.repository.AccountIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

/**
 * Google and Facebook sign-in for shoppers. A known identity signs into its account; otherwise the verified
 * email finds an existing account to link, or a new customer account is created. Staff accounts never sign in
 * this way.
 */
@Service @RequiredArgsConstructor
public class SocialLoginService {
    private final SocialLoginProperties properties;
    private final SocialTokenVerifier verifier;
    private final AccountIdentityRepository identities;
    private final AccountRepository accounts;
    private final AuthService auth;

    public SocialProviders providers() {
        return new SocialProviders(properties.googleClientId(), properties.facebookEnabled() ? properties.facebookAppId() : null);
    }

    @Transactional
    public AuthResponse google(GoogleLoginRequest request) {
        if (!properties.googleEnabled()) throw unavailable();
        return signIn(verifier.google(request.credential()));
    }

    @Transactional
    public AuthResponse facebook(FacebookLoginRequest request) {
        if (!properties.facebookEnabled()) throw unavailable();
        return signIn(verifier.facebook(request.accessToken()));
    }

    private AuthResponse signIn(VerifiedIdentity identity) {
        Account account = identities.accountOf(identity.provider(), identity.subject()).flatMap(accounts::findByIdAndDeletedFalse).orElse(null);
        if (account == null) {
            if (identity.email() == null || identity.email().isBlank() || !identity.emailVerified())
                throw new BadRequestException("SOCIAL_EMAIL_REQUIRED", "Allow access to a verified email address to sign in");
            String email = identity.email().strip().toLowerCase(Locale.ROOT);
            account = accounts.findByEmailIgnoreCaseAndDeletedFalse(email).orElse(null);
            if (account == null) account = auth.createSocialCustomer(email, identity.name());
            identities.link(account.getId(), identity.provider(), identity.subject(), email);
        }
        return auth.signInVerified(account);
    }

    private static BadRequestException unavailable() {
        return new BadRequestException("SOCIAL_LOGIN_UNAVAILABLE", "This sign-in method is not enabled");
    }
}
