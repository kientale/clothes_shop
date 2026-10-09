package lemonadex.project.clothes.features.storefront.service;

import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.common.exception.ConflictException;
import lemonadex.project.clothes.common.exception.ResourceNotFoundException;
import lemonadex.project.clothes.features.order.dto.OrderRequests.*;
import lemonadex.project.clothes.features.order.dto.OrderResponses.ItemResponse;
import lemonadex.project.clothes.features.order.dto.OrderResponses.OrderResponse;
import lemonadex.project.clothes.features.order.dto.OrderResponses.ReturnResponse;
import lemonadex.project.clothes.features.order.model.ReturnStatus;
import lemonadex.project.clothes.features.order.service.OrderService;
import lemonadex.project.clothes.features.order.service.ReturnService;
import lemonadex.project.clothes.features.storefront.dto.StorefrontRequests.CustomerReturnRequest;
import lemonadex.project.clothes.features.storefront.dto.StorefrontResponses.ExchangeOption;
import lemonadex.project.clothes.features.storefront.repository.StorefrontRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

/**
 * Returns and exchanges asked for by the signed-in customer on their own delivered orders. The request goes
 * through the same rules as one entered by staff (return window, quantities, exchange at the same price), then
 * waits for the shop to approve it.
 */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class CustomerReturnService {
    private final StorefrontRepository store;
    private final OrderService orders;
    private final ReturnService returns;

    public PageResponse<ReturnResponse> list(UUID accountId, UUID orderId, int page, int size) {
        UUID customer = customer(accountId);
        if (orderId != null) owned(customer, orderId);
        return returns.list(orderId, customer, null, null, null, null, page, size);
    }

    public ReturnResponse get(UUID accountId, UUID returnId) {
        return own(customer(accountId), returnId);
    }

    @Transactional
    public ReturnResponse create(UUID accountId, CustomerReturnRequest request) {
        owned(customer(accountId), request.orderId());
        return returns.create(new CreateReturnRequest(request.orderId(), request.requestType(), request.reason().strip(), null,
                request.items().stream().map(i -> new ReturnItemRequest(i.orderItemId(), i.quantity(), i.reason(), null, i.replacementVariantId())).toList(),
                request.images()));
    }

    /** The customer may withdraw a request until the shop has decided on it. */
    @Transactional
    public ReturnResponse cancel(UUID accountId, UUID returnId) {
        ReturnResponse current = own(customer(accountId), returnId);
        if (current.status() != ReturnStatus.REQUESTED && current.status() != ReturnStatus.CANCELLED)
            throw new ConflictException("RETURN_NOT_CANCELLABLE", "The shop has already handled this request");
        return returns.status(returnId, new ReturnStatusRequest(ReturnStatus.CANCELLED, "Khách hàng rút yêu cầu"), accountId);
    }

    /** Sizes and colors of the same product, at the price paid, that an item can be exchanged for. */
    public List<ExchangeOption> exchangeOptions(UUID accountId, UUID orderId, UUID orderItemId) {
        OrderResponse order = owned(customer(accountId), orderId);
        ItemResponse item = order.items().stream().filter(i -> i.id().equals(orderItemId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Order item"));
        if (order.warehouseId() == null) return List.of();
        return store.exchangeOptions(item.productVariantId(), order.warehouseId(), item.unitPrice()).stream()
                .map(r -> new ExchangeOption(r.variantId(), r.colorName(), r.sizeName(), Math.max(0, r.available()))).toList();
    }

    private ReturnResponse own(UUID customer, UUID returnId) {
        ReturnResponse found = returns.get(returnId);
        // Another customer's request answers 404, so ids cannot be probed.
        if (!found.customerId().equals(customer)) throw new ResourceNotFoundException("Return request");
        return found;
    }

    private OrderResponse owned(UUID customer, UUID orderId) {
        OrderResponse order = orders.get(orderId);
        if (!order.customerId().equals(customer)) throw new ResourceNotFoundException("Order");
        return order;
    }

    private UUID customer(UUID accountId) {
        return store.customerOf(accountId).orElseThrow(() -> new ResourceNotFoundException("Active customer profile"));
    }
}
