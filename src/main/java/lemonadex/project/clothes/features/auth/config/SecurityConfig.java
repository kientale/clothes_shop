package lemonadex.project.clothes.features.auth.config;

import lemonadex.project.clothes.common.exception.ApiErrorWriter;
import lemonadex.project.clothes.features.auth.config.*;
import lemonadex.project.clothes.features.auth.security.AccountJwtAuthenticationConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({SecurityProperties.class, CorsProperties.class})
public class SecurityConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecretKey jwtKey(SecurityProperties properties) {
        byte[] key = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) throw new IllegalArgumentException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        Duration ttl = properties.accessTokenTtl();
        if (ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("Access-token TTL must be positive and at most 24 hours");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> {
            try {
                String subject = jwt.getSubject();
                if (jwt.getExpiresAt() != null && jwt.getAudience() != null
                        && jwt.getAudience().contains(properties.audience()) && subject != null
                        && subject.equals(UUID.fromString(subject).toString())) {
                    return OAuth2TokenValidatorResult.success();
                }
            } catch (IllegalArgumentException ex) {
                // A malformed subject is an authentication failure, not a server error.
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid claims", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), requiredClaims));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccountJwtAuthenticationConverter converter,
                                           ApiErrorWriter errors) throws Exception {
        return http.csrf(csrf -> csrf.disable()).cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/password/forgot", "/api/v1/auth/password/reset").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        // Uploaded avatars are read by <img> tags, which cannot send a bearer token.
                        .requestMatchers(HttpMethod.GET, "/api/v1/files/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/content/articles", "/api/v1/content/articles/*", "/api/v1/content/store-policies/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/store/configuration", "/api/v1/store/catalog", "/api/v1/store/banners", "/api/v1/store/products", "/api/v1/store/products/*",
                                "/api/v1/store/collections", "/api/v1/store/collections/*", "/api/v1/store/sitemap.xml").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/store/orders/lookup").permitAll()
                        // Guest checkout and the shop's other public forms.
                        .requestMatchers(HttpMethod.POST, "/api/v1/store/orders", "/api/v1/store/orders/pay", "/api/v1/store/shipping/quote",
                                "/api/v1/store/stock-alerts").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/store/search/suggest", "/api/v1/auth/providers").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/email/verify", "/api/v1/auth/google", "/api/v1/auth/facebook").permitAll()
                        // Gateway and carrier callbacks: trusted only after their signature or shared secret checks out.
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments/vnpay/ipn").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/momo/ipn", "/api/v1/payments/*/return", "/api/v1/carriers/ghtk/webhook").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint((req, res, ex) -> errors.write(res, 401, "UNAUTHORIZED", "Authentication is required or the token is invalid"))
                        .accessDeniedHandler((req, res, ex) -> errors.write(res, 403, "FORBIDDEN", "Access denied")))
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((req, res, ex) -> errors.write(res, 401, "UNAUTHORIZED", "Authentication is required or the token is invalid"))
                        .accessDeniedHandler((req, res, ex) -> errors.write(res, 403, "FORBIDDEN", "Access denied")))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setExposedHeaders(List.of("X-Request-Id"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
