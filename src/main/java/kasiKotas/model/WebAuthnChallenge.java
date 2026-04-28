package kasiKotas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "webauthn_challenges")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebAuthnChallenge {

    @Id
    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "challenge", nullable = false, length = 255)
    private String challenge;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ChallengeType type;

    @Column(name = "request_json", nullable = false, columnDefinition = "TEXT")
    private String requestJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public enum ChallengeType {
        REGISTRATION,
        ASSERTION
    }
}