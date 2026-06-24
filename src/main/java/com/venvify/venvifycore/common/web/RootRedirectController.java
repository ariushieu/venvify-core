package com.venvify.venvifycore.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Tiện dev: mở root context ({@code /api/v1/}) sẽ redirect thẳng tới Swagger UI.
 * Khi cần dùng root cho mục đích khác, đổi/bỏ mapping này.
 */
@Controller
public class RootRedirectController {

    @GetMapping("/")
    public String redirectToSwagger() {
        return "redirect:/swagger-ui.html";
    }
}
