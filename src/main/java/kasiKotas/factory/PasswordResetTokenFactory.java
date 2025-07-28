package kasiKotas.factory;

import kasiKotas.model.PasswordResetToken;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

public class PasswordResetTokenFactory {
    public static PasswordResetToken create(Long userId, String email, int expiryMinutes) {
        String token = generateSecureToken();
        LocalDateTime now = LocalDateTime.now();
        return PasswordResetToken.builder()
                .userId(userId)
                .email(email)
                .token(token)
                .expiresAt(now.plusMinutes(expiryMinutes))
                .used(false)
                .createdAt(now)
                .build();
    }

    private static String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

