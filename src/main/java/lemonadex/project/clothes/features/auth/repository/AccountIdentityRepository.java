package lemonadex.project.clothes.features.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

/** Google/Facebook identities linked to accounts. */
@Repository @RequiredArgsConstructor
public class AccountIdentityRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public Optional<UUID> accountOf(String provider, String subject) {
        return jdbc.query("SELECT account_id FROM account_identities WHERE provider = :provider AND subject = :subject",
                new MapSqlParameterSource("provider", provider).addValue("subject", subject),
                (rs, i) -> rs.getObject("account_id", UUID.class)).stream().findFirst();
    }

    /** Links the identity; a concurrent link of the same identity is ignored. */
    public void link(UUID accountId, String provider, String subject, String email) {
        jdbc.update("""
                INSERT INTO account_identities(account_id, provider, subject, email) VALUES (:account, :provider, :subject, :email)
                ON CONFLICT (provider, subject) DO NOTHING
                """, new MapSqlParameterSource("account", accountId).addValue("provider", provider)
                .addValue("subject", subject).addValue("email", email));
    }
}
