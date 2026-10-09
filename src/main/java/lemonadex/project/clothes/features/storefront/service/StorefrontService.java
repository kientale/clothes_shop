package lemonadex.project.clothes.features.storefront.service;

import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.common.exception.BadRequestException;
import lemonadex.project.clothes.common.exception.ResourceNotFoundException;
import lemonadex.project.clothes.features.mail.service.MailService;
import lemonadex.project.clothes.features.storefront.dto.StorefrontResponses.*;
import lemonadex.project.clothes.features.storefront.repository.StorefrontRepository;
import lemonadex.project.clothes.features.storefront.model.StorefrontRows.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Public catalog for the shop: browsing, filtering and product detail. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class StorefrontService {
    private static final Set<String> GENDERS = Set.of("MEN", "WOMEN", "UNISEX", "KIDS");
    private static final Set<String> SORTS = Set.of("newest", "price_asc", "price_desc", "name");
    private static final int REVIEW_LIMIT = 10;

    private final StorefrontRepository store;
    private final MailService mail;
    private final ObjectMapper json;

    public StoreCatalog catalog() {
        return new StoreCatalog(
                store.categories().stream().map(c -> new CategoryOption(c.id(), c.parentId(), c.name(), c.slug())).toList(),
                store.brands().stream().map(b -> new BrandOption(b.id(), b.name(), b.slug(), b.logoUrl())).toList(),
                store.colors().stream().map(c -> new ColorOption(c.id(), c.name(), c.code(), c.hexCode())).toList(),
                store.sizes().stream().map(s -> new SizeOption(s.id(), s.name(), s.code(), s.sortOrder())).toList());
    }

    public PageResponse<ProductCard> products(ProductQuery query) {
        if (query.gender() != null && !GENDERS.contains(query.gender())) throw new BadRequestException("INVALID_GENDER", "Unknown gender filter");
        if (query.sort() != null && !SORTS.contains(query.sort())) throw new BadRequestException("INVALID_SORT", "Unknown sort order");
        if (query.minPrice() != null && query.maxPrice() != null && query.minPrice().compareTo(query.maxPrice()) > 0)
            throw new BadRequestException("INVALID_PRICE_RANGE", "The minimum price exceeds the maximum price");
        return cards(query);
    }

    /** One page of product cards for an already validated query (also used for collections and the wishlist). */
    public PageResponse<ProductCard> cards(ProductQuery query) {
        var page = store.products(query);
        int totalPages = (int) Math.ceil(page.total() / (double) query.size());
        return new PageResponse<>(page.content().stream().map(StorefrontService::card).toList(), query.page(), query.size(), page.total(), totalPages);
    }

    public List<CollectionSummary> collections() {
        return store.collections(null).stream().map(StorefrontService::collection).toList();
    }

    public CollectionDetail collection(String slug, int page, int size) {
        var row = store.collections(slug).stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Collection"));
        return new CollectionDetail(collection(row), cards(ProductQuery.collection(row.id(), page, size)));
    }

    /** sitemap.xml for search engines: the shop's fixed pages, then every on-sale product and visible collection. */
    public String sitemap() {
        String baseUrl = mail.storefront();
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String path : List.of("/", "/shop", "/shop/products", "/shop/collections")) url(xml, baseUrl + path, null);
        for (SitemapEntry entry : store.sitemap()) url(xml, baseUrl + entry.path(), entry.updatedAt());
        return xml.append("</urlset>\n").toString();
    }

    private static void url(StringBuilder xml, String loc, java.time.Instant updatedAt) {
        xml.append("  <url><loc>").append(org.springframework.web.util.HtmlUtils.htmlEscape(loc)).append("</loc>");
        if (updatedAt != null) xml.append("<lastmod>").append(updatedAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)).append("</lastmod>");
        xml.append("</url>\n");
    }

    private static CollectionSummary collection(CollectionRow r) {
        return new CollectionSummary(r.id(), r.slug(), r.name(), r.description(), r.imageUrl(), r.productCount(), r.startAt(), r.endAt());
    }

    public List<StoreBanner> banners(String position) {
        String code = position == null || position.isBlank() ? null : position.strip().toUpperCase(java.util.Locale.ROOT);
        return store.banners(code).stream().map(b -> new StoreBanner(b.id(), b.title(), b.imageUrl(), b.linkUrl(), b.position())).toList();
    }

    /** Detail by product slug, or by id when the key is a UUID. Only on-sale products with sellable variants are found. */
    public ProductDetail product(String key) {
        UUID id = null;
        try {
            id = UUID.fromString(key);
        } catch (IllegalArgumentException ignored) {
            // Not an id: look the product up by slug.
        }
        var head = store.product(id, key).orElseThrow(() -> new ResourceNotFoundException("Product"));
        var variants = store.variants(head.id());
        if (variants.isEmpty()) throw new ResourceNotFoundException("Product");
        var rating = store.rating(head.id());
        return new ProductDetail(head.id(), head.slug(), head.productCode(), head.name(), head.shortDescription(), head.description(),
                head.material(), head.gender(), ref(head.brand()), ref(head.category()),
                store.images(head.id()).stream().map(i -> new ProductImage(i.url(), i.altText())).toList(),
                variants.stream().map(v -> new VariantOption(v.id(), v.sku(), v.colorId(), v.colorName(), v.hexCode(), v.sizeId(), v.sizeName(),
                        v.sizeSortOrder(), v.price(), v.compareAtPrice(), (int) Math.max(0, Math.min(Integer.MAX_VALUE, v.available())))).toList(),
                Math.round(rating.average() * 10) / 10.0, rating.count(),
                store.reviews(head.id(), REVIEW_LIMIT).stream()
                        .map(r -> new ReviewSnippet(r.id(), r.rating(), r.comment(), r.customerName(), r.images(), r.createdAt())).toList(),
                store.sizeChart(head.id()).map(chart -> json.readValue(chart, SizeChart.class)).orElse(null));
    }

    /**
     * Search box suggestions: a few on-sale products, plus categories and brands whose name contains the words,
     * compared without Vietnamese accents ("ao so mi" finds "Áo sơ mi").
     */
    public Suggestions suggest(String query) {
        String term = query == null ? "" : query.strip();
        if (term.length() < 2) return new Suggestions(List.of(), List.of(), List.of());
        var products = cards(new ProductQuery(term, null, null, null, null, null, null, null, false, null, 0, 6)).content();
        String folded = fold(term);
        var catalog = catalog();
        return new Suggestions(products,
                catalog.categories().stream().filter(c -> fold(c.name()).contains(folded)).limit(4).toList(),
                catalog.brands().stream().filter(b -> fold(b.name()).contains(folded)).limit(4).toList());
    }

    /** Lower case without accents; "đ" has no decomposition, so it is mapped by hand. */
    static String fold(String text) {
        String decomposed = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").replace('đ', 'd');
    }

    private static NamedRef ref(NamedRow row) {
        return new NamedRef(row.id(), row.name(), row.slug());
    }

    /** The compare-at price only counts as a sale when it is above the highest current price. */
    private static ProductCard card(CardRow r) {
        List<Swatch> colors = r.colors().stream().map(c -> new Swatch(c.name(), c.hexCode())).toList();
        var compare = r.compareAtPrice() != null && r.compareAtPrice().compareTo(r.maxPrice()) > 0 ? r.compareAtPrice() : null;
        return new ProductCard(r.id(), r.slug(), r.name(), r.brandName(), r.categoryName(), r.gender(), r.imageUrl(), r.hoverImageUrl(),
                r.minPrice(), r.maxPrice(), compare, colors, r.available() > 0, r.createdAt());
    }
}
