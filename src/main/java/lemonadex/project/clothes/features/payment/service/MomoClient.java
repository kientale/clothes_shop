package lemonadex.project.clothes.features.payment.service;

import lemonadex.project.clothes.common.exception.ConflictException;
import lemonadex.project.clothes.features.payment.config.PaymentGatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MoMo "captureWallet" payments (API v2). The create call returns the payUrl; MoMo then calls the IPN address
 * and redirects the shopper back with the same signed fields. Signatures are HMAC-SHA256 over the fields in
 * the documented order, starting with the access key.
 */
@Slf4j
@Service
public class MomoClient {
    private final PaymentGatewayProperties properties;
    private final RestClient http = RestClient.create();

    public MomoClient(PaymentGatewayProperties properties) {
        this.properties = properties;
    }

    public boolean configured() {
        return properties.momo().configured();
    }

    @SuppressWarnings("unchecked")
    public String payUrl(String reference, long amount, String orderInfo, String redirectUrl, String ipnUrl) {
        var momo = properties.momo();
        String requestType = "captureWallet", extraData = "";
        String raw = "accessKey=" + momo.accessKey() + "&amount=" + amount + "&extraData=" + extraData + "&ipnUrl=" + ipnUrl
                + "&orderId=" + reference + "&orderInfo=" + orderInfo + "&partnerCode=" + momo.partnerCode()
                + "&redirectUrl=" + redirectUrl + "&requestId=" + reference + "&requestType=" + requestType;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", momo.partnerCode());
        body.put("requestId", reference);
        body.put("amount", amount);
        body.put("orderId", reference);
        body.put("orderInfo", orderInfo);
        body.put("redirectUrl", redirectUrl);
        body.put("ipnUrl", ipnUrl);
        body.put("requestType", requestType);
        body.put("extraData", extraData);
        body.put("lang", "vi");
        body.put("signature", sign(raw));
        try {
            Map<String, Object> answer = http.post().uri(momo.endpoint()).body(body).retrieve().body(Map.class);
            if (answer == null || !(answer.get("resultCode") instanceof Number code) || code.intValue() != 0 || answer.get("payUrl") == null) {
                log.warn("MoMo refused the payment request: {}", answer);
                throw unavailable();
            }
            return (String) answer.get("payUrl");
        } catch (RestClientException ex) {
            log.warn("MoMo payment request failed: {}", ex.getMessage());
            throw unavailable();
        }
    }

    /** True when the IPN or redirect fields carry MoMo's valid signature. */
    public boolean verify(Map<String, String> f) {
        if (!configured()) return false;
        String raw = "accessKey=" + properties.momo().accessKey() + "&amount=" + f.get("amount") + "&extraData=" + nz(f.get("extraData"))
                + "&message=" + nz(f.get("message")) + "&orderId=" + f.get("orderId") + "&orderInfo=" + nz(f.get("orderInfo"))
                + "&orderType=" + nz(f.get("orderType")) + "&partnerCode=" + f.get("partnerCode") + "&payType=" + nz(f.get("payType"))
                + "&requestId=" + f.get("requestId") + "&responseTime=" + f.get("responseTime") + "&resultCode=" + f.get("resultCode")
                + "&transId=" + f.get("transId");
        return GatewaySignatures.same(sign(raw), f.get("signature"));
    }

    String sign(String raw) {
        return GatewaySignatures.hmac("HmacSHA256", properties.momo().secretKey(), raw);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static ConflictException unavailable() {
        return new ConflictException("PAYMENT_GATEWAY_ERROR", "The payment gateway did not accept the request; try again or choose another method");
    }
}
