package lemonadex.project.clothes.features.order.service;

import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.common.exception.*;
import lemonadex.project.clothes.common.util.ListFilters;
import lemonadex.project.clothes.features.inventory.service.InventoryService;
import lemonadex.project.clothes.features.order.dto.OrderRequests.*;
import lemonadex.project.clothes.features.order.dto.OrderResponses.*;
import lemonadex.project.clothes.features.order.model.*;
import lemonadex.project.clothes.features.order.repository.ReturnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class ReturnService {
    private final lemonadex.project.clothes.features.settings.service.SettingsService settings;
    private final ReturnRepository returns;
    private final OrderService orders;
    private final InventoryService inventory;

    public PageResponse<ReturnResponse> list(UUID orderId, UUID customerId, ReturnType type, ReturnStatus status, Instant from, Instant to, int page, int size) {
        return PageResponse.from(returns.findAll(ListFilters.where(null, new String[]{},
                ListFilters.values("orderId", orderId, "customerId", customerId, "requestType", type, "status", status),
                from, to), PageRequest.of(page, size, ListFilters.NEWEST)).map(this::response));
    }
    public ReturnResponse get(UUID id) { return response(required(id)); }

    @Transactional
    public ReturnResponse create(CreateReturnRequest request) {
        inventory.lock();
        PurchaseOrder order = orders.required(request.orderId());
        boolean carrierReturn = order.getOrderStatus() == OrderStatus.CANCELLED && order.getShippingStatus() == ShippingStatus.RETURNED;
        if (order.getOrderStatus() != OrderStatus.DELIVERED && order.getOrderStatus() != OrderStatus.COMPLETED && !carrierReturn)
            throw new ConflictException("ORDER_NOT_DELIVERED", "Returns require delivery or confirmed receipt of a carrier return");
        if (!carrierReturn) settings.requireReturns(order.getId());
        if (carrierReturn && request.requestType() == ReturnType.EXCHANGE)
            throw new BadRequestException("INVALID_EXCHANGE", "Carrier returns require a new order instead of an exchange");
        Map<UUID, OrderItem> items = items(order);
        Set<UUID> chosen = new HashSet<>();
        ReturnRequest result = new ReturnRequest(); result.setOrderId(order.getId()); result.setCustomerId(order.getCustomerId());
        result.setRequestType(request.requestType()); result.setReason(request.reason().strip()); result.setNote(OrderService.text(request.note()));
        result.setStatus(ReturnStatus.REQUESTED); result.getImages().addAll(request.images());
        for (ReturnItemRequest requested : request.items()) {
            if (!chosen.add(requested.orderItemId())) throw new BadRequestException("DUPLICATE_RETURN_ITEMS", "An order item can appear only once in a return");
            OrderItem original = items.get(requested.orderItemId());
            if (original == null) throw new BadRequestException("RETURN_ITEM_ORDER_MISMATCH", "All return items must belong to this order");
            if (returns.committedQuantity(original.getId()) + requested.quantity() > original.getQuantity())
                throw new ConflictException("RETURN_QUANTITY_EXCEEDED", "The return quantity exceeds the unreturned purchased quantity");
            ReturnItem item = new ReturnItem(); item.setRequest(result); item.setOrderItemId(original.getId()); item.setQuantity(requested.quantity());
            item.setReason(OrderService.text(requested.reason())); item.setConditionNote(OrderService.text(requested.conditionNote())); item.setResolution(request.requestType());
            if (request.requestType() == ReturnType.EXCHANGE) {
                if (requested.replacementVariantId() == null) throw new BadRequestException("EXCHANGE_VARIANT_REQUIRED", "Select a replacement variant for each exchange item");
                validateExchange(original, requested.replacementVariantId());
                item.setReplacementVariantId(requested.replacementVariantId());
            } else if (requested.replacementVariantId() != null) throw new BadRequestException("INVALID_REPLACEMENT", "Only exchanges accept replacement variants");
            result.getItems().add(item);
        }
        returns.saveAndFlush(result); return response(result);
    }

    @Transactional
    public ReturnResponse status(UUID id, ReturnStatusRequest request, UUID actor) {
        inventory.lock();
        ReturnRequest result = required(id);
        ReturnStatus current = result.getStatus(), next = request.status();
        if (current == next) return response(result);
        boolean valid = current == ReturnStatus.REQUESTED && Set.of(ReturnStatus.APPROVED, ReturnStatus.REJECTED, ReturnStatus.CANCELLED).contains(next)
                || current == ReturnStatus.APPROVED && Set.of(ReturnStatus.COMPLETED, ReturnStatus.CANCELLED).contains(next);
        if (!valid) throw new ConflictException("INVALID_RETURN_TRANSITION", "The return cannot enter this state");
        PurchaseOrder order = orders.required(result.getOrderId());
        Map<UUID, OrderItem> original = items(order);
        if (next == ReturnStatus.APPROVED) {
            result.setApprovedAt(Instant.now());
            if (result.getRequestType() == ReturnType.EXCHANGE) for (ReturnItem item : result.getItems()) {
                validateExchange(original.get(item.getOrderItemId()), item.getReplacementVariantId());
                item.setReplacementInventoryId(inventory.reserve(order.getWarehouseId(), item.getReplacementVariantId(), item.getQuantity(), "RETURN", result.getId(), actor));
            }
        }
        if (next == ReturnStatus.REJECTED) result.setRejectedAt(Instant.now());
        if (next == ReturnStatus.CANCELLED && current == ReturnStatus.APPROVED && result.getRequestType() == ReturnType.EXCHANGE)
            for (ReturnItem item : result.getItems()) inventory.release(item.getReplacementInventoryId(), item.getQuantity(), "RETURN", result.getId(), actor);
        if (next == ReturnStatus.COMPLETED) {
            // The carrier-return path already replenished all order lines; do not receive them twice.
            if (order.getShippingStatus() != ShippingStatus.RETURNED) for (ReturnItem item : result.getItems())
                inventory.restock(original.get(item.getOrderItemId()).getInventoryId(), item.getQuantity(), "RETURN", result.getId(), actor);
            if (result.getRequestType() == ReturnType.EXCHANGE) for (ReturnItem item : result.getItems())
                inventory.ship(item.getReplacementInventoryId(), item.getQuantity(), true, result.getId(), actor);
            result.setCompletedAt(Instant.now());
        }
        result.setStatus(next); result.setNote(OrderService.text(request.note())); returns.flush(); return response(result);
    }

    ReturnRequest required(UUID id) { return returns.findWithItemsById(id).orElseThrow(() -> new ResourceNotFoundException("Return request")); }
    private void validateExchange(OrderItem original, UUID replacementId) {
        var originalVariant = inventory.historicalVariant(original.getProductVariantId());
        var replacement = inventory.saleVariant(replacementId);
        if (!replacement.productId().equals(originalVariant.productId()) || replacement.price().compareTo(original.getUnitPrice()) != 0)
            throw new ConflictException("EXCHANGE_PRICE_MISMATCH", "An exchange must use a variant of the same product at the original unit price");
    }
    /** Prorate line and order discounts; shipping fees are excluded from merchandise refunds. */
    BigDecimal refundable(ReturnRequest result) {
        if (result.getRequestType() == ReturnType.EXCHANGE) return BigDecimal.ZERO;
        PurchaseOrder order = orders.required(result.getOrderId());
        Map<UUID, OrderItem> items = items(order);
        BigDecimal netLines = order.getItems().stream().map(OrderItem::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (netLines.signum() == 0) return BigDecimal.ZERO;
        BigDecimal merchandise = order.getSubtotal().subtract(order.getDiscountAmount());
        BigDecimal value = BigDecimal.ZERO;
        for (ReturnItem returned : result.getItems()) {
            OrderItem original = items.get(returned.getOrderItemId());
            BigDecimal line = original.getTotalAmount().multiply(BigDecimal.valueOf(returned.getQuantity()))
                    .multiply(merchandise).divide(BigDecimal.valueOf(original.getQuantity()).multiply(netLines), 2, RoundingMode.DOWN);
            value = value.add(line);
        }
        return value;
    }
    private Map<UUID, OrderItem> items(PurchaseOrder order) { return order.getItems().stream().collect(Collectors.toMap(OrderItem::getId, Function.identity())); }
    private ReturnResponse response(ReturnRequest r) {
        List<ReturnItemResponse> items = r.getItems().stream().map(i -> new ReturnItemResponse(i.getId(), i.getOrderItemId(), i.getQuantity(),
                i.getReason(), i.getConditionNote(), i.getResolution(), i.getReplacementVariantId(), i.getReplacementInventoryId())).toList();
        return new ReturnResponse(r.getId(), r.getOrderId(), r.getCustomerId(), r.getRequestType(), r.getReason(), r.getStatus(), r.getNote(), items,
                List.copyOf(r.getImages()), refundable(r), r.getCreatedAt(), r.getApprovedAt(), r.getRejectedAt(), r.getCompletedAt());
    }
}
