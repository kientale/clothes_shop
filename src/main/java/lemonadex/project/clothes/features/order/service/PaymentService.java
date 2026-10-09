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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class PaymentService {
    private final PaymentRepository payments;
    private final PaymentTransactionRepository transactions;
    private final OrderRepository orderRepository;
    private final OrderService orders;
    private final InventoryService inventory;
    private final lemonadex.project.clothes.features.settings.service.SettingsService settings;

    public PageResponse<PaymentResponse> list(UUID orderId, PaymentStatus status, Instant from, Instant to, int page, int size) {
        return PageResponse.from(payments.findAll(ListFilters.where(null, new String[]{}, ListFilters.values("orderId", orderId, "status", status),
                from, to), PageRequest.of(page, size, ListFilters.NEWEST)).map(this::response));
    }
    public PaymentResponse get(UUID id) { return response(required(id)); }
    public List<PaymentMethodResponse> methods() {
        return settings.payment().paymentsEnabled() ? orderRepository.paymentMethods().stream()
                .map(m -> new PaymentMethodResponse(m.getCode(), m.getName(), m.getProvider())).toList() : List.of();
    }

    @Transactional
    public PaymentResponse create(PaymentRequest request) {
        inventory.lock();
        var configuration = settings.payment();
        if (!configuration.paymentsEnabled()) throw new ConflictException("PAYMENTS_DISABLED", "New payments are disabled");
        if (request.amount().compareTo(configuration.minimumPaymentAmount()) < 0)
            throw new BadRequestException("MINIMUM_PAYMENT_AMOUNT", "The payment is below the configured minimum");
        PurchaseOrder order = orders.required(request.orderId());
        if (order.getOrderStatus() == OrderStatus.CANCELLED) throw new ConflictException("ORDER_CANCELLED", "Cancelled orders cannot receive a new payment");
        String method = request.paymentMethod().toUpperCase(Locale.ROOT);
        if (!orderRepository.enabledPaymentMethod(method)) throw new BadRequestException("PAYMENT_METHOD_UNAVAILABLE", "The payment method is not enabled");
        BigDecimal committed = payments.findAllByOrderIdOrderByCreatedAtAscIdAsc(order.getId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID || p.getStatus() == PaymentStatus.PENDING)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (committed.add(request.amount()).compareTo(order.getTotalAmount()) > 0)
            throw new ConflictException("PAYMENT_LIMIT_EXCEEDED", "Paid and pending payments cannot exceed the order total");
        if (!configuration.allowPartialPayments() && request.amount().compareTo(order.getTotalAmount().subtract(committed)) != 0)
            throw new BadRequestException("PARTIAL_PAYMENT_DISABLED", "Pay the full uncommitted balance");
        Payment payment = new Payment(); payment.setOrderId(order.getId()); payment.setPaymentMethod(method);
        payment.setAmount(request.amount()); payment.setStatus(PaymentStatus.PENDING); payments.saveAndFlush(payment);
        orders.paymentEvent(payment, null);
        return response(payment);
    }

    @Transactional
    public PaymentResponse status(UUID id, PaymentStatusRequest request) {
        inventory.lock();
        Payment payment = required(id);
        if (payment.getStatus() == request.status()) return response(payment);
        if (payment.getStatus() != PaymentStatus.PENDING || request.status() == PaymentStatus.PENDING)
            throw new ConflictException("INVALID_PAYMENT_TRANSITION", "Only pending payments can be settled, failed or voided");
        PurchaseOrder order = orders.required(payment.getOrderId());
        if (request.status() == PaymentStatus.PAID && order.getOrderStatus() == OrderStatus.CANCELLED)
            throw new ConflictException("ORDER_CANCELLED", "The cancelled order cannot be settled");
        payment.setStatus(request.status());
        if (request.status() == PaymentStatus.PAID) payment.setPaidAt(Instant.now());
        payments.flush(); orders.paymentEvent(payment, request.providerTransactionId()); orders.refreshPaymentStatus(order);
        return response(payment);
    }

    public PageResponse<PaymentTransactionResponse> history(UUID paymentId, int page, int size) {
        required(paymentId);
        return PageResponse.from(transactions.findAll(ListFilters.where(null, new String[]{}, ListFilters.values("paymentId", paymentId), null, null),
                PageRequest.of(page, size, ListFilters.NEWEST)).map(t -> new PaymentTransactionResponse(t.getId(), t.getPaymentId(), t.getTransactionCode(),
                t.getProvider(), t.getProviderTransactionId(), t.getAmount(), t.getStatus(), t.getCreatedAt())));
    }
    private Payment required(UUID id) { return payments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment")); }
    private PaymentResponse response(Payment p) { return new PaymentResponse(p.getId(), p.getOrderId(), p.getPaymentMethod(), p.getAmount(), p.getStatus(), p.getPaidAt(), p.getCreatedAt()); }
}
