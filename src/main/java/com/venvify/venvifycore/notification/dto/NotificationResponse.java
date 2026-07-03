package com.venvify.venvifycore.notification.dto;

import com.venvify.venvifycore.notification.enums.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        String publicId,
        NotificationType type,
        String title,
        String content,
        boolean read,
        String relatedEntityType,
        String relatedEntityPublicId,
        Instant createdAt
) {
}
