package lemonadex.project.clothes.features.inventory.service;

import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.common.exception.*;
import lemonadex.project.clothes.common.util.*;
import lemonadex.project.clothes.features.catalog.repository.CatalogWriteRepository;
import lemonadex.project.clothes.features.inventory.dto.InventoryRequests.*;
import lemonadex.project.clothes.features.inventory.dto.InventoryResponses.*;
import lemonadex.project.clothes.features.inventory.model.*;
import lemonadex.project.clothes.features.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {
    private final WarehouseRepository warehouses;
    private final InventoryRepository stocks;
    private final InventoryTransactionRepository movements;
    private final CatalogWriteRepository catalogWrites;
    private final InventoryWriteRepository commerceWrites;

    /** Always acquire catalog before commerce so checkout and catalog deletion cannot race. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock() { catalogWrites.lock(); commerceWrites.lock(); }

    public PageResponse<WarehouseResponse> warehouses(String search, WarehouseStatus status, int page, int size) {
        return PageResponse.from(warehouses.findAll(ListFilters.where(search, new String[]{"name", "address"},
                ListFilters.values("status", status), null, null), PageRequest.of(page, size, ListFilters.NEWEST)).map(this::warehouseResponse));
    }

    public WarehouseResponse warehouse(UUID id) { return warehouseResponse(requiredWarehouse(id)); }

    @Transactional
    public WarehouseResponse createWarehouse(WarehouseRequest request) {
        lock();
        Warehouse warehouse = new Warehouse();
        apply(warehouse, request);
        return warehouseResponse(warehouses.saveAndFlush(warehouse));
    }

    @Transactional
    public WarehouseResponse updateWarehouse(UUID id, WarehouseRequest request) {
        lock();
        Warehouse warehouse = requiredWarehouse(id);
        if (request.status() == WarehouseStatus.INACTIVE && stocks.existsByWarehouseIdAndQuantityReservedGreaterThan(id, 0))
            throw new ConflictException("WAREHOUSE_HAS_RESERVATIONS", "Fulfill or cancel reserved orders before disabling the warehouse");
        apply(warehouse, request);
        warehouses.flush();
        return warehouseResponse(warehouse);
    }

    @Transactional
    public void deleteWarehouse(UUID id) {
        lock();
        Warehouse warehouse = requiredWarehouse(id);
        if (stocks.existsByWarehouseId(id)) throw new ConflictException("WAREHOUSE_IN_USE", "Archive the warehouse instead; stock and ledger references must be retained");
        warehouses.delete(warehouse);
        warehouses.flush();
    }

    private void apply(Warehouse warehouse, WarehouseRequest request) {
        warehouse.setName(request.name().strip()); warehouse.setAddress(request.address().strip()); warehouse.setStatus(request.status());
    }

    public PageResponse<StockResponse> list(String search, UUID warehouseId, UUID variantId, Boolean lowStock, int page, int size) {
        Specification<Inventory> filter = ListFilters.where(null, new String[]{},
                ListFilters.values("warehouseId", warehouseId, "productVariantId", variantId), null, null);
        if (search != null && !search.isBlank()) {
            List<UUID> ids = stocks.matchingVariants(SearchUtils.contains(search));
            filter = filter.and((root, query, cb) -> ids.isEmpty() ? cb.disjunction() : root.get("productVariantId").in(ids));
        }
        if (Boolean.TRUE.equals(lowStock)) filter = filter.and((root, query, cb) ->
                cb.lessThanOrEqualTo(cb.diff(root.<Integer>get("quantityOnHand"), root.<Integer>get("quantityReserved")), 5));
        return PageResponse.from(stocks.findAll(filter, PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id")))).map(this::stockResponse));
    }

    public StockResponse get(UUID id) { return stockResponse(requiredStock(id)); }

    @Transactional
    public StockResponse create(StockRequest request) {
        lock();
        activeWarehouse(request.warehouseId());
        stockVariant(request.productVariantId());
        if (stocks.findByWarehouseIdAndProductVariantId(request.warehouseId(), request.productVariantId()).isPresent())
            throw new ConflictException("STOCK_EXISTS", "This warehouse already has a stock record for the variant");
        Inventory stock = new Inventory();
        stock.setWarehouseId(request.warehouseId()); stock.setProductVariantId(request.productVariantId());
        return stockResponse(stocks.saveAndFlush(stock));
    }

    @Transactional
    public MovementResponse createMovement(MovementRequest request, UUID actor) {
        lock();
        if (request.transactionType() != MovementType.RECEIPT && request.transactionType() != MovementType.ISSUE)
            throw new BadRequestException("INVALID_MOVEMENT_TYPE", "Only receipt and issue are accepted here; use adjustments for stock counts");
        activeWarehouse(request.warehouseId());
        Inventory stock = byCombination(request.warehouseId(), request.productVariantId());
        if (request.transactionType() == MovementType.RECEIPT) stockVariant(request.productVariantId());
        int delta = request.transactionType() == MovementType.RECEIPT ? request.quantity() : -request.quantity();
        return movementResponse(change(stock, request.transactionType(), delta, 0, delta, "MANUAL", null, request.note(), actor));
    }

    @Transactional
    public StockResponse adjust(UUID id, AdjustmentRequest request, UUID actor) {
        lock();
        Inventory stock = requiredStock(id);
        activeWarehouse(stock.getWarehouseId());
        int delta = request.quantityOnHand() - stock.getQuantityOnHand();
        if (delta == 0) throw new BadRequestException("NO_STOCK_CHANGE", "The counted quantity is already recorded");
        change(stock, MovementType.ADJUSTMENT, delta, 0, delta, "MANUAL", null, request.reason(), actor);
        return stockResponse(stock);
    }

    public PageResponse<MovementResponse> movements(UUID warehouseId, UUID variantId, MovementType type, UUID referenceId,
                                                  Instant from, Instant to, int page, int size) {
        return PageResponse.from(movements.findAll(ListFilters.where(null, new String[]{},
                ListFilters.values("warehouseId", warehouseId, "productVariantId", variantId, "transactionType", type, "referenceId", referenceId),
                from, to), PageRequest.of(page, size, ListFilters.NEWEST)).map(this::movementResponse));
    }

    public MovementResponse movement(UUID id) {
        return movementResponse(movements.findById(id).orElseThrow(() -> new ResourceNotFoundException("Inventory transaction")));
    }

    public SaleVariant saleVariant(UUID id) {
        var variant = stocks.variant(id).orElseThrow(() -> new ResourceNotFoundException("Product variant"));
        if (!variant.getActive()) throw new ConflictException("VARIANT_UNAVAILABLE", "The product variant is not available for sale");
        return new SaleVariant(variant.getId(), variant.getProductId(), variant.getSku(), variant.getProductName(), variant.getColorName(), variant.getSizeName(), variant.getPrice());
    }

    public SaleVariant historicalVariant(UUID id) {
        var variant = stocks.variant(id).orElseThrow(() -> new ResourceNotFoundException("Product variant"));
        return new SaleVariant(variant.getId(), variant.getProductId(), variant.getSku(), variant.getProductName(), variant.getColorName(), variant.getSizeName(), variant.getPrice());
    }

    /** Reserve stock and return its durable allocation ID. Called only within a commerce transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID reserve(UUID warehouseId, UUID variantId, int quantity, String referenceType, UUID reference, UUID actor) {
        lock(); activeWarehouse(warehouseId); saleVariant(variantId);
        Inventory stock = byCombination(warehouseId, variantId);
        change(stock, MovementType.RESERVE, 0, quantity, quantity, referenceType, reference, "Stock reserved", actor);
        return stock.getId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void release(UUID stockId, int quantity, String referenceType, UUID reference, UUID actor) {
        lock(); change(requiredStock(stockId), MovementType.RELEASE, 0, -quantity, -quantity, referenceType, reference, "Reservation released", actor);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void ship(UUID stockId, int quantity, boolean exchange, UUID reference, UUID actor) {
        lock(); change(requiredStock(stockId), exchange ? MovementType.EXCHANGE : MovementType.SHIPMENT, -quantity, -quantity,
                -quantity, exchange ? "RETURN" : "ORDER", reference, "Reserved stock dispatched", actor);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void restock(UUID stockId, int quantity, String referenceType, UUID reference, UUID actor) {
        lock(); change(requiredStock(stockId), MovementType.RETURN, quantity, 0, quantity, referenceType, reference, "Returned goods received", actor);
    }

    private InventoryTransaction change(Inventory stock, MovementType type, int onHandDelta, int reservedDelta, int quantity,
                                        String referenceType, UUID referenceId, String note, UUID actor) {
        long onHand = (long) stock.getQuantityOnHand() + onHandDelta;
        long reserved = (long) stock.getQuantityReserved() + reservedDelta;
        if (onHand < 0 || reserved < 0 || reserved > onHand)
            throw new ConflictException("INSUFFICIENT_STOCK", "The movement would consume unavailable or reserved stock");
        if (onHand > 1000000000L || reserved > 1000000000L) throw new BadRequestException("STOCK_LIMIT_EXCEEDED", "Stock quantities cannot exceed one billion");
        InventoryTransaction event = new InventoryTransaction();
        event.setWarehouseId(stock.getWarehouseId()); event.setProductVariantId(stock.getProductVariantId()); event.setTransactionType(type);
        event.setQuantity(quantity); event.setQuantityBefore(stock.getQuantityOnHand()); event.setQuantityAfter((int) onHand);
        event.setReservedBefore(stock.getQuantityReserved()); event.setReservedAfter((int) reserved);
        event.setReferenceType(referenceType); event.setReferenceId(referenceId); event.setNote(note == null ? null : note.strip()); event.setCreatedBy(actor);
        stock.setQuantityOnHand((int) onHand); stock.setQuantityReserved((int) reserved);
        stocks.flush();
        return movements.saveAndFlush(event);
    }

    private void stockVariant(UUID id) {
        var variant = stocks.variant(id).orElseThrow(() -> new ResourceNotFoundException("Product variant"));
        if (!variant.getVisible()) throw new ResourceNotFoundException("Product variant");
    }

    private Warehouse requiredWarehouse(UUID id) { return warehouses.findByIdAndDeletedFalse(id).orElseThrow(() -> new ResourceNotFoundException("Warehouse")); }
    private void activeWarehouse(UUID id) {
        if (requiredWarehouse(id).getStatus() != WarehouseStatus.ACTIVE) throw new ConflictException("WAREHOUSE_INACTIVE", "The warehouse is inactive");
    }
    private Inventory requiredStock(UUID id) {
        if (id == null) throw new ConflictException("ORDER_STOCK_UNAVAILABLE", "This historical order has no warehouse allocation");
        return stocks.findById(id).orElseThrow(() -> new ResourceNotFoundException("Inventory"));
    }
    private Inventory byCombination(UUID warehouse, UUID variant) {
        return stocks.findByWarehouseIdAndProductVariantId(warehouse, variant).orElseThrow(() -> new ResourceNotFoundException("Inventory"));
    }
    private WarehouseResponse warehouseResponse(Warehouse w) { return new WarehouseResponse(w.getId(), w.getName(), w.getAddress(), w.getStatus(), w.getCreatedAt(), w.getUpdatedAt()); }
    private StockResponse stockResponse(Inventory s) {
        var variant = stocks.variant(s.getProductVariantId()).orElseThrow(() -> new ResourceNotFoundException("Product variant"));
        return new StockResponse(s.getId(), s.getWarehouseId(), requiredWarehouse(s.getWarehouseId()).getName(), s.getProductVariantId(),
                variant.getSku(), variant.getProductName(), s.getQuantityOnHand(), s.getQuantityReserved(), s.getQuantityOnHand() - s.getQuantityReserved(), s.getUpdatedAt());
    }
    private MovementResponse movementResponse(InventoryTransaction t) {
        return new MovementResponse(t.getId(), t.getWarehouseId(), t.getProductVariantId(), t.getTransactionType(), t.getQuantity(),
                t.getQuantityBefore(), t.getQuantityAfter(), t.getReservedBefore(), t.getReservedAfter(), t.getReferenceType(),
                t.getReferenceId(), t.getNote(), t.getCreatedBy(), t.getCreatedAt());
    }
}
