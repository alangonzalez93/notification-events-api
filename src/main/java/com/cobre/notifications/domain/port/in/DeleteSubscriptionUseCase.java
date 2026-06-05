package com.cobre.notifications.domain.port.in;

public interface DeleteSubscriptionUseCase {
    void delete(String clientUniqueCode, String subscriptionUniqueCode);
}
