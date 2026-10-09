package lemonadex.project.clothes.features.order.service;

import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.common.exception.*;
import lemonadex.project.clothes.common.util.ListFilters;
import lemonadex.project.clothes.features.inventory.service.InventoryService;
import lemonadex.project.clothes.features.marketing.service.MarketingCheckoutService;
import lemonadex.project.clothes.features.marketing.dto.MarketingResponses.MarketingLineQuote;
import lemonadex.project.clothes.features.order.dto.OrderRequests.*;
import lemonadex.project.clothes.features.order.dto.OrderResponses.*;
import lemonadex.project.clothes.features.order.model.*;
import lemonadex.project.clothes.features.order.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class OrderService {
    private final OrderRepository orders;
    private final OrderHistoryRepository histories;
    private final PaymentRepository payments;
    private final PaymentTransactionRepository paymentTransactions;
    private final RefundRepository refunds;
    private final ShipmentRepository shipments;
    private final ShipmentHistoryRepository shipmentHistories;
    private final InventoryService inventory;
    private final MarketingCheckoutService marketing;
    private final lemonadex.project.clothes.features.settings.service.SettingsService settings;

    public PageResponse<OrderResponse> list(String search, OrderStatus status, OrderPaymentStatus paymentStatus, ShippingStatus shippingStatus,
                                          UUID customerId, Instant from, Instant to, int page, int size) {
        return PageResponse.from(orders.findAll(ListFilters.where(search, new String[]{"orderCode", "recipientName", "recipientPhone"},
                ListFilters.values("orderStatus", status, "paymentStatus", paymentStatus, "shippingStatus", shippingStatus, "customerId", customerId),
                from, to), PageRequest.of(page, size, ListFilters.NEWEST)).map(this::response));
    }
    public OrderResponse get(UUID id) { return response(required(id)); }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, UUID actor) {
        return create(request, actor, null);
    }

    /** @param carrierFee live price quoted by the carrier for this address; replaces the method's base fee */
    @Transactional
    public OrderResponse create(CreateOrderRequest request, UUID actor, BigDecimal carrierFee) {
        inventory.lock();
        var configuration = settings.order();
        if (settings.general().maintenanceMode()) throw new ConflictException("STORE_MAINTENANCE", "The store is under maintenance");
        if (!configuration.ordersEnabled()) throw new ConflictException("ORDERS_DISABLED", "New orders are disabled");
        if (request.items().size() > configuration.maxItems() || request.items().stream().anyMatch(i -> i.quantity() > configuration.maxQuantityPerItem()))
            throw new BadRequestException("ORDER_ITEM_LIMIT", "The order exceeds configured item or quantity limits");
        orders.lockActiveCustomer(request.customerId()).orElseThrow(() -> new ResourceNotFoundException("Active customer"));
        PurchaseOrder order = new PurchaseOrder();
        order.setOrderCode(configuration.orderCodePrefix() + "-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        order.setCustomerId(request.customerId()); order.setWarehouseId(request.warehouseId());
        order.setOrderStatus(OrderStatus.PLACED); order.setShippingStatus(ShippingStatus.NOT_SHIPPED);
        order.setRecipientName(request.recipientName().strip()); order.setRecipientPhone(request.recipientPhone());
        order.setShippingAddress(request.shippingAddress().strip()); order.setNote(text(request.note()));
        BigDecimal subtotal = BigDecimal.ZERO, lineDiscounts = BigDecimal.ZERO;
        Set<UUID> chosen = new HashSet<>();
        Map<UUID, MarketingLineQuote> quotes = new HashMap<>();
        for (ItemRequest requested : request.items()) {
            if (!chosen.add(requested.productVariantId())) throw new BadRequestException("DUPLICATE_VARIANTS", "A variant can appear only once in an order");
            var variant = inventory.saleVariant(requested.productVariantId());
            BigDecimal gross = money(variant.price().multiply(BigDecimal.valueOf(requested.quantity())));
            var quote = marketing.line(variant.productId(), variant.id(), variant.price(), requested.quantity(), request.applyMarketing() == null ? configuration.enableMarketingByDefault() : request.applyMarketing());
            quotes.put(variant.id(), quote);
            BigDecimal itemDiscount = requested.discountAmount().add(quote.discountAmount());
            if (itemDiscount.compareTo(gross) > 0) throw new BadRequestException("INVALID_DISCOUNT", "The item discount exceeds its value");
            OrderItem item = new OrderItem();
            item.setOrder(order); item.setProductVariantId(variant.id()); item.setProductName(variant.productName()); item.setSku(variant.sku());
            item.setColorName(variant.colorName()); item.setSizeName(variant.sizeName()); item.setUnitPrice(variant.price()); item.setQuantity(requested.quantity());
            item.setDiscountAmount(itemDiscount); item.setTotalAmount(gross.subtract(itemDiscount));
            order.getItems().add(item); subtotal = subtotal.add(gross); lineDiscounts = lineDiscounts.add(itemDiscount);
        }
        BigDecimal discount = money(lineDiscounts.add(request.discountAmount()));
        if (discount.compareTo(subtotal) > 0) throw new BadRequestException("INVALID_DISCOUNT", "The discount exceeds the merchandise subtotal");
        var coupon = marketing.coupon(request.couponCode(), request.customerId(), subtotal.subtract(discount));
        discount = money(discount.add(coupon.discountAmount()));
        BigDecimal merchandise = subtotal.subtract(discount), shippingFee = request.shippingFee();
        if (merchandise.compareTo(configuration.minimumOrderAmount()) < 0)
            throw new BadRequestException("MINIMUM_ORDER_AMOUNT", "Merchandise after discounts is below the configured minimum");
        if (!settings.shipping().shippingEnabled()) throw new ConflictException("SHIPPING_DISABLED", "New shipments are disabled");
        if (request.shippingMethodCode() != null || settings.shipping().useConfiguredFees()) {
            var shipping = settings.quote(request.shippingMethodCode(), merchandise, carrierFee);
            shippingFee = shipping.shippingFee(); order.setShippingMethodCode(shipping.methodCode()); order.setShippingFeeConfigured(true);
        }
        order.setSubtotal(money(subtotal)); order.setDiscountAmount(discount); order.setShippingFee(shippingFee);
        order.setTotalAmount(money(merchandise.add(shippingFee)));
        order.setPaymentStatus(order.getTotalAmount().signum() == 0 ? OrderPaymentStatus.PAID : OrderPaymentStatus.UNPAID);
        // Persist identity and snapshots, then reserve every line in the same transaction.
        orders.saveAndFlush(order);
        for (OrderItem item : order.getItems()) item.setInventoryId(inventory.reserve(request.warehouseId(), item.getProductVariantId(), item.getQuantity(), "ORDER", order.getId(), actor));
        marketing.recordCoupon(coupon, request.customerId(), order.getId());
        for (OrderItem item : order.getItems()) marketing.recordLine(quotes.get(item.getProductVariantId()), item.getId(), item.getQuantity());
        history(order, null, OrderStatus.PLACED, "Order created and stock reserved", actor);
        if (configuration.autoConfirm()) {
            order.setOrderStatus(OrderStatus.CONFIRMED); order.setConfirmedAt(Instant.now());
            history(order, OrderStatus.PLACED, OrderStatus.CONFIRMED, "Automatically confirmed by order settings", actor);
        }
        orders.flush();
        return response(order);
    }

    @Transactional
    public OrderResponse update(UUID id, UpdateOrderRequest request) {
        inventory.lock();
        PurchaseOrder order = required(id);
        if (order.getOrderStatus() != OrderStatus.PLACED && order.getOrderStatus() != OrderStatus.CONFIRMED)
            throw new ConflictException("ORDER_NOT_EDITABLE", "Shipping details can only change before dispatch");
        order.setRecipientName(request.recipientName().strip()); order.setRecipientPhone(request.recipientPhone());
        order.setShippingAddress(request.shippingAddress().strip()); order.setNote(text(request.note()));
        orders.flush();
        return response(order);
    }

    @Transactional
    public OrderResponse status(UUID id, OrderStatusRequest request, UUID actor) {
        inventory.lock();
        PurchaseOrder order = required(id);
        OrderStatus current = order.getOrderStatus(), next = request.status();
        if (current == next) return response(order);
        if (next == OrderStatus.CONFIRMED && current == OrderStatus.PLACED) {
            order.setConfirmedAt(Instant.now());
        } else if (next == OrderStatus.CANCELLED && (current == OrderStatus.PLACED || current == OrderStatus.CONFIRMED)) {
            if (!settings.order().allowCancellation()) throw new ConflictException("CANCELLATION_DISABLED", "Order cancellation is disabled");
            for (OrderItem item : order.getItems()) inventory.release(item.getInventoryId(), item.getQuantity(), "ORDER", id, actor);
            cancelPending(order);
            order.setCancelledAt(Instant.now()); order.setShippingStatus(ShippingStatus.CANCELLED);
        } else if (next == OrderStatus.COMPLETED && current == OrderStatus.DELIVERED) {
            if (paid(order).subtract(refunds.refundedByOrder(id)).compareTo(order.getTotalAmount()) < 0)
                throw new ConflictException("ORDER_NOT_PAID", "Full payment is required to complete the order");
            order.setCompletedAt(Instant.now());
        } else throw new ConflictException("INVALID_ORDER_TRANSITION", "The order cannot enter this state; dispatch and delivery are managed through shipments");
        order.setOrderStatus(next); history(order, current, next, request.note(), actor); orders.flush();
        return response(order);
    }

    public PageResponse<OrderHistoryResponse> history(UUID id, int page, int size) {
        required(id);
        return PageResponse.from(histories.findAll(ListFilters.where(null, new String[]{}, ListFilters.values("orderId", id), null, null),
                PageRequest.of(page, size, ListFilters.NEWEST)).map(h -> new OrderHistoryResponse(h.getId(), h.getOrderId(), h.getFromStatus(), h.getToStatus(), h.getNote(), h.getChangedBy(), h.getCreatedAt())));
    }

    PurchaseOrder required(UUID id) { return orders.findWithItemsById(id).orElseThrow(() -> new ResourceNotFoundException("Order")); }

    BigDecimal paid(PurchaseOrder order) {
        return payments.findAllByOrderIdOrderByCreatedAtAscIdAsc(order.getId()).stream().filter(p -> p.getStatus() == PaymentStatus.PAID)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    void refreshPaymentStatus(PurchaseOrder order) {
        BigDecimal paid = paid(order), refunded = refunds.refundedByOrder(order.getId());
        OrderPaymentStatus status;
        if (refunded.signum() > 0) status = refunded.compareTo(paid) >= 0 ? OrderPaymentStatus.REFUNDED : OrderPaymentStatus.PARTIALLY_REFUNDED;
        else if (paid.compareTo(order.getTotalAmount()) >= 0) status = OrderPaymentStatus.PAID;
        else status = paid.signum() > 0 ? OrderPaymentStatus.PARTIALLY_PAID : OrderPaymentStatus.UNPAID;
        order.setPaymentStatus(status); orders.flush();
    }

    void dispatched(PurchaseOrder order, UUID actor) {
        for (OrderItem item : order.getItems()) inventory.ship(item.getInventoryId(), item.getQuantity(), false, order.getId(), actor);
        OrderStatus previous = order.getOrderStatus(); order.setOrderStatus(OrderStatus.SHIPPED);
        history(order, previous, OrderStatus.SHIPPED, "Shipment dispatched", actor);
    }
    void delivered(PurchaseOrder order, UUID actor) {
        OrderStatus previous = order.getOrderStatus(); order.setOrderStatus(OrderStatus.DELIVERED);
        history(order, previous, OrderStatus.DELIVERED, "Shipment delivered", actor);
    }
    void returnedByCarrier(PurchaseOrder order, UUID actor) {
        for (OrderItem item : order.getItems()) inventory.restock(item.getInventoryId(), item.getQuantity(), "ORDER", order.getId(), actor);
        OrderStatus previous = order.getOrderStatus(); order.setOrderStatus(OrderStatus.CANCELLED); order.setCancelledAt(Instant.now());
        cancelPending(order); history(order, previous, OrderStatus.CANCELLED, "Undelivered shipment returned to warehouse", actor);
    }

    private void cancelPending(PurchaseOrder order) {
        marketing.release(order.getId());
        for (Payment payment : payments.findAllByOrderIdOrderByCreatedAtAscIdAsc(order.getId())) {
            if (payment.getStatus() == PaymentStatus.PENDING) { payment.setStatus(PaymentStatus.VOID); paymentEvent(payment, null); }
        }
        for (Shipment shipment : shipments.findAllByOrderIdOrderByCreatedAtAscIdAsc(order.getId())) {
            if (shipment.getStatus() == ShipmentStatus.PENDING) {
                shipment.setStatus(ShipmentStatus.CANCELLED);
                ShipmentHistory history = new ShipmentHistory(); history.setShipmentId(shipment.getId());
                history.setStatus(ShipmentStatus.CANCELLED); history.setDescription("Order cancelled"); shipmentHistories.save(history);
            }
        }
    }

    void paymentEvent(Payment payment, String providerTransactionId) {
        PaymentTransaction event = new PaymentTransaction(); event.setPaymentId(payment.getId());
        event.setTransactionCode("PT-" + UUID.randomUUID()); event.setProvider("MANUAL");
        event.setProviderTransactionId(text(providerTransactionId)); event.setAmount(payment.getAmount()); event.setStatus(payment.getStatus());
        paymentTransactions.saveAndFlush(event);
    }
    private void history(PurchaseOrder order, OrderStatus previous, OrderStatus next, String note, UUID actor) {
        OrderHistory history = new OrderHistory(); history.setOrderId(order.getId()); history.setFromStatus(previous);
        history.setToStatus(next); history.setNote(text(note)); history.setChangedBy(actor); histories.saveAndFlush(history);
    }
    OrderResponse response(PurchaseOrder o) {
        List<ItemResponse> items = o.getItems().stream().map(i -> new ItemResponse(i.getId(), i.getProductVariantId(), i.getInventoryId(),
                i.getProductName(), i.getSku(), i.getColorName(), i.getSizeName(), i.getUnitPrice(), i.getQuantity(), i.getDiscountAmount(), i.getTotalAmount())).toList();
        return new OrderResponse(o.getId(), o.getOrderCode(), o.getCustomerId(), o.getWarehouseId(), o.getOrderStatus(), o.getPaymentStatus(), o.getShippingStatus(),
                o.getSubtotal(), o.getDiscountAmount(), o.getShippingFee(), o.getTotalAmount(), paid(o), refunds.refundedByOrder(o.getId()),
                o.getRecipientName(), o.getRecipientPhone(), o.getShippingAddress(), o.getNote(), o.getShippingMethodCode(), o.getContactEmail(), items, o.getPlacedAt(), o.getConfirmedAt(),
                o.getCompletedAt(), o.getCancelledAt(), o.getCreatedAt(), o.getUpdatedAt());
    }
    static String text(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    static BigDecimal money(BigDecimal value) {
        if (value.signum() < 0 || value.compareTo(new BigDecimal("9999999999999999.99")) > 0)
            throw new BadRequestException("AMOUNT_OUT_OF_RANGE", "The calculated amount exceeds the money limit");
        return value;
    }
}
