package lemonadex.project.clothes.features.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** One-time email verification links; rows hold only the SHA-256 of the emailed token. */
@Repository @RequiredArgsConstructor
public class EmailVerificationRepository {
    private final NamedParameterJdbcTemplate jdbc;

    /** Spends every open link of the account, so only the newest email works. */
    public void revokeOpen(UUID accountId, Instant now) {
        jdbc.update("UPDATE email_verification_tokens SET used_at = :now WHERE account_id = :account AND used_at IS NULL",
                new MapSqlParameterSource("account", accountId).addValue("now", Timestamp.from(now)));
    }

    public void create(UUID accountId, String tokenHash, Instant expiresAt) {
        jdbc.update("INSERT INTO email_verification_tokens(account_id, token_hash, expires_at) VALUES (:account, :hash, :expires)",
                new MapSqlParameterSource("account", accountId).addValue("hash", tokenHash).addValue("expires", Timestamp.from(expiresAt)));
    }

    public int countSince(UUID accountId, Instant since) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM email_verification_tokens WHERE account_id = :account AND created_at >= :since",
                new MapSqlParameterSource("account", accountId).addValue("since", Timestamp.from(since)), Integer.class);
        return count == null ? 0 : count;
    }

    /** Marks a valid link as used and returns its account; empty when unknown, used or expired. */
    public Optional<UUID> consume(String tokenHash, Instant now) {
        return jdbc.query("""
                UPDATE email_verification_tokens SET used_at = :now
                WHERE token_hash = :hash AND used_at IS NULL AND expires_at > :now
                RETURNING account_id
                """, new MapSqlParameterSource("hash", tokenHash).addValue("now", Timestamp.from(now)),
                (rs, i) -> rs.getObject("account_id", UUID.class)).stream().findFirst();
    }

    public void purge(Instant before) {
        jdbc.update("DELETE FROM email_verification_tokens WHERE expires_at < :before", Map.of("before", Timestamp.from(before)));
    }
}
