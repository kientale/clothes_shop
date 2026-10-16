package trankien.project.clothes.features.marketing.repository;
import trankien.project.clothes.features.marketing.model.Promotion;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;
public interface PromotionRepository extends JpaRepository<Promotion, UUID>, JpaSpecificationExecutor<Promotion> {}
