package lemonadex.project.clothes.features.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

public final class PaymentGatewayResponses {
    private PaymentGatewayResponses() {}

    /** Where to send the shopper to pay. */
    public record PaymentRedirect(String gateway, String payUrl, String reference, BigDecimal amount) {}

    /** Outcome of a gateway callback, shown on the shop's "payment result" page. */
    public record PaymentResult(UUID orderId, String orderCode, String gateway, String status, BigDecimal amount) {}

    /** Whether a gateway can take payments, and the IPN address to register with it. */
    public record GatewayStatus(String gateway, boolean configured, String ipnUrl) {}

    /** Answer VNPay expects from the IPN address. */
    public record VnpayIpnAnswer(String RspCode, String Message) {}
}
