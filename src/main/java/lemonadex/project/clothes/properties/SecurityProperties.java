package lemonadex.project.clothes.properties;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Validated
@ConfigurationProperties("app.security")
public record SecurityProperties(@NotBlank String issuer, @NotBlank String audience,
        @NotBlank String secret, @NotNull Duration accessTokenTtl) {}
