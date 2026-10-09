package lemonadex.project.clothes.features.marketing.service;
import lemonadex.project.clothes.common.exception.BadRequestException;
import lemonadex.project.clothes.features.marketing.model.DiscountType;
import java.math.*;
import java.time.Instant;
import java.util.*;

final class MarketingValidation {
    private MarketingValidation() {}
    static void period(Instant start, Instant end) {
        if (start != null && end != null && !end.isAfter(start)) throw new BadRequestException("INVALID_PERIOD", "The end must be after the start");
    }
    static void discount(DiscountType type, BigDecimal value) {
        if (type == DiscountType.PERCENTAGE && value.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new BadRequestException("INVALID_PERCENTAGE", "Percentage cannot exceed 100");
    }
    static BigDecimal amount(DiscountType type, BigDecimal value, BigDecimal base) {
        return (type == DiscountType.PERCENTAGE ? base.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN) : value).min(base);
    }
    static <T> void unique(List<T> values) {
        if (new HashSet<>(values).size() != values.size()) throw new BadRequestException("DUPLICATE_REFERENCES", "References must be unique");
    }
    static String text(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
