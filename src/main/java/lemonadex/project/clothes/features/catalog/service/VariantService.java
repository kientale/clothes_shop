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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VariantService {
    private final ProductVariantRepository variants;
    private final ColorRepository colors;
    private final SizeRepository sizes;
    private final ProductService products;
    private final TaxonomyService taxonomy;
    private final CatalogWriteRepository writes;

    public PageResponse<VariantResponse> list(String search, CatalogStatus status, UUID productId, UUID colorId, UUID sizeId,
                                              int page, int size) {
        Sort order = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("sku"), Sort.Order.asc("id"));
        return PageResponse.from(variants.findAll(CatalogSpecification.variants(search, status, productId, colorId, sizeId),
                PageRequest.of(page, size, order)).map(this::response));
    }

    public VariantResponse get(UUID id) {
        return response(required(id));
    }

    @Transactional
    public VariantResponse create(VariantCreateRequest request) {
        writes.lock();
        checkPrices(request.price(), request.compareAtPrice());
        Product product = products.required(request.productId());
        Color color = taxonomy.requiredColor(request.colorId());
        lemonadex.project.clothes.features.catalog.model.Size size = taxonomy.requiredSize(request.sizeId());
        if (variants.existsByProductIdAndColorIdAndSizeIdAndDeletedFalse(product.getId(), color.getId(), size.getId())) {
            throw new ConflictException("VARIANT_EXISTS", "This product already has a variant with that color and size");
        }
        String sku = request.sku() == null || request.sku().isBlank() ? sku(product, color, size) : CatalogNames.code(request.sku());
        return response(save(product, color, size, sku, request.price(), request.compareAtPrice(), request.status()));
    }

    @Transactional
    public VariantResponse update(UUID id, VariantUpdateRequest request) {
        writes.lock();
        checkPrices(request.price(), request.compareAtPrice());
        ProductVariant variant = required(id);
        variant.setSku(CatalogNames.code(request.sku()));
        variant.setPrice(request.price());
        variant.setCompareAtPrice(request.compareAtPrice());
        variant.setStatus(request.status());
        variants.flush();
        return response(variant);
    }

    @Transactional
    public void delete(UUID id) {
        writes.lock();
        variants.delete(required(id));
        variants.flush();
    }

    /** Creates the missing color x size combinations of a product; existing ones are left untouched. */
    @Transactional
    public List<VariantResponse> bulkCreate(VariantBulkRequest request) {
        writes.lock();
        checkPrices(request.price(), request.compareAtPrice());
        Product product = products.required(request.productId());
        List<Color> chosenColors = colors.findAllByIdInAndDeletedFalse(request.colorIds());
        var chosenSizes = sizes.findAllByIdInAndDeletedFalse(request.sizeIds());
        if (chosenColors.size() != request.colorIds().size()) throw new ResourceNotFoundException("Color");
        if (chosenSizes.size() != request.sizeIds().size()) throw new ResourceNotFoundException("Size");
        chosenColors.sort(Comparator.comparing(Color::getName));
        chosenSizes.sort(Comparator.comparingInt(lemonadex.project.clothes.features.catalog.model.Size::getSortOrder)
                .thenComparing(lemonadex.project.clothes.features.catalog.model.Size::getName));
        Set<Combination> existing = new HashSet<>();
        variants.findAllByProductIdAndDeletedFalse(product.getId()).forEach(v ->
                existing.add(new Combination(v.getColor().getId(), v.getSize().getId())));
        List<VariantResponse> created = new ArrayList<>();
        for (Color color : chosenColors) {
            for (var size : chosenSizes) {
                if (existing.contains(new Combination(color.getId(), size.getId()))) continue;
                created.add(response(save(product, color, size, sku(product, color, size), request.price(),
                        request.compareAtPrice(), request.status())));
            }
        }
        return created;
    }

    private record Combination(UUID colorId, UUID sizeId) {}

    /**
     * Stores a variant. A soft-deleted row with the same product/color/size still holds the unique
     * combination, so it is restored and overwritten instead of failing with a duplicate.
     */
    private ProductVariant save(Product product, Color color, lemonadex.project.clothes.features.catalog.model.Size size, String sku,
                                BigDecimal price, BigDecimal compareAtPrice, CatalogStatus status) {
        Optional<UUID> deleted = variants.findDeletedCombination(product.getId(), color.getId(), size.getId());
        ProductVariant variant;
        if (deleted.isPresent()) {
            variants.restore(deleted.get());
            variant = variants.findByIdAndDeletedFalse(deleted.get()).orElseThrow();
        } else {
            variant = new ProductVariant();
            variant.setProduct(product);
            variant.setColor(color);
            variant.setSize(size);
        }
        variant.setSku(sku);
        variant.setPrice(price);
        variant.setCompareAtPrice(compareAtPrice);
        variant.setStatus(status);
        return variants.saveAndFlush(variant);
    }

    private static String sku(Product product, Color color, lemonadex.project.clothes.features.catalog.model.Size size) {
        String sku = String.join("-", product.getProductCode(), color.getCode(), size.getCode());
        if (sku.length() <= 100) return sku;
        // Preserve distinct combinations when long codes would truncate away the color or size.
        String identity = product.getId() + ":" + color.getId() + ":" + size.getId();
        String suffix = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        return sku.substring(0, 67) + "-" + suffix;
    }

    private static void checkPrices(BigDecimal price, BigDecimal compareAtPrice) {
        if (compareAtPrice != null && compareAtPrice.compareTo(price) < 0) {
            throw new BadRequestException("INVALID_COMPARE_PRICE", "The compare-at price must not be lower than the price");
        }
    }

    private VariantResponse response(ProductVariant v) {
        Product p = v.getProduct();
        Color c = v.getColor();
        var s = v.getSize();
        return new VariantResponse(v.getId(), new VariantProduct(p.getId(), p.getProductCode(), p.getName()),
                new VariantColor(c.getId(), c.getName(), c.getCode(), c.getHexCode()), new VariantSize(s.getId(), s.getName(), s.getCode()),
                v.getSku(), v.getPrice(), v.getCompareAtPrice(), v.getStatus(),
                DateTimeUtils.toOffsetDateTime(v.getCreatedAt()), DateTimeUtils.toOffsetDateTime(v.getUpdatedAt()));
    }

    private ProductVariant required(UUID id) {
        return variants.findByIdAndDeletedFalse(id).orElseThrow(() -> new ResourceNotFoundException("Product variant"));
    }
}
