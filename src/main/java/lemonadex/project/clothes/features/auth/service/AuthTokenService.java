package lemonadex.project.clothes.features.auth.service;

import lemonadex.project.clothes.features.auth.dto.*;
import lemonadex.project.clothes.features.account.dto.*;
import lemonadex.project.clothes.features.auth.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuthTokenService {
    private final JwtEncoder encoder;
    private final SecurityProperties properties;
    private final Clock clock;

    public AuthResponse issue(AccountResponse account) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(properties.issuer())
                .audience(List.of(properties.audience())).subject(account.id().toString())
                .issuedAt(now).expiresAt(now.plus(properties.accessTokenTtl())).id(UUID.randomUUID().toString())
                .claim("roles", account.roles()).claim("permissions", account.permissions()).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthResponse(token, "Bearer", properties.accessTokenTtl().toSeconds(), account);
    }
}
