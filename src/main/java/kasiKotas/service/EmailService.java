package kasiKotas.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * Send OTP email for password reset
     */
    public void sendOtpEmail(String toEmail, String firstName, String otp) {
        try {
            System.out.println("[EmailService] Attempting to send OTP email to: " + toEmail);
            System.out.println("[EmailService] From address: " + fromEmail);
            System.out.println("[EmailService] OTP: " + otp);
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Password Reset OTP - KasiKotas");
            
            String htmlContent = buildOtpEmailHtml(firstName, otp);
            helper.setText(htmlContent, true);
            
            System.out.println("[EmailService] Sending email via SMTP...");
            mailSender.send(message);
            System.out.println("[EmailService] ✅ OTP email sent successfully to: " + toEmail);
        } catch (MessagingException e) {
            System.err.println("[EmailService] ❌ MessagingException: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to send OTP email: " + e.getMessage(), e);
        } catch (Exception e) {
            System.err.println("[EmailService] ❌ Unexpected error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to send OTP email: " + e.getMessage(), e);
        }
    }

    /**
     * Build HTML content for OTP email
     */
    private String buildOtpEmailHtml(String firstName, String otp) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; }" +
                ".content { background-color: #f9f9f9; padding: 30px; border-radius: 5px; margin-top: 20px; }" +
                ".otp-box { background-color: #ffffff; border: 2px dashed #4CAF50; padding: 20px; text-align: center; margin: 20px 0; border-radius: 5px; }" +
                ".otp-code { font-size: 32px; font-weight: bold; color: #4CAF50; letter-spacing: 8px; }" +
                ".footer { text-align: center; margin-top: 20px; color: #666; font-size: 12px; }" +
                ".warning { color: #ff6b6b; font-weight: bold; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>KasiKotas</h1>" +
                "</div>" +
                "<div class='content'>" +
                "<h2>Hello " + firstName + ",</h2>" +
                "<p>We're sorry to hear you forgot your password. Use the OTP below to complete the password reset process:</p>" +
                "<div class='otp-box'>" +
                "<div class='otp-code'>" + otp + "</div>" +
                "</div>" +
                "<p><strong>This OTP is valid for 15 minutes.</strong></p>" +
                "<p>If you didn't request a password reset, please ignore this email or contact support if you have concerns.</p>" +
                "<p class='warning'>⚠️ Never share your OTP with anyone!</p>" +
                "</div>" +
                "<div class='footer'>" +
                "<p>© 2026 KasiKotas. All rights reserved.</p>" +
                "<p>This is an automated email. Please do not reply.</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }
}
