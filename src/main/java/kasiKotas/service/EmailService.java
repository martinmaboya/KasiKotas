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
            System.out.println("✅ OTP email sent successfully to: " + toEmail);

        } catch (ResendException e) {
            System.err.println("❌ Failed to send email: " + e.getMessage());
            throw new RuntimeException("Failed to send OTP email", e);
        }
    }

    private String buildOtpEmailHtml(String firstName, String otp) {

        return "<!DOCTYPE html>" +
                "<html lang='en'>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<title>Reset Your Password</title>" +

                "<style>" +

                "@import url('https://fonts.googleapis.com/css2?family=Poppins:wght@400;500;600;700;800&display=swap');" +

                "*{margin:0;padding:0;box-sizing:border-box;}" +

                "body{" +
                "background:#f4f6f9;" +
                "font-family:'Poppins',Arial,sans-serif;" +
                "padding:30px;" +
                "}" +

                ".container{" +
                "max-width:620px;" +
                "margin:auto;" +
                "background:#ffffff;" +
                "border-radius:20px;" +
                "overflow:hidden;" +
                "box-shadow:0 12px 35px rgba(0,0,0,.08);" +
                "}" +

                ".header{" +
                "background:linear-gradient(135deg,#ff6b00,#ff9800);" +
                "padding:50px 40px;" +
                "text-align:center;" +
                "}" +

                ".logo{" +
                "font-size:38px;" +
                "font-weight:800;" +
                "color:white;" +
                "}" +

                ".subtitle{" +
                "margin-top:10px;" +
                "color:rgba(255,255,255,.9);" +
                "font-size:15px;" +
                "letter-spacing:1px;" +
                "}" +

                ".content{" +
                "padding:45px 40px;" +
                "}" +

                "h2{" +
                "font-size:28px;" +
                "color:#222;" +
                "margin-bottom:18px;" +
                "}" +

                "p{" +
                "font-size:15px;" +
                "line-height:1.8;" +
                "color:#555;" +
                "}" +

                ".otp-label{" +
                "margin-top:35px;" +
                "text-align:center;" +
                "font-size:12px;" +
                "font-weight:600;" +
                "letter-spacing:2px;" +
                "text-transform:uppercase;" +
                "color:#ff6b00;" +
                "}" +

                ".otp-box{" +
                "margin-top:15px;" +
                "padding:30px;" +
                "background:#f8f9fa;" +
                "border:2px dashed #ff9800;" +
                "border-radius:18px;" +
                "text-align:center;" +
                "}" +

                ".otp{" +
                "font-size:46px;" +
                "font-weight:800;" +
                "letter-spacing:12px;" +
                "color:#222;" +
                "}" +

                ".expire{" +
                "margin-top:12px;" +
                "font-size:14px;" +
                "color:#666;" +
                "}" +

                ".security{" +
                "margin-top:35px;" +
                "padding:22px;" +
                "background:#fff8f1;" +
                "border-left:5px solid #ff6b00;" +
                "border-radius:10px;" +
                "}" +

                ".security strong{" +
                "color:#ff6b00;" +
                "}" +

                ".divider{" +
                "margin:35px 0;" +
                "height:1px;" +
                "background:#e5e5e5;" +
                "}" +

                ".footer{" +
                "background:#fafafa;" +
                "padding:30px;" +
                "text-align:center;" +
                "font-size:13px;" +
                "color:#777;" +
                "line-height:1.8;" +
                "border-top:1px solid #ececec;" +
                "}" +

                ".footer strong{" +
                "color:#222;" +
                "}" +

                "</style>" +
                "</head>" +

                "<body>" +

                "<div class='container'>" +

                "<div class='header'>" +
                "<div class='logo'>KasiKotas</div>" +
                "<div class='subtitle'>Fresh Kotas. Fast Delivery.</div>" +
                "</div>" +

                "<div class='content'>" +

                "<h2>Hello, " + firstName + "</h2>" +

                "<p>" +
                "We received a request to reset the password for your KasiKotas account. " +
                "To continue, please use the One-Time Password (OTP) below." +
                "</p>" +

                "<div class='otp-label'>One-Time Password</div>" +

                "<div class='otp-box'>" +
                "<div class='otp'>" + otp + "</div>" +
                "<div class='expire'>This code expires in <strong>15 minutes</strong>.</div>" +
                "</div>" +

                "<div class='security'>" +
                "<p>" +
                "<strong>Security Reminder</strong><br><br>" +
                "Never share this OTP with anyone. KasiKotas will never ask for your verification code " +
                "by phone, email, or SMS." +
                "</p>" +
                "</div>" +

                "<div class='divider'></div>" +

                "<p>" +
                "If you did not request a password reset, you can safely ignore this email. " +
                "Your account remains secure and no changes have been made." +
                "</p>" +

                "<br><br>" +

                "<p>" +
                "Kind regards,<br>" +
                "<strong>The KasiKotas Team</strong>" +
                "</p>" +

                "</div>" +

                "<div class='footer'>" +
                "© 2026 <strong>KasiKotas</strong><br>" +
                "This is an automated email sent in response to a password reset request.<br>" +
                "Please do not reply to this message." +
                "</div>" +

                "</div>" +

                "</body>" +
                "</html>";
    }
}