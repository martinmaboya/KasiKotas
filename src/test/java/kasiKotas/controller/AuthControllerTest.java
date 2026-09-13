package kasiKotas.controller;

import kasiKotas.model.User;
import kasiKotas.security.JwtUtil;
import kasiKotas.service.RefreshTokenService;
import kasiKotas.service.UserService;
import kasiKotas.service.passkey.PasskeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasskeyService passkeyService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController();
        setField("userService", userService);
        setField("jwtUtil", jwtUtil);
        setField("passkeyService", passkeyService);
        setField("refreshTokenService", refreshTokenService);
    }

    @Test
    void loginUserIncludesPasskeyEnrollmentWhenRequested() {
        User user = buildUser();
        when(userService.authenticateUser("user@example.com", "password")).thenReturn(Optional.of(user));
        when(refreshTokenService.issueTokenPair(user)).thenReturn(new RefreshTokenService.TokenPair(
            "jwt-token", "refresh-token", null));
        when(passkeyService.hasPasskeyEnrollment(user.getId())).thenReturn(false);
        when(passkeyService.createRegistrationOptions(user)).thenReturn(Map.of(
                "requestId", "request-123",
                "publicKey", Map.of("challenge", "abc")
        ));

        ResponseEntity<?> response = authController.loginUser(Map.of(
                "email", "user@example.com",
                "password", "password",
                "enablePasskey", true
        ));

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = castMap(response.getBody());
        assertEquals("jwt-token", body.get("token"));
        assertTrue(body.containsKey("passkeyEnrollment"));
        Map<?, ?> enrollment = castMap(body.get("passkeyEnrollment"));
        assertEquals("required", enrollment.get("status"));
        Map<?, ?> registration = castMap(enrollment.get("registration"));
        assertEquals("request-123", registration.get("requestId"));
    }

    @Test
    void loginUserSkipsPasskeyEnrollmentWhenNotRequested() {
        User user = buildUser();
        when(userService.authenticateUser("user@example.com", "password")).thenReturn(Optional.of(user));
        when(refreshTokenService.issueTokenPair(user)).thenReturn(new RefreshTokenService.TokenPair(
            "jwt-token", "refresh-token", null));

        ResponseEntity<?> response = authController.loginUser(Map.of(
                "email", "user@example.com",
                "password", "password"
        ));

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = castMap(response.getBody());
        assertFalse(body.containsKey("passkeyEnrollment"));
    }

    @Test
    void refreshDoesNotRequireAnAccessTokenPrincipal() {
        RefreshTokenService.TokenPair tokenPair = new RefreshTokenService.TokenPair(
                "new-access-token", "new-refresh-token", null);
        when(refreshTokenService.rotateRefreshToken("old-refresh-token", null)).thenReturn(tokenPair);

        ResponseEntity<?> response = authController.refreshToken(Map.of("refreshToken", "old-refresh-token"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("new-access-token", castMap(response.getBody()).get("accessToken"));
        verify(userService, never()).getUserByEmail(org.mockito.ArgumentMatchers.anyString());
    }

    private User buildUser() {
        return User.builder()
                .id(1L)
                .email("user@example.com")
                .password("password")
                .firstName("John")
                .lastName("Doe")
                .role(User.UserRole.CUSTOMER)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> castMap(Object value) {
        return (Map<?, ?>) value;
    }

    private void setField(String fieldName, Object value) {
        try {
            Field field = AuthController.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(authController, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set field " + fieldName, e);
        }
    }
}