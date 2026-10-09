package lemonadex.project.clothes.features.storefront.repository;

import lemonadex.project.clothes.features.storefront.model.StorefrontRows.AddressRow;
import lemonadex.project.clothes.features.storefront.model.StorefrontRows.ProfileRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/** The signed-in customer's own data: profile, password, saved addresses and wishlist. */
@Repository @RequiredArgsConstructor
public class CustomerAccountRepository {
    private final NamedParameterJdbcTemplate jdbc;

    private static final String ADDRESS_COLUMNS = "id, recipient_name, phone, address_line, ward, district, province, is_default";

    /** Profile of an active account with its active customer record. */
    public Optional<ProfileRow> profile(UUID accountId) {
        return jdbc.query("""
                SELECT a.id AS account_id, c.id AS customer_id, a.email, a.password_hash, c.full_name, c.phone, c.gender, c.date_of_birth
                FROM accounts a JOIN customers c ON c.account_id = a.id
                WHERE a.id = :id AND NOT a.deleted AND a.status = 'ACTIVE' AND NOT c.deleted AND c.status = 'ACTIVE'
                """, Map.of("id", accountId), (rs, i) -> {
            Date birth = rs.getDate("date_of_birth");
            return new ProfileRow(uuid(rs, "account_id"), uuid(rs, "customer_id"), rs.getString("email"), rs.getString("password_hash"),
                    rs.getString("full_name"), rs.getString("phone"), rs.getString("gender"), birth == null ? null : birth.toLocalDate());
        }).stream().findFirst();
    }

    public void updateProfile(UUID customerId, String fullName, String phone, String gender, LocalDate dateOfBirth) {
        jdbc.update("UPDATE customers SET full_name = :name, phone = :phone, gender = :gender, date_of_birth = :birth WHERE id = :id",
                new MapSqlParameterSource("id", customerId).addValue("name", fullName).addValue("phone", phone)
                        .addValue("gender", gender).addValue("birth", dateOfBirth == null ? null : Date.valueOf(dateOfBirth)));
    }

    public void updatePassword(UUID accountId, String passwordHash) {
        jdbc.update("UPDATE accounts SET password_hash = :hash WHERE id = :id", Map.of("id", accountId, "hash", passwordHash));
    }

    public List<AddressRow> addresses(UUID customerId) {
        return jdbc.query("SELECT " + ADDRESS_COLUMNS + " FROM customer_addresses WHERE customer_id = :customer ORDER BY is_default DESC, created_at, id",
                Map.of("customer", customerId), CustomerAccountRepository::address);
    }

    public Optional<AddressRow> address(UUID customerId, UUID id) {
        return jdbc.query("SELECT " + ADDRESS_COLUMNS + " FROM customer_addresses WHERE customer_id = :customer AND id = :id",
                Map.of("customer", customerId, "id", id), CustomerAccountRepository::address).stream().findFirst();
    }

    public int countAddresses(UUID customerId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM customer_addresses WHERE customer_id = :customer", Map.of("customer", customerId), Integer.class);
        return count == null ? 0 : count;
    }

    public UUID insertAddress(UUID customerId, AddressRow row) {
        return jdbc.queryForObject("""
                INSERT INTO customer_addresses(customer_id, recipient_name, phone, address_line, ward, district, province, is_default)
                VALUES (:customer, :name, :phone, :line, :ward, :district, :province, FALSE) RETURNING id
                """, addressParams(customerId, row), UUID.class);
    }

    public void updateAddress(UUID customerId, UUID id, AddressRow row) {
        jdbc.update("""
                UPDATE customer_addresses SET recipient_name = :name, phone = :phone, address_line = :line, ward = :ward,
                       district = :district, province = :province
                WHERE customer_id = :customer AND id = :id
                """, addressParams(customerId, row).addValue("id", id));
    }

    /** Makes one address the default; clears the old one first because of the one-default index. */
    public void makeDefault(UUID customerId, UUID id) {
        jdbc.update("UPDATE customer_addresses SET is_default = FALSE WHERE customer_id = :customer AND is_default AND id <> :id",
                Map.of("customer", customerId, "id", id));
        jdbc.update("UPDATE customer_addresses SET is_default = TRUE WHERE customer_id = :customer AND id = :id",
                Map.of("customer", customerId, "id", id));
    }

    public void deleteAddress(UUID customerId, UUID id) {
        jdbc.update("DELETE FROM customer_addresses WHERE customer_id = :customer AND id = :id", Map.of("customer", customerId, "id", id));
    }

    /** Oldest remaining address, to promote when the default one is deleted. */
    public Optional<UUID> firstAddress(UUID customerId) {
        return jdbc.query("SELECT id FROM customer_addresses WHERE customer_id = :customer ORDER BY created_at, id LIMIT 1",
                Map.of("customer", customerId), (rs, i) -> uuid(rs, "id")).stream().findFirst();
    }

    public boolean productOnSale(UUID productId) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM products WHERE id = :id AND NOT deleted AND status = 'ACTIVE')",
                Map.of("id", productId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public void addToWishlist(UUID customerId, UUID productId) {
        jdbc.update("INSERT INTO wishlist_items(customer_id, product_id) VALUES (:customer, :product) ON CONFLICT DO NOTHING",
                Map.of("customer", customerId, "product", productId));
    }

    public void removeFromWishlist(UUID customerId, UUID productId) {
        jdbc.update("DELETE FROM wishlist_items WHERE customer_id = :customer AND product_id = :product",
                Map.of("customer", customerId, "product", productId));
    }

    public int countWishlist(UUID customerId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM wishlist_items WHERE customer_id = :customer", Map.of("customer", customerId), Integer.class);
        return count == null ? 0 : count;
    }

    /** Ids only, so the shop can mark hearts on any product list. */
    public List<UUID> wishlistIds(UUID customerId) {
        return jdbc.query("SELECT product_id FROM wishlist_items WHERE customer_id = :customer ORDER BY created_at DESC",
                Map.of("customer", customerId), (rs, i) -> uuid(rs, "product_id"));
    }

    private static MapSqlParameterSource addressParams(UUID customerId, AddressRow row) {
        return new MapSqlParameterSource("customer", customerId).addValue("name", row.recipientName()).addValue("phone", row.phone())
                .addValue("line", row.addressLine()).addValue("ward", row.ward()).addValue("district", row.district())
                .addValue("province", row.province());
    }

    private static AddressRow address(ResultSet rs, int i) throws SQLException {
        return new AddressRow(uuid(rs, "id"), rs.getString("recipient_name"), rs.getString("phone"), rs.getString("address_line"),
                rs.getString("ward"), rs.getString("district"), rs.getString("province"), rs.getBoolean("is_default"));
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }
}
