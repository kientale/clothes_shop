package lemonadex.project.clothes.features.account.model;

import lemonadex.project.clothes.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@Table(name = "admin_profiles")
@NoArgsConstructor
@SQLDelete(sql = "UPDATE admin_profiles SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
public class AdminProfile extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;
    @Column(nullable = false, length = 150)
    private String fullName;
    @Column(length = 30)
    private String phone;
    @Column(columnDefinition = "text")
    private String avatarUrl;
}
