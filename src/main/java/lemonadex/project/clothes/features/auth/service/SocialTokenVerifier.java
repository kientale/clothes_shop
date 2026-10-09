package lemonadex.project.clothes.features.auth.service;

import lemonadex.project.clothes.common.exception.BadRequestException;
import lemonadex.project.clothes.features.auth.config.SocialLoginProperties;
import lemonadex.project.clothes.features.auth.dto.SocialLoginRequests.VerifiedIdentity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.Map;
import java.util.Set;

/**
 * Checks tokens that the Google and Facebook buttons hand to the browser. Google ID tokens are JWTs signed with
 * Google's published keys; Facebook access tokens are checked with the Graph API debug_token call, which also
 * proves the token was issued to this app (not to some other site the user signed in to).
 */
@Slf4j
@Service
public class SocialTokenVerifier {
    private static final String GOOGLE_KEYS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private static final String GRAPH = "https://graph.facebook.com/v21.0";

    private final SocialLoginProperties properties;
    private final RestClient http = RestClient.create();
    private volatile JwtDecoder google;

    public SocialTokenVerifier(SocialLoginProperties properties) {
        this.properties = properties;
    }

    public VerifiedIdentity google(String credential) {
        Jwt jwt;
        try {
            jwt = googleDecoder().decode(credential);
        } catch (JwtException ex) {
            throw invalid();
        }
        return new VerifiedIdentity("GOOGLE", jwt.getSubject(), jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")), jwt.getClaimAsString("name"));
    }

    @SuppressWarnings("unchecked")
    public VerifiedIdentity facebook(String accessToken) {
        try {
            Map<String, Object> debug = http.get().uri(GRAPH + "/debug_token?input_token={token}&access_token={app}",
                    accessToken, properties.facebookAppId() + "|" + properties.facebookAppSecret()).retrieve().body(Map.class);
            Map<String, Object> data = debug == null ? null : (Map<String, Object>) debug.get("data");
            if (data == null || !Boolean.TRUE.equals(data.get("is_valid")) || !properties.facebookAppId().equals(String.valueOf(data.get("app_id"))))
                throw invalid();
            Map<String, Object> me = http.get().uri(GRAPH + "/me?fields=id,name,email&access_token={token}", accessToken).retrieve().body(Map.class);
            if (me == null || me.get("id") == null || !me.get("id").equals(data.get("user_id"))) throw invalid();
            String email = (String) me.get("email");
            // Facebook only returns an email the user has confirmed.
            return new VerifiedIdentity("FACEBOOK", (String) me.get("id"), email, email != null, (String) me.get("name"));
        } catch (RestClientException ex) {
            log.warn("Facebook token check failed: {}", ex.getMessage());
            throw invalid();
        }
    }

    private JwtDecoder googleDecoder() {
        JwtDecoder decoder = google;
        if (decoder == null) {
            NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(GOOGLE_KEYS).jwsAlgorithm(SignatureAlgorithm.RS256).build();
            OAuth2TokenValidator<Jwt> issuedForUs = jwt -> GOOGLE_ISSUERS.contains(jwt.getClaimAsString("iss"))
                    && jwt.getAudience() != null && jwt.getAudience().contains(properties.googleClientId()) && jwt.getSubject() != null
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Not issued for this shop", null));
            nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), issuedForUs));
            google = decoder = nimbus;
        }
        return decoder;
    }

    private static BadRequestException invalid() {
        return new BadRequestException("INVALID_SOCIAL_TOKEN", "The sign-in could not be verified; try again");
    }
}
