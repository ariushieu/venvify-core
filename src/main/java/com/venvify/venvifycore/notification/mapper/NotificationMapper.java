package com.venvify.venvifycore.notification.mapper;

import com.venvify.venvifycore.notification.dto.NotificationResponse;
import com.venvify.venvifycore.notification.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);
}
