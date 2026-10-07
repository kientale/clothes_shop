package lemonadex.project.clothes.repository;

import lemonadex.project.clothes.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByCodeAndDeletedFalse(String code);
}
