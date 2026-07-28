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
        String name = (firstName == null || firstName.isBlank()) ? "there" : firstName;

        try {
            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from("KasiKotas <no-reply@kasikotas.co.za>") // Works directly with your verified domain
                    .to(toEmail)
                    .subject("Reset your KasiKotas password")
                    .text(buildOtpEmailText(name, otp)) // Plain text fallback
                    .html(buildOtpEmailHtml(name, otp))
                    .build();

            resend.emails().send(params);
        } catch (ResendException e) {
            throw new RuntimeException("Failed to send OTP email", e);
        }
    }

    private String buildOtpEmailHtml(String name, String otp) {
        return "<!DOCTYPE html>"
            + "<html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
            + "<body style='margin:0;padding:24px;background:#f8f9fa;font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,sans-serif;color:#333;'>"
            + "<div style='max-width:440px;margin:auto;background:#fff;padding:32px;border-radius:10px;border:1px solid #eaeaea;'>"
            + "<div style='font-size:22px;font-weight:800;color:#ff6b00;margin-bottom:20px;'>KasiKotas</div>"
            + "<p style='font-size:15px;line-height:1.6;margin:12px 0;color:#444;'>Hi " + name + ",</p>"
            + "<p style='font-size:15px;line-height:1.6;margin:12px 0;color:#444;'>A password reset request has been made for your KasiKotas account. Enter the verification code below to proceed:</p>"
            + "<div style='font-size:38px;font-weight:800;letter-spacing:8px;color:#111;margin:24px 0;text-align:center;'>" + otp + "</div>"
            + "<p style='font-size:15px;line-height:1.6;margin:12px 0;color:#444;'>This code is valid for 10 minutes.</p>"
            + "<p style='font-size:13px;color:#777;margin-top:24px;line-height:1.5;'>If you didn't request a password reset, you can safely ignore this email.</p>"
            + "</div></body></html>";
    }

    private String buildOtpEmailText(String name, String otp) {
        return "Hi " + name + ",\n\n"
            + "A password reset request has been made for your KasiKotas account. Enter the verification code below to proceed:\n\n"
            + otp + "\n\n"
            + "This code is valid for 10 minutes.\n\n"
            + "If you didn't request a password reset, you can safely ignore this email.";
    }
}