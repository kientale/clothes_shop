package lemonadex.project.clothes.features.inventory.repository;

import lemonadex.project.clothes.features.inventory.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
import java.math.BigDecimal;

public interface InventoryRepository extends JpaRepository<Inventory, UUID>, JpaSpecificationExecutor<Inventory> {
    Optional<Inventory> findByWarehouseIdAndProductVariantId(UUID warehouseId, UUID productVariantId);
    boolean existsByWarehouseId(UUID warehouseId);
    boolean existsByWarehouseIdAndQuantityReservedGreaterThan(UUID warehouseId, int quantity);
    @Query(value = """
        SELECT v.id, v.product_id AS "productId", v.sku, p.name AS "productName", c.name AS "colorName", s.name AS "sizeName", v.price,
               (NOT v.deleted AND NOT p.deleted AND NOT c.deleted AND NOT s.deleted) AS visible,
               (NOT v.deleted AND NOT p.deleted AND v.status = 'ACTIVE' AND p.status = 'ACTIVE'
                AND NOT c.deleted AND NOT s.deleted AND c.status = 'ACTIVE' AND s.status = 'ACTIVE') AS active
        FROM product_variants v JOIN products p ON p.id = v.product_id
        JOIN colors c ON c.id = v.color_id JOIN sizes s ON s.id = v.size_id WHERE v.id = :id
        """, nativeQuery = true)
    Optional<VariantReference> variant(@Param("id") UUID id);
    interface VariantReference {
        UUID getId(); UUID getProductId(); String getSku(); String getProductName();
        String getColorName(); String getSizeName(); BigDecimal getPrice(); boolean getActive(); boolean getVisible();
    }
    @Query(value = "SELECT v.id FROM product_variants v JOIN products p ON p.id = v.product_id WHERE lower(v.sku) LIKE :term ESCAPE '\\' OR lower(p.name) LIKE :term ESCAPE '\\'", nativeQuery = true)
    List<UUID> matchingVariants(@Param("term") String term);
}
