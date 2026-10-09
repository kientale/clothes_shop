package lemonadex.project.clothes.features.order.service;

import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.common.exception.*;
import lemonadex.project.clothes.common.util.ListFilters;
import lemonadex.project.clothes.features.inventory.service.InventoryService;
import lemonadex.project.clothes.features.order.dto.OrderRequests.*;
import lemonadex.project.clothes.features.order.dto.OrderResponses.*;
import lemonadex.project.clothes.features.order.model.*;
import lemonadex.project.clothes.features.order.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class ShipmentService {
    private final ShipmentRepository shipments;
    private final ShipmentHistoryRepository histories;
    private final OrderRepository orderRepository;
    private final OrderService orders;
    private final InventoryService inventory;
    private final lemonadex.project.clothes.features.settings.service.SettingsService settings;

    public PageResponse<ShipmentResponse> list(String search, UUID orderId, ShipmentStatus status, Instant from, Instant to, int page, int size) {
        return PageResponse.from(shipments.findAll(ListFilters.where(search, new String[]{"shippingProvider", "trackingCode"},
                ListFilters.values("orderId", orderId, "status", status), from, to), PageRequest.of(page, size, ListFilters.NEWEST)).map(this::response));
    }
    public ShipmentResponse get(UUID id) { return response(required(id)); }
    /** The shipment a carrier knows by its tracking code (for carrier status callbacks). */
    public Optional<ShipmentResponse> byTracking(String provider, String trackingCode) {
        return shipments.findByShippingProviderAndTrackingCode(provider, trackingCode).map(this::response);
    }

    @Transactional
    public ShipmentResponse create(ShipmentRequest request) {
        inventory.lock();
        if (!settings.shipping().shippingEnabled()) throw new ConflictException("SHIPPING_DISABLED", "New shipments are disabled");
        PurchaseOrder order = orders.required(request.orderId());
        if (order.getOrderStatus() != OrderStatus.CONFIRMED) throw new ConflictException("ORDER_NOT_CONFIRMED", "Confirm the order before creating its shipment");
        if (shipments.findAllByOrderIdOrderByCreatedAtAscIdAsc(order.getId()).stream().anyMatch(s -> s.getStatus() != ShipmentStatus.CANCELLED))
            throw new ConflictException("SHIPMENT_EXISTS", "The order already has a shipment; update its existing shipment");
        Shipment shipment = new Shipment(); shipment.setOrderId(order.getId()); shipment.setShippingProvider(request.shippingProvider().strip());
        if (order.isShippingFeeConfigured()) {
            if (request.shippingFee().compareTo(order.getShippingFee()) != 0)
                throw new BadRequestException("SHIPPING_FEE_MISMATCH", "Shipment fee must match the order's saved fee");
        }
        shipment.setTrackingCode(OrderService.text(request.trackingCode())); shipment.setShippingFee(request.shippingFee()); shipment.setStatus(ShipmentStatus.PENDING);
        shipments.saveAndFlush(shipment); append(shipment, "Shipment created");
        order.setShippingStatus(ShippingStatus.PENDING); orderRepository.flush();
        return response(shipment);
    }

    @Transactional
    public ShipmentResponse update(UUID id, ShipmentUpdateRequest request) {
        inventory.lock();
        Shipment shipment = required(id);
        if (shipment.getStatus() != ShipmentStatus.PENDING) throw new ConflictException("SHIPMENT_NOT_EDITABLE", "Carrier and tracking code can only change before dispatch");
        shipment.setShippingProvider(request.shippingProvider().strip()); shipment.setTrackingCode(OrderService.text(request.trackingCode()));
        shipments.flush(); return response(shipment);
    }

    @Transactional
    public ShipmentResponse status(UUID id, ShipmentStatusRequest request, UUID actor) {
        inventory.lock();
        Shipment shipment = required(id);
        ShipmentStatus current = shipment.getStatus(), next = request.status();
        if (current == next) return response(shipment);
        Set<ShipmentStatus> allowed = switch (current) {
            case PENDING -> Set.of(ShipmentStatus.SHIPPED, ShipmentStatus.CANCELLED);
            case SHIPPED, IN_TRANSIT -> Set.of(ShipmentStatus.IN_TRANSIT, ShipmentStatus.DELIVERED, ShipmentStatus.FAILED, ShipmentStatus.RETURNED);
            case FAILED -> Set.of(ShipmentStatus.IN_TRANSIT, ShipmentStatus.DELIVERED, ShipmentStatus.RETURNED);
            default -> Set.of();
        };
        if (!allowed.contains(next)) throw new ConflictException("INVALID_SHIPMENT_TRANSITION", "The shipment cannot enter this state");
        PurchaseOrder order = orders.required(shipment.getOrderId());
        if (next == ShipmentStatus.SHIPPED) {
            if (order.getOrderStatus() != OrderStatus.CONFIRMED) throw new ConflictException("ORDER_NOT_CONFIRMED", "The order cannot be dispatched");
            orders.dispatched(order, actor); shipment.setShippedAt(Instant.now());
        }
        if (next == ShipmentStatus.DELIVERED) { orders.delivered(order, actor); shipment.setDeliveredAt(Instant.now()); }
        if (next == ShipmentStatus.RETURNED) orders.returnedByCarrier(order, actor);
        shipment.setStatus(next); order.setShippingStatus(ShippingStatus.valueOf(next.name()));
        shipments.flush(); orderRepository.flush(); append(shipment, request.description());
        return response(shipment);
    }

    public PageResponse<ShipmentHistoryResponse> history(UUID shipmentId, ShipmentStatus status, Instant from, Instant to, int page, int size) {
        if (shipmentId != null) required(shipmentId);
        return PageResponse.from(histories.findAll(ListFilters.where(null, new String[]{}, ListFilters.values("shipmentId", shipmentId, "status", status),
                from, to), PageRequest.of(page, size, ListFilters.NEWEST)).map(h -> new ShipmentHistoryResponse(h.getId(), h.getShipmentId(), h.getStatus(), h.getDescription(), h.getCreatedAt())));
    }
    private void append(Shipment shipment, String description) {
        ShipmentHistory event = new ShipmentHistory(); event.setShipmentId(shipment.getId()); event.setStatus(shipment.getStatus());
        event.setDescription(OrderService.text(description)); histories.saveAndFlush(event);
    }
    private Shipment required(UUID id) { return shipments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Shipment")); }
    private ShipmentResponse response(Shipment s) { return new ShipmentResponse(s.getId(), s.getOrderId(), s.getShippingProvider(), s.getTrackingCode(), s.getShippingFee(), s.getStatus(), s.getShippedAt(), s.getDeliveredAt(), s.getCreatedAt()); }
}
