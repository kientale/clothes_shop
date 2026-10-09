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
import java.util.*;
import java.util.stream.Collectors;

/** Categories, brands, colors and sizes: the reference data products and variants point at. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaxonomyService {
    private static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final ColorRepository colors;
    private final SizeRepository sizes;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final CatalogWriteRepository writes;

    // Categories

    public PageResponse<CategoryResponse> categories(String search, CatalogStatus status, UUID parentId, int page, int size) {
        Page<Category> found = categories.findAll(CatalogSpecification.categories(search, status, parentId), PageRequest.of(page, size, NEWEST));
        List<UUID> ids = found.map(Category::getId).getContent();
        Map<UUID, Long> children = counts(ids.isEmpty() ? List.of() : categories.countChildren(ids));
        Map<UUID, Long> productCounts = counts(ids.isEmpty() ? List.of() : products.countByCategories(ids));
        return PageResponse.from(found.map(c -> category(c, children.getOrDefault(c.getId(), 0L), productCounts.getOrDefault(c.getId(), 0L))));
    }

    public CategoryResponse category(UUID id) {
        return category(requiredCategory(id));
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        writes.lock();
        Category category = new Category();
        applyCategory(category, request);
        return category(categories.saveAndFlush(category));
    }

    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        writes.lock();
        Category category = requiredCategory(id);
        applyCategory(category, request);
        categories.flush();
        return category(category);
    }

    @Transactional
    public void deleteCategory(UUID id) {
        writes.lock();
        Category category = requiredCategory(id);
        if (categories.existsByParentIdAndDeletedFalse(id)) {
            throw new ConflictException("CATEGORY_HAS_CHILDREN", "Move or delete the child categories first");
        }
        if (products.existsByCategoryIdAndDeletedFalse(id)) {
            throw new ConflictException("CATEGORY_IN_USE", "Category still has products");
        }
        categories.delete(category);
        categories.flush();
    }

    private void applyCategory(Category category, CategoryRequest request) {
        Category parent = null;
        if (request.parentId() != null) {
            parent = requiredCategory(request.parentId());
            // Walk up from the new parent: reaching this category would create a loop.
            for (Category step = parent; step != null; step = step.getParent()) {
                if (category.getId() != null && category.getId().equals(step.getId())) {
                    throw new BadRequestException("CATEGORY_CYCLE", "A category cannot be placed under itself or its descendants");
                }
            }
        }
        category.setParent(parent);
        category.setName(request.name().strip());
        category.setSlug(CatalogNames.slug(request.slug(), request.name(), 200));
        category.setDescription(CatalogNames.text(request.description()));
        category.setStatus(request.status());
    }

    private CategoryResponse category(Category c) {
        Map<UUID, Long> children = counts(categories.countChildren(List.of(c.getId())));
        Map<UUID, Long> productCounts = counts(products.countByCategories(List.of(c.getId())));
        return category(c, children.getOrDefault(c.getId(), 0L), productCounts.getOrDefault(c.getId(), 0L));
    }

    private CategoryResponse category(Category c, long childCount, long productCount) {
        Category parent = c.getParent();
        return new CategoryResponse(c.getId(), parent == null ? null : parent.getId(), parent == null ? null : parent.getName(),
                c.getName(), c.getSlug(), c.getDescription(), c.getStatus(), childCount, productCount,
                DateTimeUtils.toOffsetDateTime(c.getCreatedAt()), DateTimeUtils.toOffsetDateTime(c.getUpdatedAt()));
    }

    Category requiredCategory(UUID id) {
        return categories.findByIdAndDeletedFalse(id).orElseThrow(() -> new ResourceNotFoundException("Category"));
    }

    // Brands

    public PageResponse<BrandResponse> brands(String search, CatalogStatus status, int page, int size) {
        Page<Brand> found = brands.findAll(CatalogSpecification.brands(search, status), PageRequest.of(page, size, NEWEST));
        List<UUID> ids = found.map(Brand::getId).getContent();
        Map<UUID, Long> productCounts = counts(ids.isEmpty() ? List.of() : products.countByBrands(ids));
        return PageResponse.from(found.map(b -> brand(b, productCounts.getOrDefault(b.getId(), 0L))));
    }

    public BrandResponse brand(UUID id) {
        return brand(requiredBrand(id));
    }

    @Transactional
    public BrandResponse createBrand(BrandRequest request) {
        writes.lock();
        Brand brand = new Brand();
        applyBrand(brand, request);
        return brand(brands.saveAndFlush(brand));
    }

    @Transactional
    public BrandResponse updateBrand(UUID id, BrandRequest request) {
        writes.lock();
        Brand brand = requiredBrand(id);
        applyBrand(brand, request);
        brands.flush();
        return brand(brand);
    }

    @Transactional
    public void deleteBrand(UUID id) {
        writes.lock();
        Brand brand = requiredBrand(id);
        if (products.existsByBrandIdAndDeletedFalse(id)) throw new ConflictException("BRAND_IN_USE", "Brand still has products");
        brands.delete(brand);
        brands.flush();
    }

    private void applyBrand(Brand brand, BrandRequest request) {
        brand.setName(request.name().strip());
        brand.setSlug(CatalogNames.slug(request.slug(), request.name(), 200));
        brand.setLogoUrl(CatalogNames.text(request.logoUrl()));
        brand.setDescription(CatalogNames.text(request.description()));
        brand.setStatus(request.status());
    }

    private BrandResponse brand(Brand b) {
        return brand(b, counts(products.countByBrands(List.of(b.getId()))).getOrDefault(b.getId(), 0L));
    }

    private BrandResponse brand(Brand b, long productCount) {
        return new BrandResponse(b.getId(), b.getName(), b.getSlug(), b.getLogoUrl(), b.getDescription(), b.getStatus(), productCount,
                DateTimeUtils.toOffsetDateTime(b.getCreatedAt()), DateTimeUtils.toOffsetDateTime(b.getUpdatedAt()));
    }

    Brand requiredBrand(UUID id) {
        return brands.findByIdAndDeletedFalse(id).orElseThrow(() -> new ResourceNotFoundException("Brand"));
    }

    // Colors

    public PageResponse<ColorResponse> colors(String search, CatalogStatus status, int page, int size) {
        Page<Color> found = colors.findAll(CatalogSpecification.colors(search, status), PageRequest.of(page, size, NEWEST));
        List<UUID> ids = found.map(Color::getId).getContent();
        Map<UUID, Long> variantCounts = counts(ids.isEmpty() ? List.of() : variants.countByColors(ids));
        return PageResponse.from(found.map(c -> color(c, variantCounts.getOrDefault(c.getId(), 0L))));
    }

    public ColorResponse color(UUID id) {
        return color(requiredColor(id));
    }

    @Transactional
    public ColorResponse createColor(ColorRequest request) {
        writes.lock();
        Color color = new Color();
        applyColor(color, request);
        return color(colors.saveAndFlush(color));
    }

    @Transactional
    public ColorResponse updateColor(UUID id, ColorRequest request) {
        writes.lock();
        Color color = requiredColor(id);
        applyColor(color, request);
        colors.flush();
        return color(color);
    }

    @Transactional
    public void deleteColor(UUID id) {
        writes.lock();
        Color color = requiredColor(id);
        if (variants.existsByColorIdAndDeletedFalse(id)) throw new ConflictException("COLOR_IN_USE", "Color is used by product variants");
        colors.delete(color);
        colors.flush();
    }

    private void applyColor(Color color, ColorRequest request) {
        color.setName(request.name().strip());
        color.setCode(CatalogNames.code(request.code()));
        color.setHexCode(request.hexCode() == null ? null : request.hexCode().toUpperCase(Locale.ROOT));
        color.setStatus(request.status());
    }

    private ColorResponse color(Color c) {
        return color(c, counts(variants.countByColors(List.of(c.getId()))).getOrDefault(c.getId(), 0L));
    }

    private ColorResponse color(Color c, long variantCount) {
        return new ColorResponse(c.getId(), c.getName(), c.getCode(), c.getHexCode(), c.getStatus(), variantCount,
                DateTimeUtils.toOffsetDateTime(c.getCreatedAt()), DateTimeUtils.toOffsetDateTime(c.getUpdatedAt()));
    }

    Color requiredColor(UUID id) {
        return colors.findByIdAndDeletedFalse(id).orElseThrow(() -> new ResourceNotFoundException("Color"));
    }

    // Sizes

    public PageResponse<SizeResponse> sizes(String search, CatalogStatus status, int page, int size) {
        Sort order = Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("name"), Sort.Order.asc("id"));
        Page<Size> found = sizes.findAll(CatalogSpecification.sizes(search, status), PageRequest.of(page, size, order));
        List<UUID> ids = found.map(Size::getId).getContent();
        Map<UUID, Long> variantCounts = counts(ids.isEmpty() ? List.of() : variants.countBySizes(ids));
        return PageResponse.from(found.map(s -> size(s, variantCounts.getOrDefault(s.getId(), 0L))));
    }

    public SizeResponse size(UUID id) {
        return size(requiredSize(id));
    }

    @Transactional
    public SizeResponse createSize(SizeRequest request) {
        writes.lock();
        Size size = new Size();
        applySize(size, request);
        return size(sizes.saveAndFlush(size));
    }

    @Transactional
    public SizeResponse updateSize(UUID id, SizeRequest request) {
        writes.lock();
        Size size = requiredSize(id);
        applySize(size, request);
        sizes.flush();
        return size(size);
    }

    @Transactional
    public void deleteSize(UUID id) {
        writes.lock();
        Size size = requiredSize(id);
        if (variants.existsBySizeIdAndDeletedFalse(id)) throw new ConflictException("SIZE_IN_USE", "Size is used by product variants");
        sizes.delete(size);
        sizes.flush();
    }

    private void applySize(Size size, SizeRequest request) {
        size.setName(request.name().strip());
        size.setCode(CatalogNames.code(request.code()));
        size.setSortOrder(request.sortOrder());
        size.setStatus(request.status());
    }

    private SizeResponse size(Size s) {
        return size(s, counts(variants.countBySizes(List.of(s.getId()))).getOrDefault(s.getId(), 0L));
    }

    private SizeResponse size(Size s, long variantCount) {
        return new SizeResponse(s.getId(), s.getName(), s.getCode(), s.getSortOrder(), s.getStatus(), variantCount,
                DateTimeUtils.toOffsetDateTime(s.getCreatedAt()), DateTimeUtils.toOffsetDateTime(s.getUpdatedAt()));
    }

    Size requiredSize(UUID id) {
        return sizes.findByIdAndDeletedFalse(id).orElseThrow(() -> new ResourceNotFoundException("Size"));
    }

    // Pickers

    public CatalogOptionsResponse options() {
        Sort byName = Sort.by("name", "id");
        return new CatalogOptionsResponse(
                categories.findAllByDeletedFalse(byName).stream()
                        .map(c -> new CategoryOption(c.getId(), c.getName(), c.getParent() == null ? null : c.getParent().getId(), c.getStatus()))
                        .toList(),
                brands.findAllByDeletedFalse(byName).stream().map(b -> new BrandOption(b.getId(), b.getName(), b.getStatus())).toList(),
                colors.findAllByDeletedFalse(byName).stream()
                        .map(c -> new ColorOption(c.getId(), c.getName(), c.getCode(), c.getHexCode(), c.getStatus())).toList(),
                sizes.findAllByDeletedFalse(Sort.by("sortOrder", "name", "id")).stream()
                        .map(s -> new SizeOption(s.getId(), s.getName(), s.getCode(), s.getStatus())).toList());
    }

    private static Map<UUID, Long> counts(List<CatalogRepositories.IdCount> rows) {
        return rows.stream().collect(Collectors.toMap(CatalogRepositories.IdCount::getId, CatalogRepositories.IdCount::getTotal));
    }
}
