package lemonadex.project.clothes.repository;

import lemonadex.project.clothes.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByAccountIdAndDeletedFalse(UUID accountId);
}
