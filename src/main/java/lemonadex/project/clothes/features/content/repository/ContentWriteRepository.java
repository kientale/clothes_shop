package lemonadex.project.clothes.features.content.repository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
@Repository @RequiredArgsConstructor
public class ContentWriteRepository {
    private final EntityManager em;
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock() { em.createNativeQuery("SELECT true FROM pg_advisory_xact_lock(1279613007, 3)").getSingleResult(); }
}
