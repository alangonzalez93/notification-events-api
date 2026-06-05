package com.cobre.notifications.application.service;

import com.cobre.notifications.domain.exception.ResourceNotFoundException;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.NotificationEvent;
import com.cobre.notifications.domain.model.Subscription;
import com.cobre.notifications.domain.port.out.NotificationEventRepository;
import com.cobre.notifications.domain.port.out.SubscriptionRepository;
import com.cobre.notifications.application.RetryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class DeliveryTransactionService {

    private final NotificationEventRepository notificationEventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final RetryProperties retryProperties;

    @Transactional(readOnly = true)
    public Subscription fetchSubscription(NotificationEvent event) {
        return subscriptionRepository.findByUniqueCode(event.subscriptionUniqueCode())
                .orElseThrow(() -> new ResourceNotFoundException("Subscription no longer active"));
    }

    @Transactional
    public void markDelivered(NotificationEvent event) {
        notificationEventRepository.updateStatus(
                event.uniqueCode(),
                DeliveryStatus.DELIVERED,
                OffsetDateTime.now(ZoneOffset.UTC),
                event.retryCount() != null ? event.retryCount() : 0,
                null,
                null
        );
    }

    @Transactional
    public DeliveryStatus markRetryOrFailed(NotificationEvent event, String errorMessage) {
        int newRetryCount = event.retryCount() + 1;
        DeliveryStatus newStatus;
        OffsetDateTime nextRetryAt = null;

        if (newRetryCount < retryProperties.maxAttempts()) {
            newStatus = DeliveryStatus.PENDING;
            long delaySeconds = (long) Math.min(
                    retryProperties.baseDelaySeconds() * Math.pow(2, event.retryCount()),
                    retryProperties.maxDelaySeconds());
            long jitter = (long) (ThreadLocalRandom.current().nextDouble() * delaySeconds * retryProperties.jitterFactor());
            nextRetryAt = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(delaySeconds + jitter);
        } else {
            newStatus = DeliveryStatus.FAILED;
        }

        notificationEventRepository.updateStatus(
                event.uniqueCode(),
                newStatus,
                null,
                newRetryCount,
                nextRetryAt,
                errorMessage
        );
        return newStatus;
    }

    @Transactional
    public void markCircuitOpen(NotificationEvent event, long waitDurationSeconds) {
        OffsetDateTime nextRetryAt = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(waitDurationSeconds);
        notificationEventRepository.updateStatus(
                event.uniqueCode(),
                DeliveryStatus.PENDING,
                null,
                event.retryCount() != null ? event.retryCount() : 0,
                nextRetryAt,
                "Circuit open for client " + event.clientUniqueCode()
        );
    }
}
