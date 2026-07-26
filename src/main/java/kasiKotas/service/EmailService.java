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
                    .subject("Reset Your KasiKotas Password")
                    .html(buildOtpEmailHtml(firstName, otp))
                    .build();

            resend.emails().send(params);
        } catch (ResendException e) {
            throw new RuntimeException("Failed to send OTP email", e);
        }
    }

    private String buildOtpEmailHtml(String firstName, String otp) {
        String name = (firstName == null || firstName.isBlank()) ? "there" : firstName;

        return "<!DOCTYPE html>"
            + "<html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<style>"
            + "body{margin:0;padding:24px;background:#f5f5f5;font-family:Arial,Helvetica,sans-serif;color:#333;}"
            + ".email{max-width:600px;margin:auto;background:#fff;padding:40px;border:1px solid #e5e5e5;}"
            + ".logo{font-size:32px;font-weight:bold;color:#ff6b00;margin-bottom:30px;}"
            + "h2{margin:0 0 20px;font-size:24px;color:#222;}"
            + "p{font-size:16px;line-height:1.7;margin:16px 0;}"
            + ".otp{margin:32px 0;padding:18px;border:2px dashed #ff6b00;background:#fff8f2;"
            + "font:700 42px Consolas,Monaco,monospace;letter-spacing:8px;text-align:center;"
            + "-webkit-user-select:all;user-select:all;}"
            + ".footer{margin-top:36px;padding-top:20px;border-top:1px solid #ddd;font-size:13px;color:#777;}"
            + "</style></head><body>"
            + "<div class='email'>"
            + "<div class='logo'>KasiKotas</div>"
            + "<h2>Password Reset Request</h2>"
            + "<p>Hello <strong>" + name + "</strong>,</p>"
            + "<p>We received a request to reset the password for your KasiKotas account. Use the One-Time Password (OTP) below to continue.</p>"
            + "<div class='otp'>" + otp + "</div>"
            + "<p><strong>This code expires in 15 minutes.</strong></p>"
            + "<p>If you didn't request this password reset, you can safely ignore this email. Your password will remain unchanged.</p>"
            + "<p><strong>Security reminder:</strong> Never share this OTP with anyone. KasiKotas will never ask for your verification code by email, phone, or SMS.</p>"
            + "<p>Kind regards,<br><strong>The KasiKotas Team</strong></p>"
            + "<div class='footer'>© 2026 KasiKotas<br>This is an automated email. Please do not reply.</div>"
            + "</div></body></html>";
    }
}
