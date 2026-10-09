package lemonadex.project.clothes.features.role.repository.specification;

import lemonadex.project.clothes.features.role.model.Role;
import lemonadex.project.clothes.common.util.SearchUtils;
import org.springframework.data.jpa.domain.Specification;

public final class RoleSpecification {
    private RoleSpecification() {}

    public static Specification<Role> visible(String search) {
        return (root, query, cb) -> {
            var filter = cb.isFalse(root.get("deleted"));
            if (search != null && !search.isBlank()) {
                String term = SearchUtils.contains(search);
                filter = cb.and(filter, cb.or(cb.like(cb.lower(root.get("name")), term, '\\'),
                        cb.like(cb.lower(root.get("code")), term, '\\')));
            }
            return filter;
        };
    }
}
