package lemonadex.project.clothes.features.role.repository;

import lemonadex.project.clothes.features.role.model.Permission;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface PermissionRepository extends JpaRepository<Permission, UUID>, JpaSpecificationExecutor<Permission> {
    List<Permission> findAllByIdInAndDeletedFalse(Collection<UUID> ids);
}
