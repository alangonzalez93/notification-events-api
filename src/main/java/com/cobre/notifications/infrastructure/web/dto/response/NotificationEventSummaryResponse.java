package com.cobre.notifications.infrastructure.web.dto.response;

import com.cobre.notifications.domain.model.EventType;
import com.cobre.notifications.domain.model.NotificationEvent;

import java.time.OffsetDateTime;

public record NotificationEventSummaryResponse(
        String uniqueCode,
        String eventId,
        EventType eventType,
        String deliveryStatus,
        Integer retryCount,
        String lastError,
        OffsetDateTime createdDate,
        OffsetDateTime deliveredAt
) {
    public static NotificationEventSummaryResponse from(NotificationEvent e) {
        return new NotificationEventSummaryResponse(
                e.uniqueCode(), e.eventId(), e.eventType(),
                e.deliveryStatus().toJson(),
                e.retryCount(), e.lastError(),
                e.createdDate(), e.deliveredAt()
        );
    }
}
