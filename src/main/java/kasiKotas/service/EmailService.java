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
            "<html lang='en'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
            "<title>Reset Your Password</title>" +
            "<style>" +
            "  @import url('https://fonts.googleapis.com/css2?family=Poppins:wght@400;600;700;800&display=swap');" +
            "  * { margin: 0; padding: 0; box-sizing: border-box; }" +
            "  body { background-color: #0f0f0f; font-family: 'Poppins', Arial, sans-serif; }" +
            "  .wrapper { background-color: #0f0f0f; padding: 40px 20px; }" +
            "  .container { max-width: 580px; margin: 0 auto; background: #1a1a1a; border-radius: 24px; overflow: hidden; border: 1px solid #2a2a2a; }" +
            "  .hero { background: linear-gradient(135deg, #ff6b00 0%, #ff9a00 50%, #ffb347 100%); padding: 50px 40px; text-align: center; position: relative; }" +
            "  .hero::before { content: ''; position: absolute; top: -50%; left: -50%; width: 200%; height: 200%; background: radial-gradient(circle, rgba(255,255,255,0.08) 0%, transparent 60%); }" +
            "  .logo { font-size: 36px; font-weight: 800; color: #ffffff; letter-spacing: -1px; text-shadow: 0 2px 20px rgba(0,0,0,0.3); }" +
            "  .logo span { color: #fff3e0; }" +
            "  .hero-sub { color: rgba(255,255,255,0.85); font-size: 14px; margin-top: 6px; font-weight: 400; letter-spacing: 2px; text-transform: uppercase; }" +
            "  .body { padding: 44px 40px; }" +
            "  .greeting { font-size: 26px; font-weight: 700; color: #ffffff; margin-bottom: 12px; }" +
            "  .greeting span { color: #ff9a00; }" +
            "  .message { color: #a0a0a0; font-size: 15px; line-height: 1.7; margin-bottom: 36px; }" +
            "  .otp-label { font-size: 11px; font-weight: 600; color: #ff9a00; letter-spacing: 3px; text-transform: uppercase; text-align: center; margin-bottom: 14px; }" +
            "  .otp-container { background: #111111; border: 1px solid #2e2e2e; border-radius: 16px; padding: 32px 20px; text-align: center; margin-bottom: 36px; position: relative; overflow: hidden; }" +
            "  .otp-container::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 2px; background: linear-gradient(90deg, #ff6b00, #ff9a00, #ffb347); }" +
            "  .otp-code { font-size: 48px; font-weight: 800; letter-spacing: 14px; color: #ffffff; text-shadow: 0 0 30px rgba(255, 154, 0, 0.5); }" +
            "  .otp-timer { margin-top: 14px; font-size: 13px; color: #666; }" +
            "  .otp-timer strong { color: #ff9a00; }" +
            "  .divider { height: 1px; background: linear-gradient(90deg, transparent, #2a2a2a, transparent); margin: 32px 0; }" +
            "  .security-box { background: #111; border-radius: 12px; padding: 20px 24px; border-left: 3px solid #ff6b00; margin-bottom: 32px; }" +
            "  .security-box p { font-size: 13px; color: #888; line-height: 1.6; }" +
            "  .security-box p strong { color: #ff9a00; }" +
            "  .ignore-note { font-size: 13px; color: #555; text-align: center; line-height: 1.6; }" +
            "  .footer { background: #111; padding: 28px 40px; text-align: center; border-top: 1px solid #222; }" +
            "  .footer p { font-size: 12px; color: #444; line-height: 1.8; }" +
            "  .footer a { color: #ff9a00; text-decoration: none; }" +
            "  .kota-emoji { font-size: 48px; display: block; margin-bottom: 12px; }" +
            "</style></head><body>" +
            "<div class='wrapper'>" +
            "  <div class='container'>" +
            "    <div class='hero'>" +
            "      <div class='logo'>Kasi<span>Kotas</span></div>" +
            "      <div class='hero-sub'>Street Food. Delivered.</div>" +
            "    </div>" +
            "    <div class='body'>" +
            "      <span class='kota-emoji'>🔐</span>" +
            "      <div class='greeting'>Eish, <span>" + firstName + "!</span><br>Forgot your password?</div>" +
            "      <p class='message'>No stress — it happens to the best of us. Use the one-time code below to reset your password and get back to ordering your favourite kotas. This code is yours and yours alone.</p>" +
            "      <div class='otp-label'>Your one-time code</div>" +
            "      <div class='otp-container'>" +
            "        <div class='otp-code'>" + otp + "</div>" +
            "        <div class='otp-timer'>Expires in <strong>15 minutes</strong></div>" +
            "      </div>" +
            "      <div class='security-box'>" +
            "        <p>🛡️ <strong>Security reminder:</strong> KasiKotas will never call or message you asking for this code. If anyone asks for it — that's a scam. Keep it to yourself.</p>" +
            "      </div>" +
            "      <div class='divider'></div>" +
            "      <p class='ignore-note'>Didn't request a password reset? No action needed — just ignore this email. Your account is safe.</p>" +
            "    </div>" +
            "    <div class='footer'>" +
            "      <p>© 2026 KasiKotas. All rights reserved.<br>" +
            "      Made with 🧡 in South Africa.<br>" +
            "      This is an automated message — please do not reply.</p>" +
            "    </div>" +
            "  </div>" +
            "</div>" +
            "</body></html>";
    }
}
