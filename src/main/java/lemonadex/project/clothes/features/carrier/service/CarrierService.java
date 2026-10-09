package lemonadex.project.clothes.features.carrier.service;

import lemonadex.project.clothes.common.exception.BadRequestException;
import lemonadex.project.clothes.common.exception.ConflictException;
import lemonadex.project.clothes.features.carrier.config.GhtkProperties;
import lemonadex.project.clothes.features.carrier.dto.CarrierDtos.*;
import lemonadex.project.clothes.features.order.dto.OrderRequests.ShipmentRequest;
import lemonadex.project.clothes.features.order.dto.OrderRequests.ShipmentStatusRequest;
import lemonadex.project.clothes.features.order.dto.OrderResponses.OrderResponse;
import lemonadex.project.clothes.features.order.dto.OrderResponses.ShipmentResponse;
import lemonadex.project.clothes.features.order.model.OrderStatus;
import lemonadex.project.clothes.features.order.model.ShipmentStatus;
import lemonadex.project.clothes.features.order.service.OrderService;
import lemonadex.project.clothes.features.order.service.ShipmentService;
import lemonadex.project.clothes.features.settings.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Carrier integration (GHTK): live fees at checkout, handing confirmed orders to the carrier, and following the
 * carrier's status callbacks through the normal shipment workflow (so stock, order status and history stay in
 * step with manual updates).
 */
@Slf4j
@Service @RequiredArgsConstructor
public class CarrierService {
    public static final String GHTK = "GHTK";

    private final GhtkClient ghtk;
    private final GhtkProperties properties;
    private final SettingsService settings;
    private final OrderService orders;
    private final ShipmentService shipments;

    public CarrierStatus status() {
        return new CarrierStatus(GHTK, ghtk.configured());
    }

    /**
     * Live fee for a GHTK shipping method; empty for other methods, without an area, or when GHTK is not
     * configured or cannot quote (checkout then falls back to the method's base fee).
     */
    public Optional<BigDecimal> quote(String shippingMethodCode, ShippingArea area, int itemCount, BigDecimal value) {
        if (area == null || area.province() == null || area.province().isBlank() || !ghtk.configured()) return Optional.empty();
        if (!settings.shippingProvider(shippingMethodCode).map(GHTK::equalsIgnoreCase).orElse(false)) return Optional.empty();
        return ghtk.fee(area, Math.max(1, itemCount) * properties.itemWeightGrams(), value);
    }

    /** Registers a confirmed order with GHTK and records the shipment with GHTK's tracking code. */
    @Transactional
    public ShipmentResponse ship(UUID orderId) {
        if (!ghtk.configured()) throw new ConflictException("CARRIER_NOT_CONFIGURED", "GHTK keys are not configured");
        OrderResponse order = orders.get(orderId);
        if (order.orderStatus() != OrderStatus.CONFIRMED) throw new ConflictException("ORDER_NOT_CONFIRMED", "Confirm the order before creating its shipment");
        ShippingArea area = area(order.shippingAddress());
        List<Parcel> parcels = order.items().stream().map(i -> new Parcel(i.productName() + " " + i.colorName() + "/" + i.sizeName(), i.sku(), i.quantity())).toList();
        BigDecimal due = order.totalAmount().subtract(order.paidAmount()).max(BigDecimal.ZERO);
        CarrierOrder created = ghtk.createOrder(order.orderCode(), order.recipientName(), order.recipientPhone(), area, parcels, due,
                order.totalAmount(), order.note());
        return shipments.create(new ShipmentRequest(orderId, GHTK, created.trackingCode(), order.shippingFee()));
    }

    /** GHTK status callback. Unknown labels and statuses the shipment cannot reach are ignored. */
    public void ghtkCallback(String secret, Map<String, String> fields) {
        if (!validSecret(secret)) throw new BadRequestException("INVALID_WEBHOOK", "Unknown caller");
        String label = fields.get("label_id");
        ShipmentStatus target = ghtkStatus(fields.get("status_id"));
        if (label == null || target == null) return;
        Optional<ShipmentResponse> shipment = shipments.byTracking(GHTK, label);
        if (shipment.isEmpty()) {
            log.info("GHTK callback for unknown label {}", label);
            return;
        }
        String reason = fields.get("reason");
        String note = "GHTK: trạng thái " + fields.get("status_id") + (reason == null || reason.isBlank() ? "" : " - " + reason);
        // Each step runs in its own transaction through the normal workflow.
        for (ShipmentStatus step : path(shipment.get().status(), target))
            shipments.status(shipment.get().id(), new ShipmentStatusRequest(step, note), null);
    }

    /** GHTK status_id to our shipment state; null for statuses that change nothing here. */
    static ShipmentStatus ghtkStatus(String statusId) {
        if (statusId == null) return null;
        return switch (statusId.strip()) {
            case "-1" -> ShipmentStatus.CANCELLED;
            case "3" -> ShipmentStatus.SHIPPED;
            case "4", "10" -> ShipmentStatus.IN_TRANSIT;
            case "5", "6" -> ShipmentStatus.DELIVERED;
            case "9" -> ShipmentStatus.FAILED;
            case "11", "21" -> ShipmentStatus.RETURNED;
            default -> null;
        };
    }

    /** Steps from the current state to the carrier's, through the transitions the shipment workflow allows. */
    static List<ShipmentStatus> path(ShipmentStatus current, ShipmentStatus target) {
        if (current == target) return List.of();
        boolean moving = current == ShipmentStatus.SHIPPED || current == ShipmentStatus.IN_TRANSIT || current == ShipmentStatus.FAILED;
        return switch (target) {
            case SHIPPED -> current == ShipmentStatus.PENDING ? List.of(ShipmentStatus.SHIPPED) : List.of();
            case IN_TRANSIT, DELIVERED -> current == ShipmentStatus.PENDING ? List.of(ShipmentStatus.SHIPPED, target)
                    : moving ? List.of(target) : List.of();
            case FAILED -> current == ShipmentStatus.SHIPPED || current == ShipmentStatus.IN_TRANSIT ? List.of(target) : List.of();
            case RETURNED -> moving ? List.of(target) : List.of();
            case CANCELLED -> current == ShipmentStatus.PENDING ? List.of(target) : List.of();
            default -> List.of();
        };
    }

    /**
     * Splits the saved one-line address ("street, ward, [district,] province") into the parts GHTK needs.
     * Addresses on the 2025 two-level map have no district.
     */
    static ShippingArea area(String address) {
        List<String> parts = Arrays.stream(address.split(",")).map(String::strip).filter(p -> !p.isEmpty()).toList();
        if (parts.isEmpty()) throw new BadRequestException("ADDRESS_UNPARSEABLE", "The shipping address is empty");
        int n = parts.size();
        String province = parts.get(n - 1);
        if (n >= 4) return new ShippingArea(province, parts.get(n - 2), parts.get(n - 3), String.join(", ", parts.subList(0, n - 3)));
        if (n == 3) return new ShippingArea(province, null, parts.get(1), parts.get(0));
        if (n == 2) return new ShippingArea(province, null, null, parts.get(0));
        return new ShippingArea(province, null, null, null);
    }

    private boolean validSecret(String secret) {
        String expected = properties.webhookSecret();
        return expected != null && !expected.isBlank() && secret != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8));
    }
}
