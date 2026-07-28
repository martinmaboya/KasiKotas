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
                    .subject("Your KasiKotas Reset Code: " + otp)
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
            + "body{margin:0;padding:20px;background:#f8f9fa;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;color:#2d3748;}"
            + ".container{max-width:480px;margin:auto;background:#ffffff;padding:32px;border-radius:8px;border:1px solid #e2e8f0;}"
            + ".logo{font-size:24px;font-weight:800;color:#ff6b00;margin-bottom:24px;}"
            + "h2{font-size:20px;margin:0 0 12px;color:#1a202c;}"
            + "p{font-size:15px;line-height:1.5;margin:12px 0;color:#4a5568;}"
            + ".otp{font-size:36px;font-weight:700;letter-spacing:6px;color:#1a202c;margin:20px 0;text-align:center;}"
            + ".subtext{font-size:13px;color:#718096;margin-top:24px;line-height:1.4;}"
            + "</style></head><body>"
            + "<div class='container'>"
            + "<div class='logo'>KasiKotas</div>"
            + "<h2>Reset your password</h2>"
            + "<p>Hi " + name + ", use this verification code to reset your password. It expires in 15 minutes.</p>"
            + "<div class='otp'>" + otp + "</div>"
            + "<p class='subtext'>If you didn't request this, you can safely ignore this email. Never share this code with anyone.</p>"
            + "</div></body></html>";
    }
}