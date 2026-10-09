package lemonadex.project.clothes.features.review.service;

import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.common.exception.*;
import lemonadex.project.clothes.common.util.ListFilters;
import lemonadex.project.clothes.features.inventory.service.InventoryService;
import lemonadex.project.clothes.features.review.dto.ReviewRequests.*;
import lemonadex.project.clothes.features.review.dto.ReviewResponses.*;
import lemonadex.project.clothes.features.review.model.*;
import lemonadex.project.clothes.features.review.repository.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class ReviewService {
    private final ProductReviewRepository reviews;
    private final InventoryService inventory;
    private final Clock clock;
    public PageResponse<ReviewResponse> list(String search, ReviewStatus status, UUID productId, UUID customerId, Integer rating, int page, int size) {
        return PageResponse.from(reviews.findAll(ListFilters.where(search, new String[]{"comment"},
                ListFilters.values("status", status, "productId", productId, "customerId", customerId, "rating", rating), null, null),
                PageRequest.of(page, size, ListFilters.NEWEST)).map(this::response));
    }
    public ReviewResponse get(UUID id) { return response(required(id)); }
    @Transactional public ReviewResponse create(UUID customerId, ReviewRequest r) {
        inventory.lock();
        UUID productId = reviews.verifiedProduct(r.orderItemId(), customerId).orElseThrow(() -> new ConflictException("REVIEW_REQUIRES_PURCHASE", "Review requires this customer's delivered, retained order item"));
        if (reviews.alreadyReviewed(r.orderItemId())) throw new ConflictException("ORDER_ITEM_ALREADY_REVIEWED", "One review per order item, including archived reviews");
        if (new HashSet<>(r.images()).size() != r.images().size()) throw new BadRequestException("DUPLICATE_IMAGES", "Images must be unique");
        ProductReview p = new ProductReview(); p.setProductId(productId); p.setCustomerId(customerId); p.setOrderItemId(r.orderItemId());
        p.setRating(r.rating().shortValue()); p.setComment(r.comment() == null || r.comment().isBlank() ? null : r.comment().strip()); p.getImages().addAll(r.images());
        return response(reviews.saveAndFlush(p));
    }
    @Transactional public ReviewResponse submit(UUID accountId, ReviewRequest r) { return create(ownCustomer(accountId), r); }
    public PageResponse<ReviewResponse> own(UUID accountId, int page, int size) { return list(null, null, null, ownCustomer(accountId), null, page, size); }
    @Transactional public ReviewResponse moderate(UUID id, ModerationRequest r, UUID actor) {
        inventory.lock(); ProductReview p = required(id);
        p.setStatus(r.status()); p.setModerationNote(r.note().strip()); p.setModeratedBy(actor); p.setModeratedAt(clock.instant()); reviews.flush(); return response(p);
    }
    @Transactional public void delete(UUID id) { inventory.lock(); required(id); reviews.archive(id); }
    private UUID ownCustomer(UUID accountId) { return reviews.ownCustomer(accountId).orElseThrow(() -> new ResourceNotFoundException("Active customer profile")); }
    private ProductReview required(UUID id) { return reviews.findById(id).orElseThrow(() -> new ResourceNotFoundException("Review")); }
    private ReviewResponse response(ProductReview p) { return new ReviewResponse(p.getId(), p.getProductId(), p.getCustomerId(), p.getOrderItemId(), p.getRating(), p.getComment(), p.getStatus(), List.copyOf(p.getImages()), p.getModerationNote(), p.getModeratedBy(), p.getModeratedAt(), p.getCreatedAt(), p.getUpdatedAt()); }
}
