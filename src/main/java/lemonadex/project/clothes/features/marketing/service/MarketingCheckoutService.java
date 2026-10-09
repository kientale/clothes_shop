package lemonadex.project.clothes.features.marketing.service;

import lemonadex.project.clothes.common.exception.*;
import lemonadex.project.clothes.features.marketing.dto.MarketingResponses.*;
import lemonadex.project.clothes.features.marketing.model.*;
import lemonadex.project.clothes.features.marketing.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static lemonadex.project.clothes.features.marketing.service.MarketingValidation.amount;

/** Called only after InventoryService.lock(): checkout, campaign edits and cancellation share one transaction lock. */
@Service @RequiredArgsConstructor @Transactional(propagation = Propagation.MANDATORY)
public class MarketingCheckoutService {
    private final CouponRepository coupons;
    private final PromotionRepository promotions;
    private final FlashSaleRepository flashes;
    private final FlashSaleItemRepository flashItems;
    private final MarketingQueryRepository queries;
    private final Clock clock;

    public MarketingLineQuote line(UUID productId, UUID variantId, BigDecimal price, int quantity, boolean enabled) {
        if (!enabled) return new MarketingLineQuote(null, null, BigDecimal.ZERO);
        Instant now = clock.instant();
        UUID categoryId = queries.category(productId);
        var eligible = promotions.findAll((root, query, cb) -> cb.and(cb.equal(root.get("status"), MarketingStatus.ACTIVE),
                cb.lessThanOrEqualTo(root.get("startAt"), now), cb.greaterThan(root.get("endAt"), now))).stream()
                .filter(p -> p.getPromotionType() == PromotionType.ALL_PRODUCTS || p.getProductIds().contains(productId) || p.getCategoryIds().contains(categoryId))
                .sorted(Comparator.comparingInt(Promotion::getPriority).reversed()
                        .thenComparing(p -> amount(p.getDiscountType(), p.getDiscountValue(), price), Comparator.reverseOrder()).thenComparing(Promotion::getId)).toList();
        Promotion promotion = eligible.isEmpty() ? null : eligible.getFirst();
        BigDecimal unitDiscount = promotion == null ? BigDecimal.ZERO : amount(promotion.getDiscountType(), promotion.getDiscountValue(), price);
        FlashSaleItem flash = flashes.findAll((root, query, cb) -> cb.and(cb.equal(root.get("status"), MarketingStatus.ACTIVE),
                cb.lessThanOrEqualTo(root.get("startAt"), now), cb.greaterThan(root.get("endAt"), now))).stream()
                .flatMap(s -> s.getItems().stream()).filter(i -> i.getProductVariantId().equals(variantId) && i.getFlashPrice().compareTo(price) < 0)
                .sorted(Comparator.comparing(FlashSaleItem::getFlashPrice).thenComparing(FlashSaleItem::getId)).findFirst().orElse(null);
        if (flash != null && price.subtract(flash.getFlashPrice()).compareTo(unitDiscount) > 0) {
            if (quantity > flash.getQuantityLimit() - flash.getSoldQuantity()) throw new ConflictException("FLASH_SALE_EXHAUSTED", "Flash sale quantity is insufficient");
            flash.setSoldQuantity(flash.getSoldQuantity() + quantity);
            flashItems.flush();
            return new MarketingLineQuote(flash.getId(), null, price.subtract(flash.getFlashPrice()).multiply(BigDecimal.valueOf(quantity)));
        }
        return new MarketingLineQuote(null, unitDiscount.signum() > 0 ? promotion.getId() : null, unitDiscount.multiply(BigDecimal.valueOf(quantity)));
    }
    public CouponQuote coupon(String code, UUID customerId, BigDecimal merchandise) {
        if (code == null || code.isBlank()) return new CouponQuote(null, BigDecimal.ZERO);
        Coupon c = coupons.findByCodeIgnoreCase(code.strip()).orElseThrow(() -> new ResourceNotFoundException("Coupon"));
        Instant now = clock.instant();
        if (c.getStatus() != MarketingStatus.ACTIVE || now.isBefore(c.getStartAt()) || !now.isBefore(c.getEndAt()))
            throw new ConflictException("COUPON_NOT_ACTIVE", "Coupon is outside its active period");
        if (merchandise.compareTo(c.getMinimumOrderValue()) < 0) throw new ConflictException("COUPON_MINIMUM_NOT_MET", "Merchandise total is below the minimum");
        if (c.getUsageLimit() != null && c.getUsedCount() >= c.getUsageLimit() || queries.customerUses(c.getId(), customerId) >= c.getUsageLimitPerCustomer())
            throw new ConflictException("COUPON_LIMIT_REACHED", "Coupon usage limit has been reached");
        BigDecimal discount = amount(c.getDiscountType(), c.getDiscountValue(), merchandise);
        if (c.getMaxDiscount() != null) discount = discount.min(c.getMaxDiscount());
        if (discount.signum() == 0) throw new ConflictException("COUPON_NO_DISCOUNT", "Coupon requires a positive merchandise value");
        c.setUsedCount(c.getUsedCount() + 1); coupons.flush();
        return new CouponQuote(c.getId(), discount);
    }
    public void recordCoupon(CouponQuote quote, UUID customerId, UUID orderId) {
        if (quote.couponId() != null) queries.recordCoupon(quote.couponId(), customerId, orderId, quote.discountAmount());
    }
    public void recordLine(MarketingLineQuote quote, UUID itemId, int quantity) {
        if (quote.flashSaleItemId() != null || quote.promotionId() != null)
            queries.recordLine(itemId, quote.flashSaleItemId(), quote.promotionId(), quantity, quote.discountAmount());
    }
    public void release(UUID orderId) { queries.release(orderId); }
}
