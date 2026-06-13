package com.lezifx.trading.service.notification;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final RestTemplate  restTemplate;

    @Value("${lezifx.email.from-address:noreply@lezifx.com}")
    private String fromAddress;

    @Value("${lezifx.email.from-name:LeziFx}")
    private String fromName;

    @Value("${BREVO_API_KEY:}")
    private String brevoApiKey;

    private static final String BREVO_SEND_URL = "https://api.brevo.com/v3/smtp/email";

    @Async
    public void sendPasswordResetOtp(String toEmail, String fullName, String otp, String brandName) {
        String name    = (fullName != null && !fullName.isBlank()) ? fullName.split(" ")[0] : "Trader";
        String display = (brandName != null && !brandName.isBlank()) ? brandName : "LeziFx";
        String subject = "Your " + display + " password reset code";
        String html    = buildOtpEmail(name, otp, display);

        if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            try {
                sendViaBrevoApi(toEmail, subject, html);
                return;
            } catch (Exception e) {
                log.warn("Brevo API failed, falling back to SMTP: {}", e.getMessage());
            }
        }
        sendViaSMTP(toEmail, subject, html);
    }

    private void sendViaBrevoApi(String toEmail, String subject, String html) {
        Map<String, Object> payload = Map.of(
            "sender",      Map.of("name", fromName, "email", fromAddress),
            "to",          List.of(Map.of("email", toEmail)),
            "subject",     subject,
            "htmlContent", html
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", brevoApiKey);

        ResponseEntity<String> response = restTemplate.exchange(
            BREVO_SEND_URL,
            HttpMethod.POST,
            new HttpEntity<>(payload, headers),
            String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Brevo API returned " + response.getStatusCode());
        }
        log.info("Password reset OTP sent via Brevo API to {}", toEmail);
    }

    private void sendViaSMTP(String toEmail, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
            log.info("Password reset OTP sent via SMTP to {}", toEmail);
        } catch (Exception e) {
            log.error("SMTP send failed to {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildOtpEmail(String name, String otp, String brandName) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
            </head>
            <body style="margin:0;padding:0;background:#0d1117;font-family:'DM Sans',Arial,sans-serif;">
              <div style="max-width:520px;margin:0 auto;padding:40px 16px;">
                <div style="background:#161b22;border-radius:16px;border:1px solid #21262d;overflow:hidden;">
                  <div style="background:linear-gradient(135deg,#00b8d4,#00e676);padding:28px 32px;">
                    <div style="font-size:22px;font-weight:900;color:#000;letter-spacing:-0.02em;">%s</div>
                    <div style="font-size:11px;color:rgba(0,0,0,0.6);letter-spacing:0.15em;text-transform:uppercase;margin-top:4px;">Trading Platform</div>
                  </div>
                  <div style="padding:32px;">
                    <h2 style="margin:0 0 8px;font-size:20px;font-weight:800;color:#e6edf3;">Password reset, %s</h2>
                    <p style="margin:0 0 24px;font-size:14px;color:#8b949e;line-height:1.6;">
                      Use the code below to reset your password. It expires in <strong style="color:#e6edf3;">10 minutes</strong>.
                    </p>
                    <div style="background:#0d1117;border:1px solid #21262d;border-radius:12px;padding:28px;text-align:center;margin-bottom:24px;">
                      <div style="font-size:42px;font-weight:900;letter-spacing:14px;color:#00e676;font-family:'Courier New',monospace;">%s</div>
                    </div>
                    <p style="margin:0;font-size:12px;color:#6e7681;line-height:1.6;">
                      If you did not request this, you can safely ignore this email — your password will not change.
                    </p>
                  </div>
                  <div style="padding:20px 32px;border-top:1px solid #21262d;font-size:11px;color:#6e7681;text-align:center;">
                    &copy; %s &nbsp;·&nbsp; This is an automated message, do not reply.
                  </div>
                </div>
              </div>
            </body>
            </html>
            """.formatted(brandName, name, otp, brandName);
    }
}
