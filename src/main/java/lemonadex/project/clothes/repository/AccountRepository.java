package lemonadex.project.clothes.repository;

import lemonadex.project.clothes.model.Account;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<Account> findByEmailIgnoreCaseAndDeletedFalse(String email);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<Account> findByIdAndDeletedFalse(UUID id);

    boolean existsByEmailIgnoreCaseAndDeletedFalse(String email);
}
