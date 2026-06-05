package com.cobre.notifications.domain.port.in;

import com.cobre.notifications.domain.model.Subscription;

import java.util.Set;

public interface CreateSubscriptionUseCase {
    record Command(String clientUniqueCode, String webhookUrl,
                   String authHeaderName, String authHeaderValue,
                   Set<String> eventTypes) {}

    Subscription create(Command command);
}
