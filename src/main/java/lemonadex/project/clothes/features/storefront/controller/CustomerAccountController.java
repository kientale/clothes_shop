package lemonadex.project.clothes.features.storefront.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lemonadex.project.clothes.common.dto.ApiResponse;
import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.features.storefront.dto.StorefrontRequests.*;
import lemonadex.project.clothes.features.storefront.dto.StorefrontResponses.*;
import lemonadex.project.clothes.features.storefront.service.CustomerAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** The signed-in customer's profile, password, address book and wishlist; the customer always comes from the JWT. */
@RestController @RequiredArgsConstructor @Validated @RequestMapping("/api/v1/me") @PreAuthorize("hasRole('CUSTOMER')")
public class CustomerAccountController {
    private final CustomerAccountService service;

    @GetMapping("/profile")
    ResponseEntity<ApiResponse<Profile>> profile(@AuthenticationPrincipal Jwt jwt) {
        return ok(service.profile(account(jwt)));
    }

    @PutMapping("/profile")
    ResponseEntity<ApiResponse<Profile>> updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest request) {
        return ok(service.updateProfile(account(jwt), request));
    }

    @PutMapping("/password")
    ResponseEntity<ApiResponse<Void>> changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordChangeRequest request) {
        service.changePassword(account(jwt), request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("PASSWORD_CHANGED", "Password updated", null));
    }

    @GetMapping("/addresses")
    ResponseEntity<ApiResponse<List<SavedAddress>>> addresses(@AuthenticationPrincipal Jwt jwt) {
        return ok(service.addresses(account(jwt)));
    }

    @PostMapping("/addresses")
    ResponseEntity<ApiResponse<SavedAddress>> addAddress(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("CREATE_SUCCESS", "Created", service.addAddress(account(jwt), request)));
    }

    @PutMapping("/addresses/{id}")
    ResponseEntity<ApiResponse<SavedAddress>> updateAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                            @Valid @RequestBody AddressRequest request) {
        return ok(service.updateAddress(account(jwt), id, request));
    }

    @PostMapping("/addresses/{id}/default")
    ResponseEntity<ApiResponse<SavedAddress>> makeDefault(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ok(service.makeDefault(account(jwt), id));
    }

    @DeleteMapping("/addresses/{id}")
    ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.deleteAddress(account(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/wishlist")
    ResponseEntity<ApiResponse<PageResponse<ProductCard>>> wishlist(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(60) int size) {
        return ok(service.wishlist(account(jwt), page, size));
    }

    /** Product ids in the wishlist, for marking hearts on product lists. */
    @GetMapping("/wishlist/ids")
    ResponseEntity<ApiResponse<List<UUID>>> wishlistIds(@AuthenticationPrincipal Jwt jwt) {
        return ok(service.wishlistIds(account(jwt)));
    }

    @PutMapping("/wishlist/{productId}")
    ResponseEntity<ApiResponse<List<UUID>>> addToWishlist(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId) {
        return ok(service.addToWishlist(account(jwt), productId));
    }

    @DeleteMapping("/wishlist/{productId}")
    ResponseEntity<ApiResponse<List<UUID>>> removeFromWishlist(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId) {
        return ok(service.removeFromWishlist(account(jwt), productId));
    }

    private static UUID account(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success("SUCCESS", "Success", data));
    }
}
