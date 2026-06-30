package com.venvify.venvifycore.event.enums;

/**
 * Danh mục theo CHỦ ĐỀ của event, dùng để lọc & trang discover (SPEC: lọc/discover theo danh mục).
 * Lưu dưới dạng tên enum trong cột {@code category VARCHAR(50)}. Đây là trục "chủ đề", khác với
 * "hình thức" (workshop/AMA/talkshow...) — nếu sau này cần lọc theo hình thức thì thêm field riêng.
 */
public enum EventCategory {
    TECHNOLOGY,
    BUSINESS,
    MARKETING,
    DESIGN,
    FINANCE,
    CAREER,
    EDUCATION,
    PERSONAL_DEVELOPMENT,
    HEALTH,
    ARTS,
    OTHER
}
