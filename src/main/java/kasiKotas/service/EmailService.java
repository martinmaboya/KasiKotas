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
                    .subject("Reset your KasiKotas password")
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
            + "body{margin:0;padding:24px;background:#f8f9fa;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;color:#333;}"
            + ".container{max-width:440px;margin:auto;background:#fff;padding:32px;border-radius:10px;border:1px solid #eaeaea;}"
            + ".logo{font-size:22px;font-weight:800;color:#ff6b00;margin-bottom:20px;}"
            + "p{font-size:15px;line-height:1.6;margin:12px 0;color:#444;}"
            + ".otp{font-size:38px;font-weight:800;letter-spacing:8px;color:#111;margin:24px 0;text-align:center;}"
            + ".subtext{font-size:13px;color:#777;margin-top:24px;line-height:1.5;}"
            + "</style></head><body>"
            + "<div class='container'>"
            + "<div class='logo'>KasiKotas</div>"
            + "<p>Hi " + name + ",</p>"
            + "<p>A password reset request has been made for your KasiKotas account. Enter the verification code below to proceed:</p>"
            + "<div class='otp'>" + otp + "</div>"
            + "<p>This code is valid for 15 minutes.</p>"
            + "<p class='subtext'>If you didn't request a password reset, you can safely ignore this email.</p>"
            + "</div></body></html>";
    }
}