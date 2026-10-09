package lemonadex.project.clothes.features.inventory.dto;

import jakarta.validation.constraints.*;
import lemonadex.project.clothes.features.inventory.model.*;
import java.util.UUID;

public final class InventoryRequests {
    private InventoryRequests() {}
    public record WarehouseRequest(@NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 500) String address, @NotNull WarehouseStatus status) {}
    public record StockRequest(@NotNull UUID warehouseId, @NotNull UUID productVariantId) {}
    /** Manual receipt/issue quantities are positive; direction comes from the movement type. */
    public record MovementRequest(@NotNull UUID warehouseId, @NotNull UUID productVariantId,
            @NotNull MovementType transactionType, @NotNull @Min(1) @Max(1000000000) Integer quantity,
            @NotBlank @Size(max = 2000) String note) {}
    public record AdjustmentRequest(@NotNull @Min(0) @Max(1000000000) Integer quantityOnHand,
            @NotBlank @Size(max = 2000) String reason) {}
}
