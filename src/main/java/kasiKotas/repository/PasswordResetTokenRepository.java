package kasiKotas.repository;

import kasiKotas.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenAndEmailAndUsedFalseAndExpiresAtAfter(String token, String email, LocalDateTime now);
    Optional<PasswordResetToken> findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(String email, LocalDateTime now);
    void deleteByExpiresAtBeforeOrUsedTrue(LocalDateTime now);
}

