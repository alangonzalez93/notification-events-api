package com.cobre.notifications.infrastructure.web.dto.response;

import com.cobre.notifications.domain.model.EventType;
import com.cobre.notifications.domain.model.NotificationEvent;

import java.time.OffsetDateTime;

public record NotificationEventDetailResponse(
        String uniqueCode,
        String eventId,
        EventType eventType,
        String payload,
        String deliveryStatus,
        Integer retryCount,
        String lastError,
        OffsetDateTime nextRetryAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime createdDate,
        OffsetDateTime lastModifiedDate
) {
    public static NotificationEventDetailResponse from(NotificationEvent e) {
        return new NotificationEventDetailResponse(
                e.uniqueCode(), e.eventId(), e.eventType(),
                e.payload(),
                e.deliveryStatus().toJson(),
                e.retryCount(), e.lastError(),
                e.nextRetryAt(), e.deliveredAt(),
                e.createdDate(), e.lastModifiedDate()
        );
    }
}
