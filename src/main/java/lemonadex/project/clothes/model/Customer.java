package lemonadex.project.clothes.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "customers")
@NoArgsConstructor
@SQLDelete(sql = "UPDATE customers SET deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
public class Customer extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;
    @Column(nullable = false, length = 150)
    private String fullName;
    @Column(length = 30)
    private String phone;
    @Column(length = 20)
    private String gender;
    private LocalDate dateOfBirth;
    @Column(columnDefinition = "text")
    private String avatarUrl;
    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";
}
