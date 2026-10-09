package lemonadex.project.clothes.features.payment.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * One row in payment_transactions per attempt at a gateway. The transaction code is the reference sent to the
 * gateway, so its callback finds the attempt; the row moves from PENDING to PAID or FAILED once.
 */
@Repository @RequiredArgsConstructor
public class GatewayTransactionRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public record Attempt(UUID id, UUID paymentId, BigDecimal amount, String status) {}

    public void create(UUID paymentId, String reference, String provider, BigDecimal amount) {
        jdbc.update("""
                INSERT INTO payment_transactions(payment_id, transaction_code, provider, amount, status)
                VALUES (:payment, :reference, :provider, :amount, 'PENDING')
                """, new MapSqlParameterSource("payment", paymentId).addValue("reference", reference)
                .addValue("provider", provider).addValue("amount", amount));
    }

    /** The attempt, locked until the transaction ends so two callbacks cannot settle it twice. */
    public Optional<Attempt> lock(String provider, String reference) {
        return jdbc.query("""
                SELECT id, payment_id, amount, status FROM payment_transactions
                WHERE provider = :provider AND transaction_code = :reference FOR UPDATE
                """, new MapSqlParameterSource("provider", provider).addValue("reference", reference),
                (rs, i) -> new Attempt(rs.getObject("id", UUID.class), rs.getObject("payment_id", UUID.class),
                        rs.getBigDecimal("amount"), rs.getString("status"))).stream().findFirst();
    }

    public void finish(UUID id, String status, String providerTransactionId, String rawJson) {
        jdbc.update("""
                UPDATE payment_transactions SET status = :status, provider_transaction_id = :providerId, raw_response = CAST(:raw AS jsonb)
                WHERE id = :id
                """, new MapSqlParameterSource("id", id).addValue("status", status)
                .addValue("providerId", providerTransactionId).addValue("raw", rawJson));
    }
}
