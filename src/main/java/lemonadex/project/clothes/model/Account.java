package lemonadex.project.clothes.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "accounts")
@NoArgsConstructor
@SQLDelete(sql = "UPDATE accounts SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
public class Account extends BaseEntity {
    @Column(nullable = false, length = 254)
    private String email;
    @Column(nullable = false, length = 255)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountStatus status = AccountStatus.ACTIVE;
    private Instant lastLoginAt;
    @ManyToMany
    @JoinTable(name = "account_roles", joinColumns = @JoinColumn(name = "account_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @BatchSize(size = 50)
    private Set<Role> roles = new LinkedHashSet<>();
}
