package lemonadex.project.clothes.features.content.service;

import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.common.exception.*;
import lemonadex.project.clothes.common.util.ListFilters;
import lemonadex.project.clothes.features.content.dto.ContentRequests.*;
import lemonadex.project.clothes.features.content.dto.ContentResponses.*;
import lemonadex.project.clothes.features.content.model.*;
import lemonadex.project.clothes.features.content.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.time.Clock;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class ContentService {
    private final ArticleRepository articles;
    private final PolicyRepository policies;
    private final ContentWriteRepository writes;
    private final Clock clock;
    public PageResponse<ArticleResponse> articles(String search, ArticleType type, ArticleStatus status, int page, int size) {
        return PageResponse.from(articles.findAll(ListFilters.where(search, new String[]{"title", "slug"}, ListFilters.values("articleType", type, "status", status), null, null), page(page, size)).map(this::articleResponse));
    }
    public ArticleResponse article(UUID id) { return articleResponse(articleRequired(id)); }
    @Transactional public ArticleResponse createArticle(ArticleRequest r, UUID actor) {
        writes.lock(); Article a = new Article(); a.setAuthorId(actor); return saveArticle(a, r);
    }
    @Transactional public ArticleResponse updateArticle(UUID id, ArticleRequest r) {
        writes.lock(); Article a = articleRequired(id);
        if (a.getStatus() != ArticleStatus.DRAFT) throw new ConflictException("ARTICLE_NOT_DRAFT", "Unpublish before editing the article");
        return saveArticle(a, r);
    }
    private ArticleResponse saveArticle(Article a, ArticleRequest r) {
        String slug = slug(r.slug() == null ? r.title() : r.slug());
        if (articles.slugExists(slug, a.getId())) throw new ConflictException("ARTICLE_SLUG_EXISTS", "Slug is already used, including archived articles");
        if (new HashSet<>(r.images()).size() != r.images().size()) throw new BadRequestException("DUPLICATE_IMAGES", "Images must be unique");
        a.setTitle(r.title().strip()); a.setSlug(slug); a.setThumbnailUrl(r.thumbnailUrl()); a.setContent(r.content().strip()); a.setArticleType(r.articleType());
        a.getImages().clear(); a.getImages().addAll(r.images()); a.setUpdatedAt(clock.instant());
        return articleResponse(articles.saveAndFlush(a));
    }
    @Transactional public ArticleResponse articleStatus(UUID id, ArticleStatusRequest r) {
        writes.lock(); Article a = articleRequired(id);
        if (a.getStatus() == r.status()) return articleResponse(a);
        if (r.status() == ArticleStatus.PUBLISHED) {
            if (a.getStatus() != ArticleStatus.DRAFT) throw new ConflictException("INVALID_ARTICLE_TRANSITION", "An archived article must become a draft first");
            if (a.getArticleType() == ArticleType.LOOKBOOK && a.getImages().isEmpty()) throw new ConflictException("LOOKBOOK_REQUIRES_IMAGES", "A published lookbook needs at least one image");
            a.setPublishedAt(clock.instant());
        }
        a.setStatus(r.status()); articles.flush(); return articleResponse(a);
    }
    @Transactional public void deleteArticle(UUID id) { writes.lock(); articleRequired(id); articles.archive(id); }
    public PageResponse<ArticleSummaryResponse> publishedArticles(String search, ArticleType type, int page, int size) {
        return PageResponse.from(articles.findAll(ListFilters.where(search, new String[]{"title", "slug"}, ListFilters.values("articleType", type, "status", ArticleStatus.PUBLISHED), null, null),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.asc("id")))).map(a -> new ArticleSummaryResponse(a.getId(), a.getTitle(), a.getSlug(), a.getThumbnailUrl(), a.getArticleType(), a.getPublishedAt())));
    }
    public PublishedArticleResponse publishedArticle(String slug) {
        Article a = articles.findBySlugAndStatus(slug, ArticleStatus.PUBLISHED).orElseThrow(() -> new ResourceNotFoundException("Published article"));
        return new PublishedArticleResponse(a.getId(), a.getTitle(), a.getSlug(), a.getThumbnailUrl(), a.getContent(), a.getArticleType(), List.copyOf(a.getImages()), a.getPublishedAt(), a.getUpdatedAt());
    }
    public PageResponse<PolicyResponse> policies(PolicyType type, PolicyStatus status, int page, int size) {
        return PageResponse.from(policies.findAll(ListFilters.where(null, new String[]{}, ListFilters.values("policyType", type, "status", status), null, null), page(page, size)).map(this::policyResponse));
    }
    public PolicyResponse policy(UUID id) { return policyResponse(policyRequired(id)); }
    @Transactional public PolicyResponse createPolicy(PolicyRequest r, UUID actor) {
        writes.lock(); int version = policies.lastVersion(r.policyType().name());
        if (version == Integer.MAX_VALUE) throw new ConflictException("POLICY_VERSION_LIMIT", "Policy version limit reached");
        StorePolicy p = new StorePolicy(); p.setPolicyType(r.policyType()); p.setVersion(version + 1); return savePolicy(p, r, actor);
    }
    @Transactional public PolicyResponse updatePolicy(UUID id, PolicyRequest r, UUID actor) {
        writes.lock(); StorePolicy p = policyRequired(id);
        if (p.getStatus() != PolicyStatus.DRAFT) throw new ConflictException("POLICY_IMMUTABLE", "Create a new version to change a published policy");
        if (p.getPolicyType() != r.policyType()) throw new BadRequestException("POLICY_TYPE_IMMUTABLE", "Policy type cannot change");
        return savePolicy(p, r, actor);
    }
    private PolicyResponse savePolicy(StorePolicy p, PolicyRequest r, UUID actor) {
        p.setTitle(r.title().strip()); p.setContent(r.content().strip()); p.setUpdatedBy(actor); return policyResponse(policies.saveAndFlush(p));
    }
    @Transactional public PolicyResponse policyStatus(UUID id, PolicyStatusRequest r, UUID actor) {
        writes.lock(); StorePolicy p = policyRequired(id);
        if (p.getStatus() == r.status()) return policyResponse(p);
        if (r.status() == PolicyStatus.DRAFT || p.getStatus() == PolicyStatus.ARCHIVED) throw new ConflictException("INVALID_POLICY_TRANSITION", "Published policy versions cannot become drafts or be reactivated");
        if (r.status() == PolicyStatus.ACTIVE) {
            policies.archiveActive(p.getPolicyType().name(), actor); p.setActivatedAt(clock.instant());
        }
        p.setStatus(r.status()); p.setUpdatedBy(actor); policies.flush(); return policyResponse(p);
    }
    @Transactional public void deletePolicy(UUID id) {
        writes.lock(); StorePolicy p = policyRequired(id);
        if (p.getStatus() != PolicyStatus.DRAFT) throw new ConflictException("POLICY_IMMUTABLE", "Only unpublished drafts can be removed");
        policies.archive(id);
    }
    public ActivePolicyResponse activePolicy(PolicyType type) {
        StorePolicy p = policies.findByPolicyTypeAndStatus(type, PolicyStatus.ACTIVE).orElseThrow(() -> new ResourceNotFoundException("Active policy"));
        return new ActivePolicyResponse(p.getPolicyType(), p.getTitle(), p.getContent(), p.getVersion(), p.getActivatedAt(), p.getUpdatedAt());
    }
    private String slug(String source) {
        String value = Normalizer.normalize(source.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (value.length() > 300) value = value.substring(0, 300).replaceAll("-+$", "");
        if (value.isEmpty()) throw new BadRequestException("INVALID_SLUG", "Slug must contain letters or digits"); return value;
    }
    private Pageable page(int page, int size) { return PageRequest.of(page, size, ListFilters.NEWEST); }
    private Article articleRequired(UUID id) { return articles.findById(id).orElseThrow(() -> new ResourceNotFoundException("Article")); }
    private StorePolicy policyRequired(UUID id) { return policies.findById(id).orElseThrow(() -> new ResourceNotFoundException("Policy")); }
    private ArticleResponse articleResponse(Article a) { return new ArticleResponse(a.getId(), a.getTitle(), a.getSlug(), a.getThumbnailUrl(), a.getContent(), a.getArticleType(), a.getAuthorId(), a.getStatus(), List.copyOf(a.getImages()), a.getPublishedAt(), a.getCreatedAt(), a.getUpdatedAt()); }
    private PolicyResponse policyResponse(StorePolicy p) { return new PolicyResponse(p.getId(), p.getPolicyType(), p.getTitle(), p.getContent(), p.getVersion(), p.getStatus(), p.getUpdatedBy(), p.getActivatedAt(), p.getCreatedAt(), p.getUpdatedAt()); }
}
