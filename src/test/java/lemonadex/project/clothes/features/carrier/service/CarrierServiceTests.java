package lemonadex.project.clothes.features.carrier.service;

import lemonadex.project.clothes.features.carrier.dto.CarrierDtos.ShippingArea;
import lemonadex.project.clothes.features.order.model.ShipmentStatus;
import org.junit.jupiter.api.Test;
import java.util.List;

import static lemonadex.project.clothes.features.order.model.ShipmentStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Address splitting and GHTK status handling, without calling GHTK. */
class CarrierServiceTests {
    @Test
    void savedAddressesSplitIntoTheAreaGhtkNeeds() {
        assertThat(CarrierService.area("214 Nguyễn Trãi, Phường Chợ Quán, Thành phố Hồ Chí Minh"))
                .isEqualTo(new ShippingArea("Thành phố Hồ Chí Minh", null, "Phường Chợ Quán", "214 Nguyễn Trãi"));
        assertThat(CarrierService.area("Lầu 3, 12 Lê Lợi, Phường Bến Nghé, Quận 1, TP.HCM"))
                .isEqualTo(new ShippingArea("TP.HCM", "Quận 1", "Phường Bến Nghé", "Lầu 3, 12 Lê Lợi"));
        assertThat(CarrierService.area("Hà Nội")).isEqualTo(new ShippingArea("Hà Nội", null, null, null));
    }

    @Test
    void ghtkStatusesMapToShipmentStates() {
        assertThat(CarrierService.ghtkStatus("3")).isEqualTo(SHIPPED);
        assertThat(CarrierService.ghtkStatus("10")).isEqualTo(IN_TRANSIT);
        assertThat(CarrierService.ghtkStatus("6")).isEqualTo(DELIVERED);
        assertThat(CarrierService.ghtkStatus("9")).isEqualTo(FAILED);
        assertThat(CarrierService.ghtkStatus("21")).isEqualTo(RETURNED);
        assertThat(CarrierService.ghtkStatus("-1")).isEqualTo(CANCELLED);
        assertThat(CarrierService.ghtkStatus("2")).isNull();
    }

    @Test
    void callbacksOnlyTakeTransitionsTheWorkflowAllows() {
        assertThat(CarrierService.path(PENDING, DELIVERED)).containsExactly(SHIPPED, DELIVERED);
        assertThat(CarrierService.path(SHIPPED, IN_TRANSIT)).containsExactly(IN_TRANSIT);
        assertThat(CarrierService.path(FAILED, DELIVERED)).containsExactly(DELIVERED);
        assertThat(CarrierService.path(DELIVERED, IN_TRANSIT)).isEmpty();
        assertThat(CarrierService.path(DELIVERED, DELIVERED)).isEmpty();
        assertThat(CarrierService.path(SHIPPED, CANCELLED)).isEmpty();
        List<ShipmentStatus> late = CarrierService.path(IN_TRANSIT, RETURNED);
        assertThat(late).containsExactly(RETURNED);
    }
}
