package com.cobre.notifications.domain.port.out;

import com.cobre.notifications.domain.model.EventType;
import com.cobre.notifications.domain.model.Subscription;

import java.util.Optional;

public interface SubscriptionRepository {
    Subscription save(Subscription subscription);
    Optional<Subscription> findByUniqueCode(String uniqueCode);
    Optional<Subscription> findActiveByClientUniqueCode(String clientUniqueCode);
    Optional<Subscription> findActiveByClientUniqueCodeAndEventType(String clientUniqueCode, EventType eventType);
    boolean existsActiveByClientUniqueCode(String clientUniqueCode);
    void softDelete(String uniqueCode);
}
