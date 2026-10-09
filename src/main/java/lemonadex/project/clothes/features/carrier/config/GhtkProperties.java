package lemonadex.project.clothes.features.carrier.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Giao Hàng Tiết Kiệm (GHTK). Without a token the GHTK shipping method falls back to its fixed base fee and
 * shipments are entered by hand.
 *
 * @param token           API token from the GHTK shop dashboard
 * @param baseUrl         https://services.giaohangtietkiem.vn in production, https://services-staging.ghtklab.com for tests
 * @param webhookSecret   value GHTK must send as ?hash= on status callbacks (put it in the webhook URL registered at GHTK)
 * @param itemWeightGrams weight assumed per item, since products carry no weight
 */
@ConfigurationProperties("app.carriers.ghtk")
public record GhtkProperties(String token, String baseUrl, String webhookSecret, String pickName, String pickTel,
        String pickAddress, String pickProvince, String pickDistrict, String pickWard, Integer itemWeightGrams) {
    public GhtkProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://services-staging.ghtklab.com";
        baseUrl = baseUrl.strip().replaceAll("/+$", "");
        if (itemWeightGrams == null || itemWeightGrams <= 0) itemWeightGrams = 300;
    }

    public boolean configured() {
        return present(token) && present(pickName) && present(pickTel) && present(pickAddress) && present(pickProvince);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    @Configuration
    @EnableConfigurationProperties(GhtkProperties.class)
    static class Registration {}
}
