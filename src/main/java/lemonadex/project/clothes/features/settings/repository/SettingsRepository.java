package lemonadex.project.clothes.features.settings.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class SettingsRepository {
    private final NamedParameterJdbcTemplate jdbc;
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock() {
        jdbc.query("SELECT pg_advisory_xact_lock(1279613007, 1)", Map.of(), rs -> {});
        jdbc.query("SELECT pg_advisory_xact_lock(1279613007, 2)", Map.of(), rs -> {});
    }
    public Map<String, Object> setting(String group) {
        return jdbc.queryForMap("SELECT * FROM system_settings WHERE setting_group = :group AND setting_key = 'CONFIG'", Map.of("group", group));
    }
    public Map<String, Object> updateSetting(String group, String value, long revision, UUID actor) {
        return jdbc.queryForMap("""
            UPDATE system_settings SET setting_value = :value, revision = revision + 1, updated_by = :actor
            WHERE setting_group = :group AND setting_key = 'CONFIG' AND revision = :revision RETURNING *
            """, new MapSqlParameterSource("value", value).addValue("actor", actor).addValue("group", group).addValue("revision", revision));
    }
    // The table name is selected internally; callers and HTTP requests never supply SQL identifiers.
    private String table(boolean payment) { return payment ? "payment_methods" : "shipping_methods"; }
    public List<Map<String, Object>> methods(boolean payment) {
        return jdbc.queryForList("SELECT * FROM " + table(payment) + " WHERE NOT deleted ORDER BY code", Map.of());
    }
    public Optional<Map<String, Object>> method(boolean payment, UUID id) {
        return jdbc.queryForList("SELECT * FROM " + table(payment) + " WHERE id = :id AND NOT deleted", Map.of("id", id)).stream().findFirst();
    }
    public Optional<Map<String, Object>> shippingMethod(String code) {
        return jdbc.queryForList("SELECT * FROM shipping_methods WHERE code = :code AND NOT deleted AND is_enabled", Map.of("code", code)).stream().findFirst();
    }
    public boolean codeExists(boolean payment, String code) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM " + table(payment) + " WHERE code = :code)", Map.of("code", code), Boolean.class));
    }
    public Map<String, Object> createPayment(Map<String, Object> p) {
        return jdbc.queryForMap("""
            INSERT INTO payment_methods(code, name, provider, is_enabled, configuration)
            VALUES (:code, :name, 'MANUAL', :enabled, CAST(:configuration AS jsonb)) RETURNING *
            """, p);
    }
    public Map<String, Object> updatePayment(Map<String, Object> p) {
        return jdbc.queryForMap("""
            UPDATE payment_methods SET name = :name, is_enabled = :enabled, configuration = CAST(:configuration AS jsonb), revision = revision + 1
            WHERE id = :id AND revision = :revision AND NOT deleted RETURNING *
            """, p);
    }
    public Map<String, Object> createShipping(Map<String, Object> p) {
        return jdbc.queryForMap("""
            INSERT INTO shipping_methods(code, name, provider, base_fee, estimated_days, is_enabled)
            VALUES (:code, :name, :provider, :fee, :days, :enabled) RETURNING *
            """, new MapSqlParameterSource(p));
    }
    public Map<String, Object> updateShipping(Map<String, Object> p) {
        return jdbc.queryForMap("""
            UPDATE shipping_methods SET name = :name, provider = :provider, base_fee = :fee, estimated_days = :days,
                is_enabled = :enabled, revision = revision + 1
            WHERE id = :id AND revision = :revision AND NOT deleted RETURNING *
            """, new MapSqlParameterSource(p));
    }
    public Map<String, Object> archive(boolean payment, UUID id) {
        return jdbc.queryForMap("UPDATE " + table(payment) + " SET deleted = TRUE, is_enabled = FALSE, revision = revision + 1 WHERE id = :id RETURNING *", Map.of("id", id));
    }
    public void audit(String action, String type, UUID id, UUID actor, String before, String after) {
        jdbc.update("""
            INSERT INTO audit_logs(action, entity_type, entity_id, account_id, old_data, new_data)
            VALUES (:action, :type, :id, :actor, CAST(:before AS jsonb), CAST(:after AS jsonb))
            """, new MapSqlParameterSource("action", action).addValue("type", type).addValue("id", id)
                .addValue("actor", actor).addValue("before", before).addValue("after", after));
    }
    public List<Map<String, Object>> history(String type, int page, int size) {
        return jdbc.queryForList("SELECT * FROM audit_logs WHERE entity_type = :type ORDER BY created_at DESC, id DESC LIMIT :size OFFSET :offset",
                Map.of("type", type, "size", size, "offset", (long) page * size));
    }
    public long historyCount(String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE entity_type = :type", Map.of("type", type), Long.class);
    }
    public boolean timezoneExists(String zone) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_timezone_names WHERE name = :zone)", Map.of("zone", zone), Boolean.class));
    }
    public java.time.Instant deliveredAt(UUID orderId) {
        return jdbc.queryForObject("SELECT MAX(delivered_at) FROM shipments WHERE order_id = :order", Map.of("order", orderId),
                (rs, row) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant());
    }
}
