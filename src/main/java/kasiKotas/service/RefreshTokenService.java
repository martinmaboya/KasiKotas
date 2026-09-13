package kasiKotas.service;

import kasiKotas.model.RefreshToken;
import kasiKotas.model.User;
import kasiKotas.repository.RefreshTokenRepository;
import kasiKotas.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpirationMs;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtUtil jwtUtil) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtUtil = jwtUtil;
    }

    public TokenPair issueTokenPair(User user) {
        String rawRefreshToken = generateRawRefreshToken();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sessionExpiresAt = now.plus(Duration.ofMillis(refreshTokenExpirationMs));

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashRefreshToken(rawRefreshToken))
                .tokenFamilyId(UUID.randomUUID().toString())
                .createdAt(now)
                .expiresAt(now.plus(Duration.ofMillis(refreshTokenExpirationMs)))
                .sessionExpiresAt(sessionExpiresAt)
                .build();

        refreshTokenRepository.save(refreshToken);
        String accessToken = jwtUtil.generateToken(user.getEmail(), user.getRole().toString());

        return new TokenPair(accessToken, rawRefreshToken, refreshToken);
    }

    public TokenPair rotateRefreshToken(String rawRefreshToken, User currentUser) {
        RefreshToken currentToken = findByRawToken(rawRefreshToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (currentUser != null && !currentUser.getId().equals(currentToken.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token does not belong to this user");
        }

        if (currentToken.isRevoked()) {
            revokeTokenFamily(currentToken.getTokenFamilyId(), currentToken.getUser());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has been revoked");
        }

        LocalDateTime now = LocalDateTime.now();
        if (currentToken.getSessionExpiresAt() == null
            || !currentToken.getSessionExpiresAt().isAfter(now)
            || currentToken.getExpiresAt() == null
            || !currentToken.getExpiresAt().isAfter(now)) {
            revokeToken(currentToken);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired or session expired");
        }

        if (currentToken.getReplacedByTokenId() != null) {
            revokeTokenFamily(currentToken.getTokenFamilyId(), currentToken.getUser());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has already been rotated");
        }

        String newRawRefreshToken = generateRawRefreshToken();
        LocalDateTime newExpiresAt = now.plus(Duration.ofMillis(refreshTokenExpirationMs));
        if (newExpiresAt.isAfter(currentToken.getSessionExpiresAt())) {
            newExpiresAt = currentToken.getSessionExpiresAt();
        }

        RefreshToken rotatedToken = RefreshToken.builder()
                .user(currentToken.getUser())
                .tokenHash(hashRefreshToken(newRawRefreshToken))
                .tokenFamilyId(currentToken.getTokenFamilyId())
                .createdAt(now)
                .expiresAt(newExpiresAt)
                .sessionExpiresAt(currentToken.getSessionExpiresAt())
                .build();

        refreshTokenRepository.save(rotatedToken);

        currentToken.setRevokedAt(now);
        currentToken.setReplacedByTokenId(rotatedToken.getId());
        refreshTokenRepository.save(currentToken);

        String newAccessToken = jwtUtil.generateToken(currentToken.getUser().getEmail(), currentToken.getUser().getRole().toString());
        return new TokenPair(newAccessToken, newRawRefreshToken, rotatedToken);
    }

    public void revokeCurrentRefreshToken(String rawRefreshToken, User user) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        Optional<RefreshToken> maybeToken = findByRawToken(rawRefreshToken);
        if (maybeToken.isEmpty()) {
            return;
        }

        RefreshToken refreshToken = maybeToken.get();
        if (user != null && !user.getId().equals(refreshToken.getUser().getId())) {
            return;
        }

        revokeToken(refreshToken);
    }

    public void revokeAllActiveTokensForUser(User user) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(user.getId());
        for (RefreshToken token : activeTokens) {
            revokeToken(token);
        }
    }

    public String generateRawRefreshToken() {
        byte[] random = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash refresh token", e);
        }
    }

    public Optional<RefreshToken> findByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByTokenHash(hashRefreshToken(rawToken));
    }

    public void revokeToken(RefreshToken refreshToken) {
        if (refreshToken == null || refreshToken.getRevokedAt() != null) {
            return;
        }
        refreshToken.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);
    }

    public void revokeTokenFamily(String tokenFamilyId, User user) {
        if (tokenFamilyId == null || user == null) {
            return;
        }

        List<RefreshToken> familyTokens = refreshTokenRepository.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(user.getId());
        for (RefreshToken token : familyTokens) {
            if (tokenFamilyId.equals(token.getTokenFamilyId())) {
                revokeToken(token);
            }
        }
    }

    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }

    public long getAccessTokenExpirationMs() {
        return jwtUtil.getAccessTokenExpirationMs();
    }

    public record TokenPair(String accessToken, String refreshToken, RefreshToken refreshTokenRecord) {}
}
