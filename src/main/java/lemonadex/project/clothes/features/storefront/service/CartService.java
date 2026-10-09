package lemonadex.project.clothes.features.storefront.service;

import lemonadex.project.clothes.common.exception.ResourceNotFoundException;
import lemonadex.project.clothes.features.storefront.dto.StorefrontRequests.CartRequest;
import lemonadex.project.clothes.features.storefront.dto.StorefrontRequests.CheckoutItem;
import lemonadex.project.clothes.features.storefront.dto.StorefrontResponses.CartLine;
import lemonadex.project.clothes.features.storefront.repository.CartRepository;
import lemonadex.project.clothes.features.storefront.repository.StorefrontRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/**
 * The signed-in customer's cart on the server, so it follows them from phone to laptop. The shop keeps a copy in
 * the browser too and sends the whole cart after each change; prices are never taken from here.
 */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class CartService {
    private final CartRepository carts;
    private final StorefrontRepository store;

    public List<CartLine> get(UUID accountId) {
        return carts.lines(customer(accountId)).stream().map(r -> new CartLine(r.variantId(), r.productId(), r.slug(), r.name(), r.imageUrl(),
                r.colorName(), r.sizeName(), r.sku(), r.price(), r.quantity(), Math.max(0, r.available()), r.sellable())).toList();
    }

    /** Replaces the saved cart; the same variant twice counts once with the summed quantity. */
    @Transactional
    public List<CartLine> replace(UUID accountId, CartRequest request) {
        UUID customer = customer(accountId);
        Map<UUID, Integer> lines = new LinkedHashMap<>();
        for (CheckoutItem item : request.items()) lines.merge(item.productVariantId(), item.quantity(), (a, b) -> Math.min(1000, a + b));
        carts.clear(customer);
        lines.forEach((variant, quantity) -> carts.put(customer, variant, quantity));
        return get(accountId);
    }

    private UUID customer(UUID accountId) {
        return store.customerOf(accountId).orElseThrow(() -> new ResourceNotFoundException("Active customer profile"));
    }
}
