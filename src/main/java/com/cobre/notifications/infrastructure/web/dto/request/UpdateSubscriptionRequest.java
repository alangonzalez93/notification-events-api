package com.cobre.notifications.infrastructure.web.dto.request;

import java.util.Set;

public record UpdateSubscriptionRequest(
        String webhookUrl,
        String authHeaderName,
        String authHeaderValue,
        Set<String> eventTypes,
        Boolean active
) {}
