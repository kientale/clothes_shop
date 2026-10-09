package lemonadex.project.clothes.features.catalog.repository;

import java.util.UUID;

/** Shared projection for "how many rows reference each id" aggregate queries. */
public interface CatalogRepositories {
    interface IdCount {
        UUID getId();

        long getTotal();
    }
}
