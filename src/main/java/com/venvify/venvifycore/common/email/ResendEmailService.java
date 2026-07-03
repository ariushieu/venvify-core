package com.venvify.venvifycore.common.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Gửi email qua Resend (https://resend.com). API key + sender lấy từ env.
 * Lỗi gửi mail KHÔNG chặn nghiệp vụ gọi (vd đăng ký) — chỉ log; user có thể yêu cầu gửi lại.
 */
@Slf4j
@Service
public class ResendEmailService implements EmailService {

    private static final String RESEND_ENDPOINT = "https://api.resend.com/emails";

    private final RestClient restClient;
    private final String apiKey;
    private final String from;
    private final String adminEmail;

    public ResendEmailService(
            @Value("${resend.api-key}") String apiKey,
            @Value("${app.mail.from}") String from,
            @Value("${app.ops.admin-email:}") String adminEmail) {
        this.apiKey = apiKey;
        this.from = from;
        this.adminEmail = adminEmail;
        this.restClient = RestClient.create();
    }

    @Override
    public void sendVerificationOtp(String toEmail, String fullName, String otp) {
        // Template theo design system "Ink & Spotlight" của FE (canvas sáng + thẻ live-pass đen,
        // mã vàng glow). Table + inline style vì email client không ăn CSS hiện đại;
        // %% là ký tự % literal (String.formatted).
        String html = """
                <div style="margin:0;padding:32px 16px;background-color:#faf8f5;font-family:Arial,Helvetica,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:440px;margin:0 auto;">
                    <tr>
                      <td style="padding:0 4px 14px;">
                        <span style="font-size:19px;font-weight:700;color:#14110f;letter-spacing:-0.5px;">venvify</span>
                      </td>
                    </tr>
                    <tr>
                      <td style="background-color:#14110f;border-radius:16px;padding:30px 28px;">
                        <p style="margin:0;font-size:11px;letter-spacing:3px;text-transform:uppercase;color:#e0a84b;">Live pass &middot; Email verification</p>
                        <p style="margin:18px 0 0;font-size:15px;line-height:1.6;color:#f5f1ea;">Xin chào %s,</p>
                        <p style="margin:6px 0 0;font-size:14px;line-height:1.6;color:#b8b0a4;">Nhập mã dưới đây để kích hoạt tài khoản Venvify của bạn:</p>
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin:22px 0;">
                          <tr>
                            <td align="center" style="background-color:#1d1915;border:1px dashed rgba(233,180,76,0.45);border-radius:12px;padding:20px 12px;">
                              <span style="font-family:'Courier New',monospace;font-size:34px;font-weight:700;letter-spacing:10px;padding-left:10px;color:#e9b44c;">%s</span>
                            </td>
                          </tr>
                        </table>
                        <p style="margin:0;font-size:13px;line-height:1.6;color:#b8b0a4;">Mã có hiệu lực trong <strong style="color:#f5f1ea;">10 phút</strong> và chỉ dùng được một lần.</p>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:16px 4px 0;">
                        <p style="margin:0;font-size:12px;line-height:1.6;color:#8a8378;">Nếu bạn không đăng ký Venvify, hãy bỏ qua email này &mdash; không ai truy cập được tài khoản của bạn khi thiếu mã.</p>
                      </td>
                    </tr>
                  </table>
                </div>
                """.formatted(fullName, otp);

        try {
            restClient.post()
                    .uri(RESEND_ENDPOINT)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", from,
                            "to", List.of(toEmail),
                            // Mã nằm luôn trong subject để thấy được từ notification, khỏi mở mail.
                            "subject", otp + " là mã xác thực Venvify của bạn",
                            "html", html))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Sent verification email to {}", toEmail);
        } catch (Exception ex) {
            log.warn("Failed to send verification email to {}: {}", toEmail, ex.getMessage());
        }
    }

    @Override
    @Async("emailExecutor")
    public void sendNotificationEmail(String toEmail, String subject, String bodyText) {
        // Text thuần bọc khung tối giản cùng tông "Ink & Spotlight"; escape HTML vì nội dung
        // có thể chứa title event do user đặt.
        String safe = bodyText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>");
        String html = """
                <div style="margin:0;padding:32px 16px;background-color:#faf8f5;font-family:Arial,Helvetica,sans-serif;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:440px;margin:0 auto;">
                    <tr><td style="padding:0 4px 14px;">
                      <span style="font-size:19px;font-weight:700;color:#14110f;letter-spacing:-0.5px;">venvify</span>
                    </td></tr>
                    <tr><td style="background-color:#14110f;border-radius:16px;padding:30px 28px;">
                      <p style="margin:0;font-size:14px;line-height:1.7;color:#f5f1ea;">%s</p>
                    </td></tr>
                  </table>
                </div>
                """.formatted(safe);
        try {
            restClient.post()
                    .uri(RESEND_ENDPOINT)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", from,
                            "to", List.of(toEmail),
                            "subject", subject,
                            "html", html))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Sent notification email to {}: {}", toEmail, subject);
        } catch (Exception ex) {
            log.warn("Failed to send notification email to {}: {}", toEmail, ex.getMessage());
        }
    }

    @Override
    public void sendOpsAlert(String subject, String body) {
        if (adminEmail == null || adminEmail.isBlank()) {
            // Không có nơi nhận thì ít nhất phải gào to trong log — đây là P0.
            log.error("OPS ALERT NOT DELIVERED (app.ops.admin-email chưa cấu hình): {}\n{}", subject, body);
            return;
        }
        // Nội dung kỹ thuật cho admin — <pre> giữ nguyên xuống dòng, khỏi cần template đẹp.
        String html = "<pre style=\"font-family:monospace;font-size:13px;\">"
                + body.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                + "</pre>";
        try {
            restClient.post()
                    .uri(RESEND_ENDPOINT)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", from,
                            "to", List.of(adminEmail),
                            "subject", subject,
                            "html", html))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Sent ops alert to {}: {}", adminEmail, subject);
        } catch (Exception ex) {
            // Job gọi không được chết vì mail — nhưng lỗi này bản thân nó cũng đáng ERROR.
            log.error("Failed to send ops alert '{}' to {}: {}", subject, adminEmail, ex.getMessage());
        }
    }
}
