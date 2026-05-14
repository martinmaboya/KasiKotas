package kasiKotas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bank_details_audit")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankDetailsAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_details_id")
    private Long bankDetailsId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column(name = "actor_username", nullable = false)
    private String actorUsername;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "before_snapshot_json", columnDefinition = "TEXT")
    private String beforeSnapshotJson;

    @Column(name = "after_snapshot_json", columnDefinition = "TEXT")
    private String afterSnapshotJson;

    public enum AuditAction {
        CREATE,
        UPDATE,
        DELETE
    }
}

