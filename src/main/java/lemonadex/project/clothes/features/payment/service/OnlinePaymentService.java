package lemonadex.project.clothes.features.payment.service;

import lemonadex.project.clothes.common.exception.BadRequestException;
import lemonadex.project.clothes.common.exception.ConflictException;
import lemonadex.project.clothes.features.mail.service.MailService;
import lemonadex.project.clothes.features.order.dto.OrderRequests.PaymentRequest;
import lemonadex.project.clothes.features.order.dto.OrderRequests.PaymentStatusRequest;
import lemonadex.project.clothes.features.order.dto.OrderResponses.OrderResponse;
import lemonadex.project.clothes.features.order.dto.OrderResponses.PaymentResponse;
import lemonadex.project.clothes.features.order.model.OrderStatus;
import lemonadex.project.clothes.features.order.model.PaymentStatus;
import lemonadex.project.clothes.features.order.service.OrderService;
import lemonadex.project.clothes.features.order.service.PaymentService;
import lemonadex.project.clothes.features.payment.config.PaymentGatewayProperties;
import lemonadex.project.clothes.features.payment.dto.PaymentGatewayResponses.*;
import lemonadex.project.clothes.features.payment.repository.GatewayTransactionRepository;
import lemonadex.project.clothes.features.payment.repository.GatewayTransactionRepository.Attempt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.*;

/**
 * Online payment of an order through VNPay or MoMo. Starting a payment reuses the order's pending payment of
 * that method (or replaces a pending payment of another method) and records one gateway attempt; the
 * gateway's signed callback (IPN, or the shopper's return to the shop) then settles the payment once.
 * Callers must have checked that the shopper may pay this order.
 */
@Slf4j
@Service @RequiredArgsConstructor
public class OnlinePaymentService {
    public static final Set<String> GATEWAYS = Set.of("VNPAY", "MOMO");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderService orders;
    private final PaymentService payments;
    private final GatewayTransactionRepository attempts;
    private final VnpayClient vnpay;
    private final MomoClient momo;
    private final PaymentGatewayProperties properties;
    private final MailService mail;
    private final ObjectMapper json;

    /** Methods that can actually be paid online right now (keys configured). */
    public boolean available(String method) {
        return switch (method == null ? "" : method.toUpperCase(Locale.ROOT)) {
            case "VNPAY" -> vnpay.configured();
            case "MOMO" -> momo.configured();
            default -> false;
        };
    }

    /** For the payment settings screen: which gateways have their keys, and the IPN addresses to register. */
    public List<GatewayStatus> gateways() {
        String base = properties.callbackBaseUrl();
        return List.of(new GatewayStatus("VNPAY", vnpay.configured(), base.isEmpty() ? null : base + "/api/v1/payments/vnpay/ipn"),
                new GatewayStatus("MOMO", momo.configured() && !base.isEmpty(), base.isEmpty() ? null : base + "/api/v1/payments/momo/ipn"));
    }

    @Transactional
    public PaymentRedirect start(UUID orderId, String method, String clientIp) {
        String gateway = method.toUpperCase(Locale.ROOT);
        if (!GATEWAYS.contains(gateway) || !available(gateway))
            throw new BadRequestException("PAYMENT_GATEWAY_UNAVAILABLE", "This online payment method is not available");
        OrderResponse order = orders.get(orderId);
        if (order.orderStatus() == OrderStatus.CANCELLED) throw new ConflictException("ORDER_CANCELLED", "Cancelled orders cannot be paid");
        BigDecimal due = order.totalAmount().subtract(order.paidAmount());
        if (due.signum() <= 0) throw new ConflictException("ORDER_ALREADY_PAID", "The order is already paid");

        List<PaymentResponse> pending = payments.list(orderId, PaymentStatus.PENDING, null, null, 0, 50).content();
        PaymentResponse payment = pending.stream().filter(p -> p.paymentMethod().equals(gateway)).findFirst().orElse(null);
        if (payment == null) {
            // The shopper switched methods (for example from COD): the old promise to pay is withdrawn.
            for (PaymentResponse other : pending) payments.status(other.id(), new PaymentStatusRequest(PaymentStatus.VOID, null));
            payment = payments.create(new PaymentRequest(orderId, gateway, due));
        }
        long amount = whole(payment.amount());
        String reference = order.orderCode() + "-" + Long.toString(Math.abs(RANDOM.nextLong()), 36).toUpperCase(Locale.ROOT);
        attempts.create(payment.id(), reference, gateway, payment.amount());
        // Gateways show this text on their page; VNPay wants it without Vietnamese accents.
        String info = "Thanh toan don hang " + order.orderCode();
        String returnUrl = mail.storefront() + "/shop/payment/" + gateway.toLowerCase(Locale.ROOT);
        String payUrl = gateway.equals("VNPAY")
                ? vnpay.payUrl(reference, amount, info, returnUrl, clientIp)
                : momo.payUrl(reference, amount, info, returnUrl, properties.callbackBaseUrl() + "/api/v1/payments/momo/ipn");
        return new PaymentRedirect(gateway, payUrl, reference, payment.amount());
    }

