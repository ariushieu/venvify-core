package com.venvify.venvifycore.common.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
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

    public ResendEmailService(
            @Value("${resend.api-key}") String apiKey,
            @Value("${app.mail.from}") String from) {
        this.apiKey = apiKey;
        this.from = from;
        this.restClient = RestClient.create();
    }

    @Override
    public void sendVerificationOtp(String toEmail, String fullName, String otp) {
        String html = """
                <p>Xin chào %s,</p>
                <p>Cảm ơn bạn đã đăng ký Venvify. Mã xác thực email của bạn là:</p>
                <p style="font-size:32px;font-weight:700;letter-spacing:8px;font-family:monospace">%s</p>
                <p>Mã có hiệu lực trong 10 phút. Nếu bạn không đăng ký, hãy bỏ qua email này.</p>
                """.formatted(fullName, otp);

        try {
            restClient.post()
                    .uri(RESEND_ENDPOINT)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", from,
                            "to", List.of(toEmail),
                            "subject", "Xác thực email Venvify",
                            "html", html))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Sent verification email to {}", toEmail);
        } catch (Exception ex) {
            log.warn("Failed to send verification email to {}: {}", toEmail, ex.getMessage());
        }
    }
}
