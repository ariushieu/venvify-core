package com.venvify.venvifycore.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;

/**
 * Khi app sẵn sàng: log URL Swagger (IntelliJ cho Ctrl+click) và — nếu bật
 * {@code app.open-swagger-on-start=true} — tự mở trình duyệt tới Swagger UI.
 * Mặc định TẮT để không chạy ở prod / không phiền khi devtools restart liên tục.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupBrowserLauncher {

    private final Environment env;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        String port = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        String url = "http://localhost:" + port + contextPath + "/swagger-ui.html";

        log.info("Swagger UI: {}", url);

        if (Boolean.parseBoolean(env.getProperty("app.open-swagger-on-start", "false"))) {
            openBrowser(url);
        }
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                log.warn("Môi trường không hỗ trợ mở browser tự động; mở thủ công: {}", url);
            }
        } catch (Exception e) {
            log.warn("Không mở được browser tự động ({}); mở thủ công: {}", e.getMessage(), url);
        }
    }
}
