package lemonadex.project.clothes.features.carrier.controller;

import jakarta.validation.Valid;
import lemonadex.project.clothes.common.dto.ApiResponse;
import lemonadex.project.clothes.features.carrier.dto.CarrierDtos.*;
import lemonadex.project.clothes.features.carrier.service.CarrierService;
import lemonadex.project.clothes.features.order.dto.OrderResponses.ShipmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController @RequiredArgsConstructor
public class CarrierController {
    private final CarrierService service;

    @GetMapping("/api/v1/admin/carriers/ghtk")
    @PreAuthorize("hasAuthority('SHIPMENT_READ')")
    ResponseEntity<ApiResponse<CarrierStatus>> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("SUCCESS", "Carrier status", service.status()));
    }

    /** Hands a confirmed order to GHTK and records the shipment with GHTK's tracking code. */
    @PostMapping("/api/v1/admin/carriers/ghtk/shipments")
    @PreAuthorize("hasAuthority('SHIPMENT_WRITE')")
    ResponseEntity<ApiResponse<ShipmentResponse>> ship(@Valid @RequestBody CarrierShipmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("CREATE_SHIPMENT_SUCCESS", "Shipment created at GHTK", service.ship(request.orderId())));
    }

    /** GHTK status callback (form-encoded). The URL registered at GHTK carries ?hash=<webhook secret>. */
    @PostMapping(path = "/api/v1/carriers/ghtk/webhook", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<Void> ghtkForm(@RequestParam("hash") String hash, @RequestParam MultiValueMap<String, String> form) {
        service.ghtkCallback(hash, form.toSingleValueMap());
        return ResponseEntity.ok().build();
    }

    /** The same callback when GHTK sends JSON. */
    @PostMapping(path = "/api/v1/carriers/ghtk/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> ghtkJson(@RequestParam("hash") String hash, @RequestBody Map<String, Object> body) {
        Map<String, String> fields = new LinkedHashMap<>();
        body.forEach((key, value) -> fields.put(key, value == null ? null : String.valueOf(value)));
        service.ghtkCallback(hash, fields);
        return ResponseEntity.ok().build();
    }
}
