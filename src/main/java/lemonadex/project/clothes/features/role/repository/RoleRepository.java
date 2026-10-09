package lemonadex.project.clothes.features.role.repository;

import lemonadex.project.clothes.features.role.model.Role;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID>, JpaSpecificationExecutor<Role> {
    Optional<Role> findByCodeAndDeletedFalse(String code);

    Optional<Role> findByIdAndDeletedFalse(UUID id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    List<Role> findAllByIdInAndDeletedFalse(Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Role r where r.id = :id and r.deleted = false")
    Optional<Role> lockById(@Param("id") UUID id);

    @Query(value = "select exists (select 1 from account_roles ar join accounts a on a.id = ar.account_id where ar.role_id = :id and a.deleted = false)", nativeQuery = true)
    boolean isAssigned(@Param("id") UUID id);
}
