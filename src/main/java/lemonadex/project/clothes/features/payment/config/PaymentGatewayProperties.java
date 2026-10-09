package lemonadex.project.clothes.features.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Keys of the online payment gateways. A gateway is usable only when all of its keys are set; the matching
 * payment method (VNPAY, MOMO) must also be switched on in the payment settings.
 *
 * @param callbackBaseUrl public origin of this backend, used for the MoMo IPN address (VNPay's IPN address is
 *                        registered in the VNPay merchant portal instead)
 */
@ConfigurationProperties("app.payments")
public record PaymentGatewayProperties(String callbackBaseUrl, Vnpay vnpay, Momo momo) {
    public PaymentGatewayProperties {
        callbackBaseUrl = callbackBaseUrl == null ? "" : callbackBaseUrl.strip().replaceAll("/+$", "");
        if (vnpay == null) vnpay = new Vnpay(null, null, null);
        if (momo == null) momo = new Momo(null, null, null, null);
    }

    public record Vnpay(String tmnCode, String hashSecret, String payUrl) {
        public Vnpay {
            if (payUrl == null || payUrl.isBlank()) payUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
        }

        public boolean configured() {
            return tmnCode != null && !tmnCode.isBlank() && hashSecret != null && !hashSecret.isBlank();
        }
    }

    public record Momo(String partnerCode, String accessKey, String secretKey, String endpoint) {
        public Momo {
            if (endpoint == null || endpoint.isBlank()) endpoint = "https://test-payment.momo.vn/v2/gateway/api/create";
        }

        public boolean configured() {
            return partnerCode != null && !partnerCode.isBlank() && accessKey != null && !accessKey.isBlank()
                    && secretKey != null && !secretKey.isBlank();
        }
    }

    @Configuration
    @EnableConfigurationProperties(PaymentGatewayProperties.class)
    static class Registration {}
}
