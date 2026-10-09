package lemonadex.project.clothes.features.payment.controller;

import jakarta.validation.constraints.Pattern;
import lemonadex.project.clothes.common.dto.ApiResponse;
import lemonadex.project.clothes.features.payment.dto.PaymentGatewayResponses.*;
import lemonadex.project.clothes.features.payment.service.OnlinePaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls from the payment gateways (IPN) and from the shop's "payment result" page. No authentication: every
 * call is trusted only after its gateway signature checks out.
 */
@RestController @RequiredArgsConstructor @Validated @RequestMapping("/api/v1/payments")
public class PaymentCallbackController {
    private final OnlinePaymentService service;

    /** VNPay server-to-server notification (registered in the VNPay merchant portal). */
    @GetMapping("/vnpay/ipn")
    VnpayIpnAnswer vnpayIpn(@RequestParam Map<String, String> params) {
        try {
            return service.vnpayIpn(params);
        } catch (RuntimeException ex) {
            return new VnpayIpnAnswer("99", "Unknown error");
        }
    }

    /** MoMo server-to-server notification; MoMo expects 204 when it was received. */
    @PostMapping("/momo/ipn")
    ResponseEntity<Void> momoIpn(@RequestBody Map<String, Object> body) {
        Map<String, String> fields = new LinkedHashMap<>();
        body.forEach((key, value) -> fields.put(key, value == null ? null : String.valueOf(value)));
        return service.momoIpn(fields) ? ResponseEntity.noContent().build() : ResponseEntity.badRequest().build();
    }

    @GetMapping("/gateways")
    @PreAuthorize("hasAuthority('SETTINGS_PAYMENT_READ')")
    ResponseEntity<ApiResponse<List<GatewayStatus>>> gateways() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("SUCCESS", "Payment gateways", service.gateways()));
    }

    /** The parameters the gateway appended to the shop's return address, forwarded by the shop page. */
    @PostMapping("/{gateway}/return")
    ResponseEntity<ApiResponse<PaymentResult>> returned(@PathVariable @Pattern(regexp = "(?i)vnpay|momo") String gateway,
                                                        @RequestBody Map<String, String> params) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("SUCCESS", "Payment result", service.returned(gateway, params)));
    }
}
