package com.cobre.notifications.domain.model;

public record PlatformEvent(
        String eventId,
        EventType eventType,
        String payload,
        String clientUniqueCode
) {}
