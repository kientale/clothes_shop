package lemonadex.project.clothes.features.review.dto;
import lemonadex.project.clothes.features.review.model.ReviewStatus;
import java.time.Instant;
import java.util.*;
public final class ReviewResponses {
    private ReviewResponses() {}
    public record ReviewResponse(UUID id, UUID productId, UUID customerId, UUID orderItemId, int rating, String comment,
            ReviewStatus status, List<String> images, String moderationNote, UUID moderatedBy, Instant moderatedAt,
            Instant createdAt, Instant updatedAt) {}
}
