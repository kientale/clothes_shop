package lemonadex.project.clothes.features.review.dto;

import jakarta.validation.constraints.*;
import lemonadex.project.clothes.features.review.model.ReviewStatus;
import java.util.*;

public final class ReviewRequests {
    private ReviewRequests() {}
    public record ReviewRequest(@NotNull UUID orderItemId, @NotNull @Min(1) @Max(5) Integer rating,
            @Size(max = 5000) String comment,
            @NotNull @Size(max = 10) List<@NotBlank @Size(max = 2048) @Pattern(regexp = "https?://[^\\s]+") String> images) {}
    public record ModerationRequest(@NotNull ReviewStatus status, @NotBlank @Size(max = 2000) String note) {}
}
