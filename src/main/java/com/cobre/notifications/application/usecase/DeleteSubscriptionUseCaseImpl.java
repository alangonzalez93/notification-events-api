package com.cobre.notifications.application.usecase;

import com.cobre.notifications.domain.exception.ForbiddenException;
import com.cobre.notifications.domain.exception.ResourceNotFoundException;
import com.cobre.notifications.domain.port.in.DeleteSubscriptionUseCase;
import com.cobre.notifications.domain.port.out.NotificationEventRepository;
import com.cobre.notifications.domain.port.out.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteSubscriptionUseCaseImpl implements DeleteSubscriptionUseCase {

    private final SubscriptionRepository subscriptionRepository;
    private final NotificationEventRepository notificationEventRepository;

    @Override
    @Transactional
    public void delete(String clientUniqueCode, String subscriptionUniqueCode) {
        var subscription = subscriptionRepository.findByUniqueCode(subscriptionUniqueCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription not found: " + subscriptionUniqueCode));

        if (!subscription.clientUniqueCode().equals(clientUniqueCode)) {
            throw new ForbiddenException("Subscription does not belong to client");
        }

        // Fail pending notifications before soft-deleting so the JPQL query can still
        // join through the subscription row (deleted = false at this point in the TX).
        notificationEventRepository.failAllPendingForSubscription(subscriptionUniqueCode);
        subscriptionRepository.softDelete(subscriptionUniqueCode);
    }
}
