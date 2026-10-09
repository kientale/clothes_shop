package lemonadex.project.clothes.features.catalog.service;

import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.common.exception.*;
import lemonadex.project.clothes.common.util.DateTimeUtils;
import lemonadex.project.clothes.features.catalog.dto.CatalogRequests.*;
import lemonadex.project.clothes.features.catalog.dto.CatalogResponses.*;
import lemonadex.project.clothes.features.catalog.model.*;
import lemonadex.project.clothes.features.catalog.repository.*;
import lemonadex.project.clothes.features.catalog.repository.specification.CatalogSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Products with their images, and collections (ordered groups of products). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {
    private static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final CollectionRepository collections;
    private final TaxonomyService taxonomy;
    private final CatalogWriteRepository writes;

    // Products

    public PageResponse<ProductResponse> products(String search, ProductStatus status, UUID brandId, UUID categoryId,
                                                  ProductGender gender, int page, int size) {
        Page<Product> found = products.findAll(CatalogSpecification.products(search, status, brandId, categoryId, gender),
                PageRequest.of(page, size, NEWEST));
        Map<UUID, ProductVariantRepository.VariantStats> stats = stats(found.map(Product::getId).getContent());
        return PageResponse.from(found.map(p -> product(p, stats.get(p.getId()))));
    }

    public ProductResponse product(UUID id) {
        return product(required(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        writes.lock();
        Product product = new Product();
        apply(product, request);
        products.saveAndFlush(product);
        replaceImages(product, request.images());
        return product(product);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        writes.lock();
        Product product = required(id);
        apply(product, request);
        // An image-only update must also advance the owning product's timestamp.
        product.setUpdatedAt(Instant.now());
        replaceImages(product, request.images());
        return product(product);
    }

    @Transactional
    public void delete(UUID id) {
        writes.lock();
        required(id);
        variants.softDeleteByProduct(id);
        products.softDelete(id);
    }

    private void apply(Product product, ProductRequest request) {
        product.setProductCode(CatalogNames.code(request.productCode()));
        product.setName(request.name().strip());
        product.setSlug(CatalogNames.slug(request.slug(), request.name(), 300));
        product.setDescription(CatalogNames.text(request.description()));
        product.setShortDescription(CatalogNames.text(request.shortDescription()));
        product.setBrand(taxonomy.requiredBrand(request.brandId()));
        product.setCategory(taxonomy.requiredCategory(request.categoryId()));
        product.setMaterial(CatalogNames.text(request.material()));
        product.setGender(request.gender());
        product.setBasePrice(request.basePrice());
        product.setStatus(request.status());
    }

    /**
     * Replaces all product images. Old rows are flushed away first: Hibernate inserts before it
     * deletes, which would otherwise collide with the one-primary-image-per-product index.
     */
    private void replaceImages(Product product, List<ProductImageRequest> requested) {
        product.getImages().clear();
        products.flush();
        for (int i = 0; i < requested.size(); i++) {
            ProductImage image = new ProductImage();
            image.setProduct(product);
            image.setImageUrl(requested.get(i).url().strip());
            image.setAltText(CatalogNames.text(requested.get(i).altText()));
            image.setPrimary(i == 0);
            image.setSortOrder(i);
            product.getImages().add(image);
        }
        products.flush();
    }

    private ProductResponse product(Product p) {
        return product(p, stats(List.of(p.getId())).get(p.getId()));
    }

    private ProductResponse product(Product p, ProductVariantRepository.VariantStats stats) {
        List<ProductImageResponse> images = p.getImages().stream()
                .map(i -> new ProductImageResponse(i.getId(), i.getImageUrl(), i.getAltText(), i.isPrimary())).toList();
        return new ProductResponse(p.getId(), p.getProductCode(), p.getName(), p.getSlug(), p.getDescription(), p.getShortDescription(),
                new Ref(p.getBrand().getId(), p.getBrand().getName()), new Ref(p.getCategory().getId(), p.getCategory().getName()),
                p.getMaterial(), p.getGender(), p.getBasePrice(), p.getStatus(), images,
                stats == null ? 0 : stats.getTotal(), stats == null ? null : stats.getMinPrice(), stats == null ? null : stats.getMaxPrice(),
                DateTimeUtils.toOffsetDateTime(p.getCreatedAt()), DateTimeUtils.toOffsetDateTime(p.getUpdatedAt()));
    }

    private Map<UUID, ProductVariantRepository.VariantStats> stats(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return variants.statsByProducts(ids).stream()
                .collect(Collectors.toMap(ProductVariantRepository.VariantStats::getProductId, Function.identity()));
    }

    Product required(UUID id) {
        return products.findByIdAndDeletedFalse(id).orElseThrow(() -> new ResourceNotFoundException("Product"));
    }

    // Collections

    public PageResponse<CollectionResponse> collections(String search, CatalogStatus status, int page, int size) {
        Page<lemonadex.project.clothes.features.catalog.model.Collection> found =
                collections.findAll(CatalogSpecification.collections(search, status), PageRequest.of(page, size, NEWEST));
        Set<UUID> referenced = found.getContent().stream().flatMap(c -> c.getProductIds().stream()).collect(Collectors.toSet());
        Set<UUID> live = referenced.isEmpty() ? Set.of() : new HashSet<>(products.findExistingIds(referenced));
        return PageResponse.from(found.map(c -> collection(c, c.getProductIds().stream().filter(live::contains).count(), null)));
    }

    public CollectionResponse collection(UUID id) {
        return collectionDetail(requiredCollection(id));
    }

    @Transactional
    public CollectionResponse createCollection(CollectionRequest request) {
        writes.lock();
        var collection = new lemonadex.project.clothes.features.catalog.model.Collection();
        applyCollection(collection, request);
        return collectionDetail(collections.saveAndFlush(collection));
    }

    @Transactional
    public CollectionResponse updateCollection(UUID id, CollectionRequest request) {
        writes.lock();
        var collection = requiredCollection(id);
        applyCollection(collection, request);
        collection.setUpdatedAt(Instant.now());
        collections.flush();
        return collectionDetail(collection);
    }

    @Transactional
    public void deleteCollection(UUID id) {
        writes.lock();
        collections.delete(requiredCollection(id));
        collections.flush();
    }

    private void applyCollection(lemonadex.project.clothes.features.catalog.model.Collection collection, CollectionRequest request) {
        if (request.startAt() != null && request.endAt() != null && !request.endAt().isAfter(request.startAt())) {
            throw new BadRequestException("INVALID_PERIOD", "The end time must be after the start time");
        }
        List<UUID> ids = request.productIds();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new BadRequestException("DUPLICATE_PRODUCTS", "A product can appear only once in a collection");
        }
        if (!ids.isEmpty() && products.findExistingIds(ids).size() != ids.size()) throw new ResourceNotFoundException("Product");
        collection.setName(request.name().strip());
        collection.setSlug(CatalogNames.slug(request.slug(), request.name(), 200));
        collection.setDescription(CatalogNames.text(request.description()));
        collection.setImageUrl(CatalogNames.text(request.imageUrl()));
        collection.setStartAt(request.startAt() == null ? null : request.startAt().toInstant());
        collection.setEndAt(request.endAt() == null ? null : request.endAt().toInstant());
        collection.setStatus(request.status());
        // A new list instance makes Hibernate rewrite the rows instead of updating them in place.
        collection.setProductIds(new ArrayList<>(ids));
    }

    private CollectionResponse collectionDetail(lemonadex.project.clothes.features.catalog.model.Collection c) {
        Map<UUID, Product> byId = c.getProductIds().isEmpty() ? Map.of()
                : products.findAllByIdInAndDeletedFalse(c.getProductIds()).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        List<CollectionProductResponse> items = c.getProductIds().stream().map(byId::get).filter(Objects::nonNull)
                .map(p -> new CollectionProductResponse(p.getId(), p.getProductCode(), p.getName(),
                        p.getImages().isEmpty() ? null : p.getImages().getFirst().getImageUrl(), p.getStatus()))
                .toList();
        return collection(c, items.size(), items);
    }

    private CollectionResponse collection(lemonadex.project.clothes.features.catalog.model.Collection c, long productCount,
                                          List<CollectionProductResponse> items) {
        return new CollectionResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription(), c.getImageUrl(),
                DateTimeUtils.toOffsetDateTime(c.getStartAt()), DateTimeUtils.toOffsetDateTime(c.getEndAt()), c.getStatus(),
                productCount, items, DateTimeUtils.toOffsetDateTime(c.getCreatedAt()), DateTimeUtils.toOffsetDateTime(c.getUpdatedAt()));
    }

    private lemonadex.project.clothes.features.catalog.model.Collection requiredCollection(UUID id) {
        return collections.findByIdAndDeletedFalse(id).orElseThrow(() -> new ResourceNotFoundException("Collection"));
    }
}
