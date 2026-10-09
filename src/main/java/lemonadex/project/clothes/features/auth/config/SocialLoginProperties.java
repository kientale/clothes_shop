package lemonadex.project.clothes.features.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Google and Facebook sign-in. A provider is offered only when its keys are set.
 *
 * @param googleClientId    OAuth client id of the Google Identity Services button (ID tokens must be issued to it)
 * @param facebookAppId     Facebook app id used by the JavaScript SDK
 * @param facebookAppSecret secret of that app, used server-side to check the access token
 */
@ConfigurationProperties("app.social")
public record SocialLoginProperties(String googleClientId, String facebookAppId, String facebookAppSecret) {
    public SocialLoginProperties {
        googleClientId = blankToNull(googleClientId);
        facebookAppId = blankToNull(facebookAppId);
        facebookAppSecret = blankToNull(facebookAppSecret);
    }

    public boolean googleEnabled() {
        return googleClientId != null;
    }

    public boolean facebookEnabled() {
        return facebookAppId != null && facebookAppSecret != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    @Configuration
    @EnableConfigurationProperties(SocialLoginProperties.class)
    static class Registration {}
}
