package lemonadex.project.clothes.features.mail.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Sender of customer emails and the public shop address used in their links. Without spring.mail.host
 * the emails are only logged, which is enough for local development.
 */
@Validated
@ConfigurationProperties("app.mail")
public record MailProperties(@NotBlank @Email String from, @NotBlank String fromName,
        @NotBlank @Pattern(regexp = "https?://[^\\s]+") String storefrontUrl) {

    /** Shop address without a trailing slash. */
    public String storefront() {
        return storefrontUrl.endsWith("/") ? storefrontUrl.substring(0, storefrontUrl.length() - 1) : storefrontUrl;
    }

    @Configuration
    @EnableConfigurationProperties(MailProperties.class)
    static class Registration {}
}
