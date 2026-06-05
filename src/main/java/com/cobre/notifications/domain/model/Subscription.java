package com.cobre.notifications.domain.model;

import java.time.OffsetDateTime;
import java.util.Set;

public record Subscription(
        Long id,
        String uniqueCode,
        String clientUniqueCode,
        String webhookUrl,
        String authHeaderName,
        String authHeaderValue,
        Set<EventType> eventTypes,
        Boolean active,
        Boolean deleted,
        OffsetDateTime createdDate,
        OffsetDateTime lastModifiedDate
) {}
