package lemonadex.project.clothes.features.marketing.repository;
import lemonadex.project.clothes.features.marketing.model.Banner;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;
public interface BannerRepository extends JpaRepository<Banner, UUID>, JpaSpecificationExecutor<Banner> {}
