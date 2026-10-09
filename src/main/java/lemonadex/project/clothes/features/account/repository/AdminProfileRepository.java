package lemonadex.project.clothes.features.account.repository;

import lemonadex.project.clothes.features.account.model.AdminProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AdminProfileRepository extends JpaRepository<AdminProfile, UUID> {
    Optional<AdminProfile> findByAccountIdAndDeletedFalse(UUID accountId);
}