    /** VNPay IPN: answers in the format VNPay expects and never throws. */
    @Transactional
    public VnpayIpnAnswer vnpayIpn(Map<String, String> params) {
        if (!vnpay.verify(params)) return new VnpayIpnAnswer("97", "Invalid signature");
        Optional<Attempt> attempt = attempts.lock("VNPAY", params.getOrDefault("vnp_TxnRef", ""));
        if (attempt.isEmpty()) return new VnpayIpnAnswer("01", "Order not found");
        if (!sameAmount(attempt.get(), params.get("vnp_Amount"), 100)) return new VnpayIpnAnswer("04", "Invalid amount");
        if (!"PENDING".equals(attempt.get().status())) return new VnpayIpnAnswer("02", "Order already confirmed");
        settle(attempt.get(), vnpaySucceeded(params), params.get("vnp_TransactionNo"), params);
        return new VnpayIpnAnswer("00", "Confirm Success");
    }

    /** MoMo IPN; false when the request is not a valid MoMo callback. */
    @Transactional
    public boolean momoIpn(Map<String, String> fields) {
        if (!momo.verify(fields)) return false;
        Optional<Attempt> attempt = attempts.lock("MOMO", fields.getOrDefault("orderId", ""));
        if (attempt.isEmpty() || !sameAmount(attempt.get(), fields.get("amount"), 1)) return false;
        if ("PENDING".equals(attempt.get().status())) settle(attempt.get(), "0".equals(fields.get("resultCode")), fields.get("transId"), fields);
        return true;
    }

    /**
     * The shopper's browser coming back from the gateway with the signed result. It settles the attempt like the
     * IPN would (whichever arrives first wins), so the shop works even where the IPN cannot reach it.
     */
    @Transactional
    public PaymentResult returned(String gatewayName, Map<String, String> params) {
        String gateway = gatewayName.toUpperCase(Locale.ROOT);
        boolean valid = switch (gateway) {
            case "VNPAY" -> vnpay.verify(params);
            case "MOMO" -> momo.verify(params);
            default -> false;
        };
        if (!valid) throw new BadRequestException("INVALID_PAYMENT_SIGNATURE", "The payment result could not be verified");
        String reference = gateway.equals("VNPAY") ? params.getOrDefault("vnp_TxnRef", "") : params.getOrDefault("orderId", "");
        Attempt attempt = attempts.lock(gateway, reference).orElseThrow(() -> new BadRequestException("UNKNOWN_PAYMENT", "Unknown payment reference"));
        if (!sameAmount(attempt, gateway.equals("VNPAY") ? params.get("vnp_Amount") : params.get("amount"), gateway.equals("VNPAY") ? 100 : 1))
            throw new BadRequestException("INVALID_PAYMENT_AMOUNT", "The paid amount does not match the order");
        String status = attempt.status();
        if ("PENDING".equals(status)) {
            boolean success = gateway.equals("VNPAY") ? vnpaySucceeded(params) : "0".equals(params.get("resultCode"));
            status = settle(attempt, success, gateway.equals("VNPAY") ? params.get("vnp_TransactionNo") : params.get("transId"), params);
        }
        PaymentResponse payment = payments.get(attempt.paymentId());
        OrderResponse order = orders.get(payment.orderId());
        return new PaymentResult(order.id(), order.orderCode(), gateway, status, attempt.amount());
    }

    private String settle(Attempt attempt, boolean success, String providerTransactionId, Map<String, String> raw) {
        String rawJson = json.writeValueAsString(raw);
        if (!success) {
            // The payment stays pending so the shopper can try again.
            attempts.finish(attempt.id(), "FAILED", null, rawJson);
            return "FAILED";
        }
        // Checked first: a failing call would mark the whole transaction for rollback and lose the attempt record.
        PaymentStatus current = payments.get(attempt.paymentId()).status();
        if (current == PaymentStatus.PENDING) {
            payments.status(attempt.paymentId(), new PaymentStatusRequest(PaymentStatus.PAID, null));
        } else if (current != PaymentStatus.PAID) {
            // Money arrived for a payment that was voided meanwhile (order cancelled, method switched): keep the
            // record so staff can refund or re-attach it.
            log.error("Gateway payment {} received for payment {} in state {}; manual review needed",
                    providerTransactionId, attempt.paymentId(), current);
        }
        attempts.finish(attempt.id(), "PAID", providerTransactionId, rawJson);
        return "PAID";
    }

    private static boolean vnpaySucceeded(Map<String, String> params) {
        return "00".equals(params.get("vnp_ResponseCode")) && "00".equals(params.get("vnp_TransactionStatus"));
    }

    private static boolean sameAmount(Attempt attempt, String received, int unit) {
        try {
            return new BigDecimal(received).compareTo(attempt.amount().multiply(BigDecimal.valueOf(unit))) == 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Gateways take whole đồng. */
    private static long whole(BigDecimal amount) {
        try {
            return amount.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException ex) {
            throw new BadRequestException("INVALID_PAYMENT_AMOUNT", "Online payments need a whole number of đồng");
        }
    }
}
