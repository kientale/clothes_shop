package lemonadex.project.clothes.features.account.repository;

import lemonadex.project.clothes.features.account.model.Account;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<Account> findByEmailIgnoreCaseAndDeletedFalse(String email);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<Account> findByIdAndDeletedFalse(UUID id);

    boolean existsByEmailIgnoreCaseAndDeletedFalse(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.deleted = false and exists (select r.id from a.roles r where r.code = 'ADMIN' and r.deleted = false) order by a.id")
    List<Account> lockAdministrators();
}
