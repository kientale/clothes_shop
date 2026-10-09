package lemonadex.project.clothes.features.account.repository.specification;

import lemonadex.project.clothes.features.account.model.Account;
import lemonadex.project.clothes.features.account.model.AdminProfile;
import lemonadex.project.clothes.features.account.model.AccountStatus;
import lemonadex.project.clothes.features.role.model.Role;
import lemonadex.project.clothes.common.util.SearchUtils;
import org.springframework.data.jpa.domain.Specification;

public final class AdminAccountSpecification {
    private AdminAccountSpecification() {}

    public static Specification<Account> visible(AccountStatus status, String search) {
        return (root, query, cb) -> {
            var roleQuery = query.subquery(Integer.class);
            var role = roleQuery.correlate(root).join("roles");
            roleQuery.select(cb.literal(1)).where(cb.equal(role.get("code"), "ADMIN"), cb.isFalse(role.get("deleted")));
            var filter = cb.and(cb.isFalse(root.get("deleted")), cb.exists(roleQuery));
            if (status != null) filter = cb.and(filter, cb.equal(root.get("status"), status));
            if (search != null && !search.isBlank()) {
                String term = SearchUtils.contains(search);
                var profileQuery = query.subquery(Integer.class);
                var profile = profileQuery.from(AdminProfile.class);
                profileQuery.select(cb.literal(1)).where(cb.equal(profile.get("account"), root),
                        cb.isFalse(profile.get("deleted")), cb.like(cb.lower(profile.get("fullName")), term, '\\'));
                filter = cb.and(filter, cb.or(cb.like(cb.lower(root.get("email")), term, '\\'), cb.exists(profileQuery)));
            }
            return filter;
        };
    }
}
