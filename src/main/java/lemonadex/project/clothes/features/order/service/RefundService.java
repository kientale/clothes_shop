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
public class RefundService {
    private final RefundRepository refunds;
    private final PaymentRepository payments;
    private final ReturnService returns;
    private final OrderService orders;
    private final InventoryService inventory;

    public PageResponse<RefundResponse> list(UUID paymentId, UUID returnRequestId, RefundStatus status, Instant from, Instant to, int page, int size) {
        return PageResponse.from(refunds.findAll(ListFilters.where(null, new String[]{},
                ListFilters.values("paymentId", paymentId, "returnRequestId", returnRequestId, "status", status),
                from, to), PageRequest.of(page, size, ListFilters.NEWEST)).map(this::response));
    }
    public RefundResponse get(UUID id) { return response(required(id)); }

    @Transactional
    public RefundResponse create(RefundRequest request) {
        inventory.lock();
        ReturnRequest returned = returns.required(request.returnRequestId());
        if (returned.getStatus() != ReturnStatus.COMPLETED || returned.getRequestType() != ReturnType.RETURN)
            throw new ConflictException("RETURN_NOT_REFUNDABLE", "Complete a merchandise return before requesting a refund");
        Payment payment = payments.findById(request.paymentId()).orElseThrow(() -> new ResourceNotFoundException("Payment"));
        if (!payment.getOrderId().equals(returned.getOrderId())) throw new BadRequestException("REFUND_ORDER_MISMATCH", "The payment and return must belong to the same order");
        if (payment.getStatus() != PaymentStatus.PAID) throw new ConflictException("PAYMENT_NOT_PAID", "Only settled payments can be refunded");
        if (committed(refunds.findAllByPaymentId(payment.getId())).add(request.amount()).compareTo(payment.getAmount()) > 0
                || committed(refunds.findAllByReturnRequestId(returned.getId())).add(request.amount()).compareTo(returns.refundable(returned)) > 0)
            throw new ConflictException("REFUND_LIMIT_EXCEEDED", "Pending and successful refunds cannot exceed the payment or returned merchandise value");
        Refund refund = new Refund(); refund.setReturnRequestId(returned.getId()); refund.setPaymentId(payment.getId());
        refund.setAmount(request.amount()); refund.setRefundMethod(request.refundMethod().strip()); refund.setReason(request.reason().strip());
        refund.setStatus(RefundStatus.PENDING); return response(refunds.saveAndFlush(refund));
    }

    @Transactional
    public RefundResponse status(UUID id, RefundStatusRequest request, UUID actor) {
        inventory.lock();
        Refund refund = required(id);
        if (refund.getStatus() == request.status()) return response(refund);
        if (refund.getStatus() != RefundStatus.PENDING || request.status() == RefundStatus.PENDING)
            throw new ConflictException("INVALID_REFUND_TRANSITION", "Only pending refunds can be completed, failed or cancelled");
        refund.setStatus(request.status()); refund.setProcessedBy(actor); refund.setProcessedAt(Instant.now()); refunds.flush();
        Payment payment = payments.findById(refund.getPaymentId()).orElseThrow(() -> new ResourceNotFoundException("Payment"));
        orders.refreshPaymentStatus(orders.required(payment.getOrderId())); return response(refund);
    }
    private BigDecimal committed(List<Refund> items) { return items.stream().filter(r -> r.getStatus() == RefundStatus.PENDING || r.getStatus() == RefundStatus.SUCCEEDED).map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private Refund required(UUID id) { return refunds.findById(id).orElseThrow(() -> new ResourceNotFoundException("Refund")); }
    private RefundResponse response(Refund r) { return new RefundResponse(r.getId(), r.getReturnRequestId(), r.getPaymentId(), r.getAmount(), r.getRefundMethod(), r.getStatus(), r.getReason(), r.getProcessedBy(), r.getProcessedAt(), r.getCreatedAt()); }
}
