package com.cobre.notifications.domain.model;

import java.time.OffsetDateTime;

public record NotificationEvent(
        Long id,
        String uniqueCode,
        String eventId,
        EventType eventType,
        String payload,
        String clientUniqueCode,
        String subscriptionUniqueCode,
        DeliveryStatus deliveryStatus,
        OffsetDateTime deliveredAt,
        Integer retryCount,
        OffsetDateTime nextRetryAt,
        String lastError,
        Long version,
        Boolean deleted,
        OffsetDateTime createdDate,
        OffsetDateTime lastModifiedDate
) {}
