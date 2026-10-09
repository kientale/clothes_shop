package lemonadex.project.clothes.features.marketing.repository;
import lemonadex.project.clothes.features.marketing.model.Coupon;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;
public interface CouponRepository extends JpaRepository<Coupon, UUID>, JpaSpecificationExecutor<Coupon> {
    java.util.Optional<Coupon> findByCodeIgnoreCase(String code);
}
