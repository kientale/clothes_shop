package lemonadex.project.clothes.features.customer.repository;

import lemonadex.project.clothes.features.customer.model.Customer;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {
    Optional<Customer> findByAccountIdAndDeletedFalse(UUID accountId);

    Optional<Customer> findByIdAndDeletedFalse(UUID id);

    /** Login emails of the active (not soft-deleted) accounts linked to the given profiles. */
    @Query(value = """
            SELECT c.id AS customerId, a.email AS email
            FROM customers c JOIN accounts a ON a.id = c.account_id
            WHERE c.id IN (:ids) AND a.deleted = FALSE
            """, nativeQuery = true)
    List<LinkedEmail> findLinkedEmails(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT status AS status, COUNT(*) AS total FROM customers WHERE deleted = FALSE GROUP BY status",
            nativeQuery = true)
    List<StatusCount> countByStatus();

    /**
     * Customer accounts that can receive a profile: not deleted, CUSTOMER role, no ADMIN role and no
     * profile row at all. Deleted profiles count too, because uq_customers_account still holds them.
     */
    @Query(value = """
            SELECT a.id AS id, a.email AS email, a.created_at AS createdAt
            FROM accounts a
            WHERE a.deleted = FALSE
              AND EXISTS (SELECT 1 FROM account_roles ar JOIN roles r ON r.id = ar.role_id
                          WHERE ar.account_id = a.id AND r.code = 'CUSTOMER' AND r.deleted = FALSE)
              AND NOT EXISTS (SELECT 1 FROM account_roles ar JOIN roles r ON r.id = ar.role_id
                              WHERE ar.account_id = a.id AND r.code = 'ADMIN' AND r.deleted = FALSE)
              AND NOT EXISTS (SELECT 1 FROM customers c WHERE c.account_id = a.id)
              AND (CAST(:term AS text) IS NULL OR lower(a.email) LIKE CAST(:term AS text) ESCAPE '\\')
            ORDER BY a.created_at DESC, a.id
            LIMIT :limit
            """, nativeQuery = true)
    List<LinkableAccount> findLinkableAccounts(@Param("term") String term, @Param("limit") int limit);

    interface LinkedEmail {
        UUID getCustomerId();

        String getEmail();
    }

    interface StatusCount {
        String getStatus();

        long getTotal();
    }

    interface LinkableAccount {
        UUID getId();

        String getEmail();

        Instant getCreatedAt();
    }
}
