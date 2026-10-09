package lemonadex.project.clothes.features.content.repository;
import lemonadex.project.clothes.features.content.model.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface ArticleRepository extends JpaRepository<Article, UUID>, JpaSpecificationExecutor<Article> {
    Optional<Article> findBySlugAndStatus(String slug, ArticleStatus status);
    @Query(value = "SELECT EXISTS(SELECT 1 FROM articles WHERE slug = :slug AND (CAST(:id AS uuid) IS NULL OR id <> CAST(:id AS uuid)))", nativeQuery = true)
    boolean slugExists(String slug, UUID id);
    @Modifying @Query(value = "UPDATE articles SET deleted = true WHERE id = :id", nativeQuery = true)
    void archive(UUID id);
}
