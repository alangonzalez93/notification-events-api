package com.cobre.notifications.infrastructure.web.dto.response;

import com.cobre.notifications.domain.model.EventType;
import com.cobre.notifications.domain.model.Subscription;

import java.time.OffsetDateTime;
import java.util.Set;

public record SubscriptionResponse(
        String uniqueCode,
        String webhookUrl,
        String authHeaderName,
        Set<EventType> eventTypes,
        Boolean active,
        OffsetDateTime createdDate
) {
    public static SubscriptionResponse from(Subscription s) {
        return new SubscriptionResponse(
                s.uniqueCode(), s.webhookUrl(), s.authHeaderName(),
                s.eventTypes(), s.active(), s.createdDate()
        );
    }
}
