package kasiKotas.controller;

import kasiKotas.factory.PasswordResetTokenFactory;
import kasiKotas.model.PasswordResetToken;
import kasiKotas.model.User;
import kasiKotas.repository.PasswordResetTokenRepository;
import kasiKotas.repository.UserRepository;
import kasiKotas.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        Optional<User> user = userRepository.findByEmail(email);
        Map<String, Object> response = new HashMap<>();
        
        if (user.isEmpty()) {
            // Return success even if user doesn't exist (security best practice)
            response.put("success", true);
            response.put("message", "If the email exists, an OTP has been sent");
            return ResponseEntity.ok(response);
        }
        
        try {
            // Generate 6-digit OTP
            String otp = generateOtp();
            
            // Create reset token with OTP
            PasswordResetToken resetToken = PasswordResetTokenFactory.create(
                user.get().getId(),
                email,
                15 // expiry in minutes
            );
            resetToken.setOtp(otp);
            tokenRepository.save(resetToken);
            
            // Send OTP email
            emailService.sendOtpEmail(email, user.get().getFirstName(), otp);
            
            response.put("success", true);
            response.put("message", "OTP has been sent to your email");
            System.out.println("OTP sent to " + email + ": " + otp); // For debugging
        } catch (Exception e) {
            System.err.println("Error sending OTP: " + e.getMessage());
            response.put("success", false);
            response.put("message", "Failed to send OTP. Please try again later.");
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Generate a random 6-digit OTP
     */
    private String generateOtp() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    // New endpoint to fetch user's first name by email
    @GetMapping("/user-firstname")
    public ResponseEntity<Map<String, Object>> getUserFirstName(@RequestParam("email") String email) {
        Optional<User> user = userRepository.findByEmail(email);
        Map<String, Object> response = new HashMap<>();
        if (user.isPresent()) {
            response.put("success", true);
            response.put("firstName", user.get().getFirstName());
        } else {
            response.put("success", false);
            response.put("firstName", "");
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
    
    /**
     * Verify OTP for password reset
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otp = request.get("otp");
        Map<String, Object> response = new HashMap<>();
        
        // Find the most recent unused token for this email
        Optional<PasswordResetToken> resetToken = tokenRepository
            .findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(email, LocalDateTime.now());
        
        if (resetToken.isEmpty()) {
            response.put("success", false);
            response.put("message", "No valid reset request found or OTP has expired");
            return ResponseEntity.badRequest().body(response);
        }
        
        // Verify OTP matches
        if (!otp.equals(resetToken.get().getOtp())) {
            response.put("success", false);
            response.put("message", "Invalid OTP");
            return ResponseEntity.badRequest().body(response);
        }
        
        response.put("success", true);
        response.put("message", "OTP verified successfully");
        response.put("token", resetToken.get().getToken()); // Return token for next step
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
        if (user.isEmpty()) {
            response.put("success", false);
            response.put("message", "User not found");
            return ResponseEntity.badRequest().body(response);
        }
        
        // Update password
        String hashedPassword = passwordEncoder.encode(newPassword);
        user.get().setPassword(hashedPassword);
        userRepository.save(user.get());
        
        // Mark token as used
        resetToken.get().setUsed(true);
        tokenRepository.save(resetToken.get());
        
        response.put("success", true);
        response.put("message", "Password reset successfully");
        return ResponseEntity.ok(response);
    }
    
    /**
     * Reset password using OTP directly (alternative flow)
     */
    @PostMapping("/reset-password-with-otp")
    public ResponseEntity<Map<String, Object>> resetPasswordWithOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otp = request.get("otp");
        String newPassword = request.get("newPassword");
        Map<String, Object> response = new HashMap<>();
        
        // Find the most recent unused token for this email
        Optional<PasswordResetToken> resetToken = tokenRepository
            .findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(email, LocalDateTime.now());
        
        if (resetToken.isEmpty()) {
            response.put("success", false);
            response.put("message", "No valid reset request found or OTP has expired");
            return ResponseEntity.badRequest().body(response);
        }
        
        // Verify OTP matches
        if (!otp.equals(resetToken.get().getOtp())) {
            response.put("success", false);
            response.put("message", "Invalid OTP");
            return ResponseEntity.badRequest().body(response);
        }
        
        // Find user and update password
        Optional<User> user = userRepository.findById(resetToken.get().getUserId());
        if (user.isEmpty()) {
            response.put("success", false);
            response.put("message", "User not found");
            return ResponseEntity.badRequest().body(response);
        }
        
        // Update password
        String hashedPassword = passwordEncoder.encode(newPassword);
        user.get().setPassword(hashedPassword);
        userRepository.save(user.get());
        
        // Mark token as used
        resetToken.get().setUsed(true);
        tokenRepository.save(resetToken.get());
        
        response.put("success", true);
        response.put("message", "Password reset successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * Reset password by email only — OTP already verified on frontend via EmailJS.
     * No server-side OTP check is performed.
     */
    @PostMapping("/reset-password-by-email")
    public ResponseEntity<Map<String, Object>> resetPasswordByEmail(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String newPassword = request.get("newPassword");
        Map<String, Object> response = new HashMap<>();

        Optional<User> user = userRepository.findByEmail(email);
        if (user.isEmpty()) {
            response.put("success", false);
            response.put("message", "User not found");
            return ResponseEntity.badRequest().body(response);
        }

        user.get().setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user.get());

        response.put("success", true);
        response.put("message", "Password reset successfully");
        return ResponseEntity.ok(response);
    }
}
