package lemonadex.project.clothes.features.file.model;

import lemonadex.project.clothes.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "uploaded_files")
@NoArgsConstructor
@SQLRestriction("deleted = false")
public class StoredFile extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FilePurpose purpose;
    @Column(nullable = false, length = 100)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private int size;
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;
    @Column(name = "uploaded_by")
    private UUID uploadedBy;
}
