package lemonadex.project.clothes.features.catalog.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes catalog writes across application instances until the transaction ends. */
@Repository
@RequiredArgsConstructor
public class CatalogWriteRepository {
    private final EntityManager entityManager;

    /**
     * Checking references and then soft-deleting them must be atomic with other catalog writes.
     * A shared lock also protects category ancestry checks and variant restoration. Reads remain
     * concurrent, and PostgreSQL releases the lock automatically on commit or rollback.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock() {
        // Two-key advisory-lock namespace reserved for LemonadeX catalog management.
        entityManager.createNativeQuery("SELECT true FROM pg_advisory_xact_lock(1279613007, 1)")
                .getSingleResult();
    }
}
