package com.cobre.notifications.domain.port.in;

import com.cobre.notifications.domain.model.Subscription;

import java.util.Set;

public interface UpdateSubscriptionUseCase {
    record Command(String clientUniqueCode, String subscriptionUniqueCode,
                   String webhookUrl, String authHeaderName, String authHeaderValue,
                   Set<String> eventTypes, Boolean active) {}

    Subscription update(Command command);
}
