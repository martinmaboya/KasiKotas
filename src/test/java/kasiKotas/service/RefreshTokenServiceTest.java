package kasiKotas.service;

import kasiKotas.model.RefreshToken;
import kasiKotas.model.User;
import kasiKotas.repository.RefreshTokenRepository;
import kasiKotas.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtUtil jwtUtil;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(refreshTokenRepository, jwtUtil);
        ReflectionTestUtils.setField(service, "refreshTokenExpirationMs", Duration.ofDays(7).toMillis());
    }

    @Test
    void rotationKeepsAbsoluteSessionExpiryAndCapsNewTokenExpiry() {
        User user = user(1L, "user@example.com");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sessionExpiry = now.plusDays(1);
        RefreshToken current = token(user, "old-hash", "family-1", now.minusDays(6), sessionExpiry, sessionExpiry);

        when(refreshTokenRepository.findByTokenHash(service.hashRefreshToken("old-token")))
                .thenReturn(Optional.of(current));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            if (token.getId() == null) {
                token.setId(2L);
            }
            return token;
        });
        when(jwtUtil.generateToken(user.getEmail(), user.getRole().toString())).thenReturn("access-token");

        RefreshTokenService.TokenPair result = service.rotateRefreshToken("old-token", null);

        RefreshToken rotated = result.refreshTokenRecord();
        assertEquals(sessionExpiry, rotated.getSessionExpiresAt());
        assertTrue(!rotated.getExpiresAt().isAfter(sessionExpiry));
        assertEquals("family-1", rotated.getTokenFamilyId());
        assertTrue(current.isRevoked());
        assertEquals(2L, current.getReplacedByTokenId());
        verify(jwtUtil).generateToken(user.getEmail(), user.getRole().toString());
    }

    @Test
    void expiredAbsoluteSessionCannotBeRotated() {
        User user = user(1L, "user@example.com");
        LocalDateTime expired = LocalDateTime.now().minusSeconds(1);
        RefreshToken current = token(user, "old-hash", "family-1", expired.minusDays(7), expired, expired);

        when(refreshTokenRepository.findByTokenHash(service.hashRefreshToken("old-token")))
                .thenReturn(Optional.of(current));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.rotateRefreshToken("old-token", null));

        assertEquals(401, exception.getStatusCode().value());
        assertTrue(current.isRevoked());
    }

    private RefreshToken token(User user, String hash, String family, LocalDateTime created,
                               LocalDateTime expires, LocalDateTime sessionExpires) {
        return RefreshToken.builder()
                .id(1L)
                .user(user)
                .tokenHash(hash)
                .tokenFamilyId(family)
                .createdAt(created)
                .expiresAt(expires)
                .sessionExpiresAt(sessionExpires)
                .build();
    }

    private User user(Long id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .password("password")
                .firstName("Test")
                .lastName("User")
                .role(User.UserRole.CUSTOMER)
                .build();
    }
}
