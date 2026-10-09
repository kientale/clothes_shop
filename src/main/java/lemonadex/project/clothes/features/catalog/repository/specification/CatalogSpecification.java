package lemonadex.project.clothes.features.catalog.repository.specification;

import jakarta.persistence.criteria.*;
import lemonadex.project.clothes.common.util.SearchUtils;
import lemonadex.project.clothes.features.catalog.model.*;
import org.springframework.data.jpa.domain.Specification;
import java.util.UUID;

/** List filters for the catalog screens; every search is a literal, case-insensitive substring match. */
public final class CatalogSpecification {
    private CatalogSpecification() {}

    public static Specification<Category> categories(String search, CatalogStatus status, UUID parentId) {
        return (root, query, cb) -> {
            Predicate filter = visible(root, cb, status, search, "name", "slug");
            return parentId == null ? filter : cb.and(filter, cb.equal(root.get("parent").get("id"), parentId));
        };
    }

    public static Specification<Brand> brands(String search, CatalogStatus status) {
        return (root, query, cb) -> visible(root, cb, status, search, "name", "slug");
    }

    public static Specification<Color> colors(String search, CatalogStatus status) {
        return (root, query, cb) -> visible(root, cb, status, search, "name", "code");
    }

    public static Specification<Size> sizes(String search, CatalogStatus status) {
        return (root, query, cb) -> visible(root, cb, status, search, "name", "code");
    }

    public static Specification<Collection> collections(String search, CatalogStatus status) {
        return (root, query, cb) -> visible(root, cb, status, search, "name", "slug");
    }

    public static Specification<Product> products(String search, ProductStatus status, UUID brandId, UUID categoryId,
                                                  ProductGender gender) {
        return (root, query, cb) -> {
            Predicate filter = cb.isFalse(root.get("deleted"));
            if (status != null) filter = cb.and(filter, cb.equal(root.get("status"), status));
            if (brandId != null) filter = cb.and(filter, cb.equal(root.get("brand").get("id"), brandId));
            if (categoryId != null) filter = cb.and(filter, cb.equal(root.get("category").get("id"), categoryId));
            if (gender != null) filter = cb.and(filter, cb.equal(root.get("gender"), gender));
            return search == null || search.isBlank() ? filter : cb.and(filter, matches(root, cb, search, "name", "productCode", "slug"));
        };
    }

    public static Specification<ProductVariant> variants(String search, CatalogStatus status, UUID productId, UUID colorId, UUID sizeId) {
        return (root, query, cb) -> {
            Predicate filter = cb.isFalse(root.get("deleted"));
            if (status != null) filter = cb.and(filter, cb.equal(root.get("status"), status));
            if (productId != null) filter = cb.and(filter, cb.equal(root.get("product").get("id"), productId));
            if (colorId != null) filter = cb.and(filter, cb.equal(root.get("color").get("id"), colorId));
            if (sizeId != null) filter = cb.and(filter, cb.equal(root.get("size").get("id"), sizeId));
            if (search == null || search.isBlank()) return filter;
            String term = SearchUtils.contains(search);
            Join<ProductVariant, Product> product = root.join("product");
            return cb.and(filter, cb.or(cb.like(cb.lower(root.get("sku")), term, '\\'),
                    cb.like(cb.lower(product.get("name")), term, '\\'),
                    cb.like(cb.lower(product.get("productCode")), term, '\\')));
        };
    }

    private static <T> Predicate visible(Root<T> root, CriteriaBuilder cb, CatalogStatus status, String search, String... fields) {
        Predicate filter = cb.isFalse(root.get("deleted"));
        if (status != null) filter = cb.and(filter, cb.equal(root.get("status"), status));
        return search == null || search.isBlank() ? filter : cb.and(filter, matches(root, cb, search, fields));
    }

    private static <T> Predicate matches(Root<T> root, CriteriaBuilder cb, String search, String... fields) {
        String term = SearchUtils.contains(search);
        Predicate[] any = new Predicate[fields.length];
        for (int i = 0; i < fields.length; i++) any[i] = cb.like(cb.lower(root.get(fields[i])), term, '\\');
        return cb.or(any);
    }
}
