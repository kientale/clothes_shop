package lemonadex.project.clothes.features.carrier.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class CarrierDtos {
    private CarrierDtos() {}

    /** Delivery area of an address; the district is optional since the 2025 two-level administrative map. */
    public record ShippingArea(@NotBlank @Size(max = 100) String province, @Size(max = 100) String district,
            @Size(max = 100) String ward, @Size(max = 300) String street) {}

    /** One line of a carrier order. */
    public record Parcel(String name, String sku, int quantity) {}

    /** Admin: hand a confirmed order to GHTK. */
    public record CarrierShipmentRequest(@NotNull UUID orderId) {}

    public record CarrierStatus(String carrier, boolean configured) {}

    /** What the carrier returned for a new order. */
    public record CarrierOrder(String trackingCode, BigDecimal fee) {}
}
