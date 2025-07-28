package kasiKotas.controller;

import kasiKotas.factory.PasswordResetTokenFactory;
import kasiKotas.model.PasswordResetToken;
import kasiKotas.model.User;
import kasiKotas.repository.PasswordResetTokenRepository;
import kasiKotas.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        Optional<User> user = userRepository.findByEmail(email);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Reset link sent if email exists");
        if (user.isPresent()) {
            PasswordResetToken resetToken = PasswordResetTokenFactory.create(
                user.get().getId(),
                email,
                15 // expiry in minutes
            );
            tokenRepository.save(resetToken);
            // Frontend will handle email sending
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/get-reset-token")
    public ResponseEntity<Map<String, Object>> getResetToken(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        Map<String, Object> response = new HashMap<>();
        Optional<PasswordResetToken> token = tokenRepository.findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(email, LocalDateTime.now());
        if (token.isPresent()) {
            response.put("token", token.get().getToken());
            response.put("success", true);
        } else {
            response.put("success", false);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String token = request.get("token");
        String newPassword = request.get("newPassword");
        Map<String, Object> response = new HashMap<>();
        Optional<PasswordResetToken> resetToken = tokenRepository.findByTokenAndEmailAndUsedFalseAndExpiresAtAfter(token, email, LocalDateTime.now());
        if (resetToken.isEmpty()) {
            response.put("success", false);
            response.put("message", "Invalid or expired token");
            return ResponseEntity.badRequest().body(response);
        }
        Optional<User> user = userRepository.findById(resetToken.get().getUserId());
        if (user.isPresent()) {
            String hashedPassword = passwordEncoder.encode(newPassword);
            user.get().setPassword(hashedPassword);
            userRepository.save(user.get());
            resetToken.get().setUsed(true);
            tokenRepository.save(resetToken.get());
            response.put("success", true);
            response.put("message", "Password reset successfully");
            return ResponseEntity.ok(response);
        }
        response.put("success", false);
        response.put("message", "User not found");
        return ResponseEntity.badRequest().body(response);
    }
}
