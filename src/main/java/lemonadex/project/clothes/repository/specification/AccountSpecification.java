package lemonadex.project.clothes.repository.specification;

import lemonadex.project.clothes.model.Account;
import lemonadex.project.clothes.model.AccountStatus;
import org.springframework.data.jpa.domain.Specification;
import java.util.Locale;

public final class AccountSpecification {
    private AccountSpecification() {}

    public static Specification<Account> visible(AccountStatus status, String search) {
        Specification<Account> filter = (root, query, cb) -> cb.isFalse(root.get("deleted"));
        if (status != null) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (search != null && !search.isBlank()) {
            String term = search.strip().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            filter = filter.and((root, query, cb) -> cb.like(cb.lower(root.get("email")), "%" + term + "%", '\\'));
        }
        return filter;
    }
}
