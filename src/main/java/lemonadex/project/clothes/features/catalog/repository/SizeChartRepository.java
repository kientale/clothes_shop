package lemonadex.project.clothes.features.catalog.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Size charts stored as JSON, one per product. */
@Repository @RequiredArgsConstructor
public class SizeChartRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public Optional<String> find(UUID productId) {
        return jdbc.query("SELECT chart::text AS chart FROM product_size_charts WHERE product_id = :id", Map.of("id", productId),
                (rs, i) -> rs.getString("chart")).stream().findFirst();
    }

    public void save(UUID productId, String chartJson) {
        jdbc.update("""
                INSERT INTO product_size_charts(product_id, chart) VALUES (:id, CAST(:chart AS jsonb))
                ON CONFLICT (product_id) DO UPDATE SET chart = EXCLUDED.chart
                """, new MapSqlParameterSource("id", productId).addValue("chart", chartJson));
    }

    public void delete(UUID productId) {
        jdbc.update("DELETE FROM product_size_charts WHERE product_id = :id", Map.of("id", productId));
    }
}
