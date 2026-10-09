package lemonadex.project.clothes.features.payment.service;

import lemonadex.project.clothes.features.payment.config.PaymentGatewayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * VNPay payment URL (API 2.1.0) and checks of the signed parameters VNPay sends back. Every vnp_ parameter
 * except the hash is sorted by name, URL-encoded and signed with HMAC-SHA512 of the merchant secret.
 */
@Service @RequiredArgsConstructor
public class VnpayClient {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Duration PAY_WITHIN = Duration.ofMinutes(15);

    private final PaymentGatewayProperties properties;
    private final Clock clock;

    public boolean configured() {
        return properties.vnpay().configured();
    }

    public String payUrl(String reference, long amount, String orderInfo, String returnUrl, String clientIp) {
        var now = clock.instant().atZone(VIETNAM);
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", properties.vnpay().tmnCode());
        params.put("vnp_Amount", Long.toString(amount * 100));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", reference);
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
        params.put("vnp_CreateDate", STAMP.format(now));
        params.put("vnp_ExpireDate", STAMP.format(now.plus(PAY_WITHIN)));
        String query = canonical(params);
        return properties.vnpay().payUrl() + "?" + query + "&vnp_SecureHash=" + sign(query);
    }

    /** True when the parameters carry VNPay's valid signature. */
    public boolean verify(Map<String, String> received) {
        if (!configured()) return false;
        Map<String, String> params = new TreeMap<>();
        received.forEach((key, value) -> {
            if (key.startsWith("vnp_") && !key.equals("vnp_SecureHash") && !key.equals("vnp_SecureHashType") && value != null && !value.isEmpty())
                params.put(key, value);
        });
        return GatewaySignatures.same(sign(canonical(params)), received.get("vnp_SecureHash"));
    }

    String sign(String data) {
        return GatewaySignatures.hmac("HmacSHA512", properties.vnpay().hashSecret(), data);
    }

    private static String canonical(Map<String, String> sorted) {
        StringJoiner joined = new StringJoiner("&");
        sorted.forEach((key, value) -> joined.add(encode(key) + "=" + encode(value)));
        return joined.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }
}
