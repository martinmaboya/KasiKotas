package kasiKotas.repository;

import kasiKotas.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByIdAndUserId(Long id, Long userId);

    List<RefreshToken> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(Long userId);

    List<RefreshToken> findByUserIdAndSessionExpiresAtAfter(Long userId, LocalDateTime now);

    Optional<RefreshToken> findByTokenHashAndUserId(String tokenHash, Long userId);
}
