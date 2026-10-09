package lemonadex.project.clothes.features.inventory.dto;

import lemonadex.project.clothes.features.inventory.model.*;
import java.time.Instant;
import java.util.UUID;
import java.math.BigDecimal;

public final class InventoryResponses {
    private InventoryResponses() {}
    public record WarehouseResponse(UUID id, String name, String address, WarehouseStatus status, Instant createdAt, Instant updatedAt) {}
    public record StockResponse(UUID id, UUID warehouseId, String warehouseName, UUID productVariantId, String sku, String productName,
            int quantityOnHand, int quantityReserved, int quantityAvailable, Instant updatedAt) {}
    public record MovementResponse(UUID id, UUID warehouseId, UUID productVariantId, MovementType transactionType,
            int quantity, int quantityBefore, int quantityAfter, int reservedBefore, int reservedAfter,
            String referenceType, UUID referenceId, String note, UUID createdBy, Instant createdAt) {}
    /** Immutable saleable-variant lookup for checkout and exchanges; never exposes a persistence entity. */
    public record SaleVariant(UUID id, UUID productId, String sku, String productName, String colorName, String sizeName, BigDecimal price) {}
}
