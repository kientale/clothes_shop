package lemonadex.project.clothes.features.carrier.service;

import lemonadex.project.clothes.common.exception.ConflictException;
import lemonadex.project.clothes.features.carrier.config.GhtkProperties;
import lemonadex.project.clothes.features.carrier.dto.CarrierDtos.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import java.math.BigDecimal;
import java.util.*;

/** GHTK shipment API: fee quotes and order creation. Every call sends the shop token in the Token header. */
@Slf4j
@Service
public class GhtkClient {
    private final GhtkProperties properties;
    private final RestClient http = RestClient.create();

    public GhtkClient(GhtkProperties properties) {
        this.properties = properties;
    }

    public boolean configured() {
        return properties.configured();
    }

    /** Fee for a parcel of the given weight and declared value to the area; empty when GHTK cannot quote it. */
    @SuppressWarnings("unchecked")
    public Optional<BigDecimal> fee(ShippingArea area, int weightGrams, BigDecimal value) {
        var uri = UriComponentsBuilder.fromUriString(properties.baseUrl() + "/services/shipment/fee")
                .queryParam("pick_province", properties.pickProvince())
                .queryParamIfPresent("pick_district", Optional.ofNullable(blankToNull(properties.pickDistrict())))
                .queryParam("pick_address", properties.pickAddress())
                .queryParam("province", area.province())
                .queryParamIfPresent("district", Optional.ofNullable(blankToNull(area.district())))
                .queryParamIfPresent("ward", Optional.ofNullable(blankToNull(area.ward())))
                .queryParamIfPresent("address", Optional.ofNullable(blankToNull(area.street())))
                .queryParam("weight", weightGrams)
                .queryParam("value", value.toBigInteger())
                .queryParam("deliver_option", "none")
                .encode().build().toUri();
        try {
            Map<String, Object> answer = http.get().uri(uri).header("Token", properties.token()).retrieve().body(Map.class);
            if (answer != null && Boolean.TRUE.equals(answer.get("success")) && answer.get("fee") instanceof Map<?, ?> fee
                    && fee.get("fee") instanceof Number amount && !Boolean.FALSE.equals(fee.get("delivery"))) {
                return Optional.of(BigDecimal.valueOf(amount.longValue()));
            }
            log.info("GHTK gave no fee for {}: {}", area.province(), answer);
        } catch (RestClientException ex) {
            log.warn("GHTK fee request failed: {}", ex.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Registers the order with GHTK. The shop pays the shipping (is_freeship) because the customer already paid
     * the fee on the order; GHTK collects {@code codAmount} from the recipient.
     */
    @SuppressWarnings("unchecked")
    public CarrierOrder createOrder(String orderCode, String recipientName, String recipientPhone, ShippingArea area,
                                    List<Parcel> parcels, BigDecimal codAmount, BigDecimal value, String note) {
        List<Map<String, Object>> products = parcels.stream().map(p -> {
            Map<String, Object> product = new LinkedHashMap<>();
            product.put("name", p.name());
            product.put("product_code", p.sku());
            product.put("quantity", p.quantity());
            product.put("weight", properties.itemWeightGrams() / 1000.0);
            return product;
        }).toList();
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", orderCode);
        order.put("pick_name", properties.pickName());
        order.put("pick_tel", properties.pickTel());
        order.put("pick_address", properties.pickAddress());
        order.put("pick_province", properties.pickProvince());
        if (blankToNull(properties.pickDistrict()) != null) order.put("pick_district", properties.pickDistrict());
        if (blankToNull(properties.pickWard()) != null) order.put("pick_ward", properties.pickWard());
        order.put("name", recipientName);
        order.put("tel", recipientPhone);
        order.put("address", area.street() == null || area.street().isBlank() ? area.ward() : area.street());
        order.put("province", area.province());
        if (blankToNull(area.district()) != null) order.put("district", area.district());
        if (blankToNull(area.ward()) != null) order.put("ward", area.ward());
        order.put("hamlet", "Khác");
        order.put("is_freeship", "1");
        order.put("pick_money", codAmount.toBigInteger());
        order.put("value", value.toBigInteger());
        if (note != null && !note.isBlank()) order.put("note", note.length() > 120 ? note.substring(0, 120) : note);
        try {
            Map<String, Object> answer = http.post().uri(properties.baseUrl() + "/services/shipment/order")
                    .header("Token", properties.token()).body(Map.of("products", products, "order", order)).retrieve().body(Map.class);
            if (answer != null && Boolean.TRUE.equals(answer.get("success")) && answer.get("order") instanceof Map<?, ?> created
                    && created.get("label") instanceof String label) {
                BigDecimal fee = created.get("fee") instanceof Number n ? BigDecimal.valueOf(n.longValue()) : null;
                return new CarrierOrder(label, fee);
            }
            log.warn("GHTK refused order {}: {}", orderCode, answer);
            String message = answer != null && answer.get("message") instanceof String m ? m : "GHTK did not accept the order";
            throw new ConflictException("CARRIER_REJECTED", message);
        } catch (RestClientException ex) {
            log.warn("GHTK order request failed: {}", ex.getMessage());
            throw new ConflictException("CARRIER_UNAVAILABLE", "GHTK could not be reached; try again later");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
