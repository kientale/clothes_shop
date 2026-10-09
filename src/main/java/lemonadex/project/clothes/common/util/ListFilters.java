package lemonadex.project.clothes.common.util;

import lemonadex.project.clothes.common.exception.BadRequestException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.*;

/** Literal substring search, exact filters and a half-open creation-time range. */
public final class ListFilters {
    private ListFilters() {}
    public static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    public static Map<String, Object> values(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) if (pairs[i + 1] != null) values.put((String) pairs[i], pairs[i + 1]);
        return values;
    }

    public static <T> Specification<T> where(String search, String[] fields, Map<String, Object> exact, Instant from, Instant to) {
        if (from != null && to != null && !to.isAfter(from)) throw new BadRequestException("INVALID_PERIOD", "The end time must be after the start time");
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            exact.forEach((name, value) -> all.add(cb.equal(root.get(name), value)));
            if (from != null) all.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) all.add(cb.lessThan(root.get("createdAt"), to));
            if (search != null && !search.isBlank() && fields.length > 0) {
                String term = SearchUtils.contains(search);
                all.add(cb.or(Arrays.stream(fields).map(field -> cb.like(cb.lower(root.get(field)), term, '\\')).toArray(Predicate[]::new)));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }
}
