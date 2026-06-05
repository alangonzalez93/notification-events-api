package com.cobre.notifications.application.usecase;

import com.cobre.notifications.domain.exception.ResourceNotFoundException;
import com.cobre.notifications.domain.model.Subscription;
import com.cobre.notifications.domain.port.in.GetSubscriptionUseCase;
import com.cobre.notifications.domain.port.out.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetSubscriptionUseCaseImpl implements GetSubscriptionUseCase {

    private final SubscriptionRepository subscriptionRepository;

    @Override
    @Transactional(readOnly = true)
    public Subscription getByClientUniqueCode(String clientUniqueCode) {
        return subscriptionRepository.findActiveByClientUniqueCode(clientUniqueCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active subscription for client: " + clientUniqueCode));
    }
}
