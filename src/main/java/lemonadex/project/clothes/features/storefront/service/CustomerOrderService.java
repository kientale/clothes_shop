package lemonadex.project.clothes.features.storefront.service;

import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.common.exception.BadRequestException;
import lemonadex.project.clothes.common.exception.ConflictException;
import lemonadex.project.clothes.common.exception.ResourceNotFoundException;
import lemonadex.project.clothes.features.carrier.service.CarrierService;
import lemonadex.project.clothes.features.order.dto.OrderRequests.*;
import lemonadex.project.clothes.features.order.dto.OrderResponses.OrderResponse;
import lemonadex.project.clothes.features.order.model.OrderStatus;
import lemonadex.project.clothes.features.order.service.OrderService;
import lemonadex.project.clothes.features.order.service.PaymentService;
import lemonadex.project.clothes.features.order.service.ShipmentService;
import lemonadex.project.clothes.features.mail.service.MailService;
import lemonadex.project.clothes.features.mail.service.MailTemplates;
import lemonadex.project.clothes.features.payment.dto.PaymentGatewayResponses.PaymentRedirect;
import lemonadex.project.clothes.features.payment.service.OnlinePaymentService;
import lemonadex.project.clothes.features.settings.service.SettingsService;
import lemonadex.project.clothes.features.storefront.dto.StorefrontRequests.*;
import lemonadex.project.clothes.features.storefront.dto.StorefrontResponses.*;
import lemonadex.project.clothes.features.storefront.repository.StorefrontRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/**
 * Orders placed by shoppers, signed in or as guests. Checkout reuses the admin order pipeline (pricing,
 * promotions, coupon, shipping fee, stock reservation) so both channels follow the same rules; a signed-in
 * customer comes from the JWT and the warehouse is the first active one that can fill every line.
 */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class CustomerOrderService {
    private static final int RECORD_LIMIT = 50;

    private final StorefrontRepository store;
    private final OrderService orders;
    private final PaymentService paymentRecords;
    private final OnlinePaymentService payments;
    private final CarrierService carriers;
    private final ShipmentService shipments;
    private final MailService mail;
    private final SettingsService settings;

    public PageResponse<OrderResponse> list(UUID accountId, OrderStatus status, int page, int size) {
        return orders.list(null, status, null, null, customer(accountId), null, null, page, size);
    }

    public CustomerOrder get(UUID accountId, UUID orderId) {
        return view(owned(accountId, orderId));
    }

    @Transactional
    public CustomerOrder checkout(UUID accountId, CheckoutRequest request) {
        OrderResponse placed = place(customer(accountId), request, accountId);
        store.accountEmail(accountId).ifPresent(email ->
                confirmByEmail(email, placed, request.paymentMethod(), mail.storefront() + "/shop/orders/" + placed.id()));
        return view(placed);
    }

    /**
     * Checkout without an account. The guest is a customer without a login, found again by phone number; the
     * confirmation goes to the email given here, and the order is followed with its code and phone.
     */
    @Transactional
    public TrackedOrder guestCheckout(CheckoutRequest request) {
        if (blank(request.email())) throw new BadRequestException("EMAIL_REQUIRED", "Enter an email for the order confirmation");
        String phone = digits(request.recipientPhone());
        OrderResponse placed = place(store.guestCustomer(request.recipientName().strip(), phone), request, null);
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        store.setContactEmail(placed.id(), email);
        confirmByEmail(email, placed, request.paymentMethod(),
                mail.storefront() + "/shop/track?code=" + placed.orderCode() + "&phone=" + phone);
        return tracked(placed);
    }

    /** Estimated shipping fee for the cart and address, before the order is placed. */
    public ShippingQuote quote(ShippingQuoteRequest request) {
        Map<UUID, Integer> wanted = quantities(request.items());
        BigDecimal value = value(wanted);
        Optional<BigDecimal> live = carriers.quote(request.shippingMethodCode(), request.area(), count(wanted), value);
        var quote = settings.quote(request.shippingMethodCode(), value, live.orElse(null));
        return new ShippingQuote(quote.methodCode(), quote.shippingFee(), quote.freeShipping(), live.isPresent());
    }

    /** Sends the signed-in customer to VNPay or MoMo to pay their order. */
    @Transactional
    public PaymentRedirect pay(UUID accountId, UUID orderId, String method, String clientIp) {
        owned(accountId, orderId);
        return payments.start(orderId, method, clientIp);
    }

    /** The same for a guest, who proves the order with its code and phone like on the tracking page. */
    @Transactional
    public PaymentRedirect guestPay(GuestPayRequest request, String clientIp) {
        UUID id = store.orderByCodeAndPhone(request.orderCode().strip(), digits(request.phone()))
                .orElseThrow(() -> new ResourceNotFoundException("Order"));
        return payments.start(id, request.method(), clientIp);
    }

    /**
     * Order tracking without signing in: the order code and the phone on the order must both match, otherwise
     * the answer is 404 either way. Only status, items, totals, payments and delivery are shown, never the full address.
     */
    public TrackedOrder track(OrderLookupRequest request) {
        UUID id = store.orderByCodeAndPhone(request.orderCode().strip(), digits(request.phone()))
                .orElseThrow(() -> new ResourceNotFoundException("Order"));
        return tracked(orders.get(id));
    }

    /** Customers may cancel only before the shop confirms the order. */
    @Transactional
    public CustomerOrder cancel(UUID accountId, UUID orderId) {
        OrderResponse order = owned(accountId, orderId);
        if (order.orderStatus() != OrderStatus.PLACED && order.orderStatus() != OrderStatus.CANCELLED)
            throw new ConflictException("ORDER_NOT_CANCELLABLE", "The order has been confirmed; contact the shop to cancel it");
        return view(orders.status(orderId, new OrderStatusRequest(OrderStatus.CANCELLED, "Khách hàng hủy đơn"), accountId));
    }

    /** Places the order through the admin pipeline; the chosen payment method becomes a pending payment. */
    private OrderResponse place(UUID customer, CheckoutRequest request, UUID actor) {
        Map<UUID, Integer> wanted = quantities(request.items());
        String method = blank(request.paymentMethod()) ? null : request.paymentMethod().strip().toUpperCase(Locale.ROOT);
        // An online method whose gateway keys are missing would leave the shopper with no way to pay.
        if (method != null && OnlinePaymentService.GATEWAYS.contains(method) && !payments.available(method))
            throw new BadRequestException("PAYMENT_GATEWAY_UNAVAILABLE", "This online payment method is not available");
        UUID warehouse = warehouseFor(wanted);
        String shippingMethod = blank(request.shippingMethodCode()) ? null : request.shippingMethodCode().strip();
        BigDecimal carrierFee = shippingMethod == null ? null
                : carriers.quote(shippingMethod, request.area(), count(wanted), value(wanted)).orElse(null);
        var order = orders.create(new CreateOrderRequest(customer, warehouse, request.recipientName().strip(), request.recipientPhone(),
                request.shippingAddress().strip(), request.note(), BigDecimal.ZERO, BigDecimal.ZERO,
                wanted.entrySet().stream().map(e -> new ItemRequest(e.getKey(), e.getValue(), BigDecimal.ZERO)).toList(),
                blank(request.couponCode()) ? null : request.couponCode().strip(), null, shippingMethod), actor, carrierFee);
        // Recorded for the full total; staff (or the gateway callback) confirm it when the money arrives.
        if (method != null && order.totalAmount().signum() > 0)
            paymentRecords.create(new PaymentRequest(order.id(), method, order.totalAmount()));
        return orders.get(order.id());
    }

    private TrackedOrder tracked(OrderResponse o) {
        return new TrackedOrder(o.orderCode(), o.orderStatus().name(), o.paymentStatus().name(), o.shippingStatus().name(), o.placedAt(),
                o.confirmedAt(), o.completedAt(), o.cancelledAt(), maskName(o.recipientName()), area(o.shippingAddress()),
                o.items().stream().map(i -> new TrackedLine(i.productName(), i.colorName(), i.sizeName(), i.quantity(), i.totalAmount())).toList(),
                o.subtotal(), o.discountAmount(), o.shippingFee(), o.totalAmount(), o.paidAmount(),
                shipments.list(null, o.id(), null, null, null, 0, RECORD_LIMIT).content().stream()
                        .map(s -> new TrackedShipment(s.shippingProvider(), s.trackingCode(), s.status().name(), s.shippedAt(), s.deliveredAt())).toList(),
                paymentRecords.list(o.id(), null, null, null, 0, RECORD_LIMIT).content().stream()
                        .map(p -> new TrackedPayment(p.paymentMethod(), p.amount(), p.status().name())).toList());
    }

    private void confirmByEmail(String email, OrderResponse order, String paymentMethod, String link) {
        var lines = order.items().stream()
                .map(i -> new MailTemplates.Line(i.productName(), i.colorName() + " / " + i.sizeName(), i.quantity(), i.totalAmount())).toList();
        mail.send(email, "LemonadeX đã nhận đơn " + order.orderCode(), MailTemplates.orderPlaced(order.recipientName(), order.orderCode(), lines,
                order.shippingFee(), order.totalAmount(), order.shippingAddress(), paymentNote(paymentMethod, order), link));
    }

    /** For a bank transfer: where to send the money and what to write in the transfer note. */
    private String paymentNote(String paymentMethod, OrderResponse order) {
        if (blank(paymentMethod)) return null;
        return settings.publicConfiguration().paymentMethods().stream()
                .filter(m -> m.code().equalsIgnoreCase(paymentMethod.strip()) && m.bankDetails() != null)
                .findFirst()
                .map(m -> "Chuyển khoản " + order.totalAmount().toBigInteger() + " ₫ tới " + m.bankDetails().bankName() + " "
                        + m.bankDetails().accountNumber() + " (" + m.bankDetails().accountHolder() + "), nội dung: " + order.orderCode())
                .orElse(null);
    }

    /** "Nguyễn Minh Thư" becomes "N*** Thư". */
    private static String maskName(String name) {
        String[] parts = name.strip().split("\\s+");
        if (parts.length == 1) return parts[0].charAt(0) + "***";
        return parts[0].charAt(0) + "*** " + parts[parts.length - 1];
    }

    /** Only the last two parts of the address (ward and province), never the street and number. */
    private static String area(String address) {
        String[] parts = address.split(",");
        int from = Math.max(0, parts.length - 2);
        return String.join(",", java.util.Arrays.copyOfRange(parts, from, parts.length)).strip();
    }

    private UUID warehouseFor(Map<UUID, Integer> wanted) {
        Map<UUID, Map<UUID, Long>> stock = store.availability(wanted.keySet());
        return stock.entrySet().stream()
                .filter(w -> wanted.entrySet().stream().allMatch(item -> w.getValue().getOrDefault(item.getKey(), 0L) >= item.getValue()))
                .max(Comparator.comparingLong(w -> w.getValue().values().stream().mapToLong(Long::longValue).sum()))
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new ConflictException("INSUFFICIENT_STOCK", "Not enough stock in a single warehouse for every item"));
    }

    /** Merchandise value at current prices, for carrier quotes (insurance) before the order prices it for real. */
    private BigDecimal value(Map<UUID, Integer> wanted) {
        Map<UUID, BigDecimal> prices = store.prices(wanted.keySet());
        return wanted.entrySet().stream()
                .map(e -> prices.getOrDefault(e.getKey(), BigDecimal.ZERO).multiply(BigDecimal.valueOf(e.getValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private CustomerOrder view(OrderResponse order) {
        return new CustomerOrder(order, paymentRecords.list(order.id(), null, null, null, 0, RECORD_LIMIT).content(),
                shipments.list(null, order.id(), null, null, null, 0, RECORD_LIMIT).content());
    }

    private OrderResponse owned(UUID accountId, UUID orderId) {
        UUID customer = customer(accountId);
        OrderResponse order = orders.get(orderId);
        // Another customer's order answers 404, so ids cannot be probed.
        if (!order.customerId().equals(customer)) throw new ResourceNotFoundException("Order");
        return order;
    }

    private UUID customer(UUID accountId) {
        return store.customerOf(accountId).orElseThrow(() -> new ResourceNotFoundException("Active customer profile"));
    }

    private static Map<UUID, Integer> quantities(List<CheckoutItem> items) {
        Map<UUID, Integer> wanted = new LinkedHashMap<>();
        for (CheckoutItem item : items) wanted.merge(item.productVariantId(), item.quantity(), Integer::sum);
        return wanted;
    }

    private static int count(Map<UUID, Integer> wanted) {
        return wanted.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static String digits(String phone) {
        return phone.replaceAll("[^0-9]", "");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
