package lemonadex.project.clothes.features.catalog.service;

import lemonadex.project.clothes.common.exception.BadRequestException;
import java.text.Normalizer;
import java.util.Locale;

/** Slug and code normalisation shared by the catalog services. */
final class CatalogNames {
    private CatalogNames() {}

    /** The given slug, or one generated from the name: "Áo Thun Nữ" becomes "ao-thun-nu". */
    static String slug(String requested, String name, int maxLength) {
        String source = requested == null || requested.isBlank() ? name : requested;
        String ascii = Normalizer.normalize(source.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > maxLength) slug = slug.substring(0, maxLength).replaceAll("-+$", "");
        if (slug.isEmpty()) throw new BadRequestException("INVALID_SLUG", "Slug must contain at least one letter or digit");
        return slug;
    }

    static String code(String value) {
        return value.strip().toUpperCase(Locale.ROOT);
    }

    static String text(String value) {
        if (value == null) return null;
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
