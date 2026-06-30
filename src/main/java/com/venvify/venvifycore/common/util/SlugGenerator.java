package com.venvify.venvifycore.common.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Sinh slug SEO-friendly từ tiêu đề (plan §1 E2). Hỗ trợ tiếng Việt:
 * bỏ dấu (NFD) và quy đổi đ/Đ → d trước khi chỉ giữ [a-z0-9-].
 * Tính duy nhất do service đảm bảo (thêm hậu tố khi trùng), KHÔNG xử lý ở đây.
 */
public final class SlugGenerator {

    private SlugGenerator() {
    }

    public static String toSlug(String input) {
        if (input == null) {
            return "";
        }
        String slug = input.trim().toLowerCase(Locale.ENGLISH);
        slug = slug.replace('đ', 'd');
        slug = Normalizer.normalize(slug, Normalizer.Form.NFD);
        slug = slug.replaceAll("\\p{M}+", "");      // bỏ dấu thanh/dấu phụ
        slug = slug.replaceAll("[^a-z0-9\\s-]", ""); // chỉ giữ chữ thường, số, khoảng trắng, gạch
        slug = slug.replaceAll("[\\s-]+", "-");      // gộp khoảng trắng/gạch thành một dấu '-'
        slug = slug.replaceAll("^-+|-+$", "");        // cắt gạch ở hai đầu
        return slug;
    }
}
