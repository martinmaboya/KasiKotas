package kasiKotas.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final Resend resend;

    public EmailService(@Value("${resend.api.key}") String apiKey) {
        this.resend = new Resend(apiKey);
    }

    public void sendOtpEmail(String toEmail, String firstName, String otp) {
        try {
            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from("KasiKotas <no-reply@kasikotas.co.za>")
                    .to(toEmail)
                    .subject("Password Reset OTP - KasiKotas")
                    .html(buildOtpEmailHtml(firstName, otp))
                    .build();

            resend.emails().send(params);
            System.out.println("[EmailService] ✅ OTP email sent successfully to: " + toEmail);
        } catch (ResendException e) {
            System.err.println("[EmailService] ❌ ResendException: " + e.getMessage());
            throw new RuntimeException("Failed to send OTP email: " + e.getMessage(), e);
        }
    }

    private String buildOtpEmailHtml(String firstName, String otp) {
        return "<!DOCTYPE html>" +
                "<html><head><style>" +
                "body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; }" +
                ".content { background-color: #f9f9f9; padding: 30px; border-radius: 5px; margin-top: 20px; }" +
                ".otp-box { background-color: #ffffff; border: 2px dashed #4CAF50; padding: 20px; text-align: center; margin: 20px 0; border-radius: 5px; }" +
                ".otp-code { font-size: 32px; font-weight: bold; color: #4CAF50; letter-spacing: 8px; }" +
                ".footer { text-align: center; margin-top: 20px; color: #666; font-size: 12px; }" +
                ".warning { color: #ff6b6b; font-weight: bold; }" +
                "</style></head><body>" +
                "<div class='container'>" +
                "<div class='header'><h1>KasiKotas</h1></div>" +
                "<div class='content'>" +
                "<h2>Hello " + firstName + ",</h2>" +
                "<p>Use the OTP below to complete your password reset:</p>" +
                "<div class='otp-box'><div class='otp-code'>" + otp + "</div></div>" +
                "<p><strong>This OTP is valid for 15 minutes.</strong></p>" +
                "<p>If you didn't request this, please ignore this email.</p>" +
                "<p class='warning'>⚠️ Never share your OTP with anyone!</p>" +
                "</div>" +
                "<div class='footer'>" +
                "<p>© 2026 KasiKotas. All rights reserved.</p>" +
                "<p>This is an automated email. Please do not reply.</p>" +
                "</div></div></body></html>";
    }
}
