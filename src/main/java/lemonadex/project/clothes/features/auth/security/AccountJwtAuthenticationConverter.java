package lemonadex.project.clothes.features.auth.security;

import lemonadex.project.clothes.features.account.service.AccountAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class AccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final AccountAccessService access;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            var identity = access.activeIdentity(UUID.fromString(jwt.getSubject()));
            var authorities = Stream.concat(identity.roles().stream().map(role -> "ROLE_" + role),
                    identity.permissions().stream()).distinct().map(SimpleGrantedAuthority::new).toList();
            return new JwtAuthenticationToken(jwt, authorities, identity.id().toString());
        } catch (BadCredentialsException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "Account unavailable");
        }
    }
}
