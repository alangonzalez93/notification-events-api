package com.cobre.notifications.domain.port.in;

import com.cobre.notifications.domain.model.Subscription;

public interface GetSubscriptionUseCase {
    Subscription getByClientUniqueCode(String clientUniqueCode);
}
