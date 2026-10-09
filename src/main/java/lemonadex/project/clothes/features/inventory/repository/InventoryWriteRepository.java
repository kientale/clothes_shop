package lemonadex.project.clothes.features.inventory.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository @RequiredArgsConstructor
public class InventoryWriteRepository {
    private final EntityManager entityManager;
    /** Commerce writes share this transaction lock, including reservation and refund limits. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock() {
        entityManager.createNativeQuery("SELECT true FROM pg_advisory_xact_lock(1279613007, 2)").getSingleResult();
    }
}
