package lemonadex.project.clothes.features.customer.repository.specification;

import lemonadex.project.clothes.common.util.SearchUtils;
import lemonadex.project.clothes.features.account.model.Account;
import lemonadex.project.clothes.features.customer.model.*;
import org.springframework.data.jpa.domain.Specification;

public final class CustomerSpecification {
    private CustomerSpecification() {}

    public static Specification<Customer> visible(CustomerStatus status, String search) {
        return (root, query, cb) -> {
            var filter = cb.isFalse(root.get("deleted"));
            if (status != null) filter = cb.and(filter, cb.equal(root.get("status"), status.name()));
            if (search != null && !search.isBlank()) {
                String term = SearchUtils.contains(search);
                // Also match the linked account's login email (only while that account is not deleted).
                var emailQuery = query.subquery(Integer.class);
                var account = emailQuery.from(Account.class);
                emailQuery.select(cb.literal(1)).where(cb.equal(account, root.get("account")),
                        cb.isFalse(account.get("deleted")), cb.like(cb.lower(account.get("email")), term, '\\'));
                filter = cb.and(filter, cb.or(cb.like(cb.lower(root.get("fullName")), term, '\\'),
                        cb.like(root.get("phone"), term, '\\'), cb.exists(emailQuery)));
            }
            return filter;
        };
    }
}
