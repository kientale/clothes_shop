package lemonadex.project.clothes.features.content.repository;
import lemonadex.project.clothes.features.content.model.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface PolicyRepository extends JpaRepository<StorePolicy, UUID>, JpaSpecificationExecutor<StorePolicy> {
    Optional<StorePolicy> findByPolicyTypeAndStatus(PolicyType policyType, PolicyStatus status);
    @Query(value = "SELECT coalesce(max(version), 0) FROM store_policies WHERE policy_type = :type", nativeQuery = true)
    int lastVersion(String type);
    @Modifying @Query(value = "UPDATE store_policies SET status = 'ARCHIVED', updated_by = :actor WHERE policy_type = :type AND status = 'ACTIVE' AND NOT deleted", nativeQuery = true)
    void archiveActive(String type, UUID actor);
    @Modifying @Query(value = "UPDATE store_policies SET deleted = true WHERE id = :id", nativeQuery = true)
    void archive(UUID id);
}
