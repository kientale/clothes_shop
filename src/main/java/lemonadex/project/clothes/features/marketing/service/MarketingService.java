package lemonadex.project.clothes.features.marketing.service;

import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.common.exception.*;
import lemonadex.project.clothes.common.util.ListFilters;
import lemonadex.project.clothes.features.inventory.service.InventoryService;
import lemonadex.project.clothes.features.marketing.dto.MarketingRequests.*;
import lemonadex.project.clothes.features.marketing.dto.MarketingResponses.*;
import lemonadex.project.clothes.features.marketing.model.*;
import lemonadex.project.clothes.features.marketing.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static lemonadex.project.clothes.features.marketing.service.MarketingValidation.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class MarketingService {
    private final CouponRepository coupons;
    private final CouponUsageRepository usages;
    private final PromotionRepository promotions;
    private final FlashSaleRepository flashes;
    private final FlashSaleItemRepository flashItems;
    private final BannerRepository banners;
    private final MarketingQueryRepository queries;
    private final InventoryService inventory;

    public PageResponse<CouponResponse> coupons(String search, MarketingStatus status, int page, int size) {
        return PageResponse.from(coupons.findAll(ListFilters.where(search, new String[]{"code", "name"}, ListFilters.values("status", status), null, null), page(page, size)).map(this::couponResponse));
    }
    public CouponResponse coupon(UUID id) { return couponResponse(couponRequired(id)); }
    @Transactional public CouponResponse createCoupon(CouponRequest r) { inventory.lock(); return saveCoupon(new Coupon(), r); }
    @Transactional public CouponResponse updateCoupon(UUID id, CouponRequest r) { inventory.lock(); return saveCoupon(couponRequired(id), r); }
    @Transactional public void deleteCoupon(UUID id) { inventory.lock(); coupons.delete(couponRequired(id)); coupons.flush(); }
    private CouponResponse saveCoupon(Coupon c, CouponRequest r) {
        period(r.startAt(), r.endAt()); discount(r.discountType(), r.discountValue());
        String code = r.code().toUpperCase(Locale.ROOT);
        if (queries.duplicateCoupon(code, c.getId())) throw new ConflictException("COUPON_CODE_EXISTS", "Coupon code already exists, including archived coupons");
        if (r.usageLimit() != null && r.usageLimit() < c.getUsedCount()) throw new ConflictException("COUPON_LIMIT_BELOW_USAGE", "Limit cannot be below current usage");
        c.setCode(code); c.setName(r.name().strip()); c.setDiscountType(r.discountType()); c.setDiscountValue(r.discountValue());
        c.setMaxDiscount(r.maxDiscount()); c.setMinimumOrderValue(r.minimumOrderValue()); c.setUsageLimit(r.usageLimit());
        c.setUsageLimitPerCustomer(r.usageLimitPerCustomer()); c.setStartAt(r.startAt()); c.setEndAt(r.endAt()); c.setStatus(r.status());
        return couponResponse(coupons.saveAndFlush(c));
    }
    public PageResponse<CouponUsageResponse> usages(UUID id, int page, int size) {
        couponRequired(id);
        return PageResponse.from(usages.findAll((root, query, cb) -> cb.equal(root.get("couponId"), id),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("usedAt"), Sort.Order.asc("id"))))
                .map(u -> new CouponUsageResponse(u.getId(), u.getCouponId(), u.getCustomerId(), u.getOrderId(), u.getDiscountAmount(), u.getUsedAt(), u.isReleased(), u.getReleasedAt())));
    }
    public PageResponse<PromotionResponse> promotions(String search, MarketingStatus status, int page, int size) {
        return PageResponse.from(promotions.findAll(ListFilters.where(search, new String[]{"name"}, ListFilters.values("status", status), null, null), page(page, size)).map(this::promotionResponse));
    }
    public PromotionResponse promotion(UUID id) { return promotionResponse(promotionRequired(id)); }
    @Transactional public PromotionResponse createPromotion(PromotionRequest r) { inventory.lock(); return savePromotion(new Promotion(), r); }
    @Transactional public PromotionResponse updatePromotion(UUID id, PromotionRequest r) { inventory.lock(); return savePromotion(promotionRequired(id), r); }
    @Transactional public void deletePromotion(UUID id) { inventory.lock(); promotions.delete(promotionRequired(id)); promotions.flush(); }
    private PromotionResponse savePromotion(Promotion p, PromotionRequest r) {
        period(r.startAt(), r.endAt()); discount(r.discountType(), r.discountValue()); unique(r.productIds()); unique(r.categoryIds());
        boolean valid = switch (r.promotionType()) {
            case ALL_PRODUCTS -> r.productIds().isEmpty() && r.categoryIds().isEmpty();
            case PRODUCT_DISCOUNT -> !r.productIds().isEmpty() && r.categoryIds().isEmpty();
            case CATEGORY_DISCOUNT -> r.productIds().isEmpty() && !r.categoryIds().isEmpty();
        };
        if (!valid) throw new BadRequestException("INVALID_PROMOTION_SCOPE", "Choose the references matching the promotion type");
        r.productIds().forEach(id -> reference("products", id)); r.categoryIds().forEach(id -> reference("categories", id));
        p.setName(r.name().strip()); p.setDescription(text(r.description())); p.setPromotionType(r.promotionType());
        p.setDiscountType(r.discountType()); p.setDiscountValue(r.discountValue()); p.setPriority(r.priority());
        p.setStartAt(r.startAt()); p.setEndAt(r.endAt()); p.setStatus(r.status());
        p.getProductIds().clear(); p.getProductIds().addAll(r.productIds()); p.getCategoryIds().clear(); p.getCategoryIds().addAll(r.categoryIds());
        p.setUpdatedAt(java.time.Instant.now());
        return promotionResponse(promotions.saveAndFlush(p));
    }
    public PageResponse<FlashSaleResponse> flashes(String search, MarketingStatus status, int page, int size) {
        return PageResponse.from(flashes.findAll(ListFilters.where(search, new String[]{"name"}, ListFilters.values("status", status), null, null), page(page, size)).map(this::flashResponse));
    }
    public FlashSaleResponse flash(UUID id) { return flashResponse(flashRequired(id)); }
    @Transactional public FlashSaleResponse createFlash(FlashSaleRequest r) { inventory.lock(); return saveFlash(new FlashSale(), r); }
    @Transactional public FlashSaleResponse updateFlash(UUID id, FlashSaleRequest r) { inventory.lock(); return saveFlash(flashRequired(id), r); }
    @Transactional public void deleteFlash(UUID id) { inventory.lock(); flashes.delete(flashRequired(id)); flashes.flush(); }
    private FlashSaleResponse saveFlash(FlashSale f, FlashSaleRequest r) {
        period(r.startAt(), r.endAt()); unique(r.items().stream().map(FlashItemRequest::productVariantId).toList());
        Map<UUID, FlashSaleItem> existing = new HashMap<>(); f.getItems().forEach(i -> existing.put(i.getProductVariantId(), i));
        Set<UUID> requested = new HashSet<>();
        for (FlashItemRequest line : r.items()) {
            requested.add(line.productVariantId());
            var variant = inventory.saleVariant(line.productVariantId());
            if (line.flashPrice().compareTo(variant.price()) >= 0) throw new BadRequestException("INVALID_FLASH_PRICE", "Flash price must be below the current variant price");
            FlashSaleItem i = existing.get(line.productVariantId());
            if (i == null) { i = new FlashSaleItem(); i.setSale(f); i.setProductVariantId(line.productVariantId()); f.getItems().add(i); }
            if (line.quantityLimit() < i.getSoldQuantity()) throw new ConflictException("FLASH_LIMIT_BELOW_USAGE", "Quantity limit cannot be below current sales");
            i.setFlashPrice(line.flashPrice()); i.setQuantityLimit(line.quantityLimit());
        }
        for (FlashSaleItem i : List.copyOf(f.getItems())) if (!requested.contains(i.getProductVariantId())) {
            // Historical allocations may reference even a released item; retain all sold item identities.
            if (flashItems.hasAllocations(i.getId())) throw new ConflictException("FLASH_ITEM_CANNOT_BE_REMOVED", "Allocated flash items retain their identities; deactivate this sale instead");
            f.getItems().remove(i); flashItems.delete(i);
        }
        f.setName(r.name().strip()); f.setStartAt(r.startAt()); f.setEndAt(r.endAt()); f.setStatus(r.status()); f.setUpdatedAt(java.time.Instant.now());
        return flashResponse(flashes.saveAndFlush(f));
    }
    public PageResponse<BannerResponse> banners(String search, MarketingStatus status, String position, int page, int size) {
        return PageResponse.from(banners.findAll(ListFilters.where(search, new String[]{"title"}, ListFilters.values("status", status, "position", position), null, null),
                PageRequest.of(page, size, Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.desc("createdAt"), Sort.Order.asc("id")))).map(this::bannerResponse));
    }
    public BannerResponse banner(UUID id) { return bannerResponse(bannerRequired(id)); }
    @Transactional public BannerResponse createBanner(BannerRequest r) { inventory.lock(); return saveBanner(new Banner(), r); }
    @Transactional public BannerResponse updateBanner(UUID id, BannerRequest r) { inventory.lock(); return saveBanner(bannerRequired(id), r); }
    @Transactional public void deleteBanner(UUID id) { inventory.lock(); banners.delete(bannerRequired(id)); banners.flush(); }
    private BannerResponse saveBanner(Banner b, BannerRequest r) {
        period(r.startAt(), r.endAt()); b.setTitle(r.title().strip()); b.setImageUrl(r.imageUrl()); b.setLinkUrl(r.linkUrl());
        b.setPosition(r.position().toUpperCase(Locale.ROOT)); b.setSortOrder(r.sortOrder()); b.setStartAt(r.startAt()); b.setEndAt(r.endAt()); b.setStatus(r.status());
        return bannerResponse(banners.saveAndFlush(b));
    }
    private void reference(String table, UUID id) { if (!queries.exists(table, id)) throw new ResourceNotFoundException(table); }
    private Coupon couponRequired(UUID id) { return coupons.findById(id).orElseThrow(() -> new ResourceNotFoundException("Coupon")); }
    private Promotion promotionRequired(UUID id) { return promotions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Promotion")); }
    private FlashSale flashRequired(UUID id) { return flashes.findById(id).orElseThrow(() -> new ResourceNotFoundException("Flash sale")); }
    private Banner bannerRequired(UUID id) { return banners.findById(id).orElseThrow(() -> new ResourceNotFoundException("Banner")); }
    private Pageable page(int page, int size) { return PageRequest.of(page, size, ListFilters.NEWEST); }
    private CouponResponse couponResponse(Coupon c) { return new CouponResponse(c.getId(), c.getCode(), c.getName(), c.getDiscountType(), c.getDiscountValue(), c.getMaxDiscount(), c.getMinimumOrderValue(), c.getUsageLimit(), c.getUsageLimitPerCustomer(), c.getUsedCount(), c.getStartAt(), c.getEndAt(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt()); }
    private PromotionResponse promotionResponse(Promotion p) { return new PromotionResponse(p.getId(), p.getName(), p.getDescription(), p.getPromotionType(), p.getDiscountType(), p.getDiscountValue(), p.getStartAt(), p.getEndAt(), p.getPriority(), p.getStatus(), p.getProductIds().stream().sorted().toList(), p.getCategoryIds().stream().sorted().toList(), p.getCreatedAt(), p.getUpdatedAt()); }
    private FlashSaleResponse flashResponse(FlashSale f) { return new FlashSaleResponse(f.getId(), f.getName(), f.getStartAt(), f.getEndAt(), f.getStatus(), f.getItems().stream().map(i -> new FlashItemResponse(i.getId(), i.getProductVariantId(), i.getFlashPrice(), i.getQuantityLimit(), i.getSoldQuantity(), i.getQuantityLimit() - i.getSoldQuantity())).toList(), f.getCreatedAt(), f.getUpdatedAt()); }
    private BannerResponse bannerResponse(Banner b) { return new BannerResponse(b.getId(), b.getTitle(), b.getImageUrl(), b.getLinkUrl(), b.getPosition(), b.getSortOrder(), b.getStartAt(), b.getEndAt(), b.getStatus(), b.getCreatedAt(), b.getUpdatedAt()); }
}
