package lemonadex.project.clothes.features.content.dto;
import lemonadex.project.clothes.features.content.model.*;
import java.time.Instant;
import java.util.*;
public final class ContentResponses {
    private ContentResponses() {}
    public record ArticleResponse(UUID id, String title, String slug, String thumbnailUrl, String content, ArticleType articleType,
            UUID authorId, ArticleStatus status, List<String> images, Instant publishedAt, Instant createdAt, Instant updatedAt) {}
    public record ArticleSummaryResponse(UUID id, String title, String slug, String thumbnailUrl, ArticleType articleType, Instant publishedAt) {}
    public record PublishedArticleResponse(UUID id, String title, String slug, String thumbnailUrl, String content,
            ArticleType articleType, List<String> images, Instant publishedAt, Instant updatedAt) {}
    public record PolicyResponse(UUID id, PolicyType policyType, String title, String content, int version,
            PolicyStatus status, UUID updatedBy, Instant activatedAt, Instant createdAt, Instant updatedAt) {}
    public record ActivePolicyResponse(PolicyType policyType, String title, String content, int version, Instant activatedAt, Instant updatedAt) {}
}
