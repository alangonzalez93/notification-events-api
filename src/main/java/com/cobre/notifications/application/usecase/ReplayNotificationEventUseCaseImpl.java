package com.cobre.notifications.application.usecase;

import com.cobre.notifications.domain.exception.ConflictException;
import com.cobre.notifications.domain.exception.ResourceNotFoundException;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.NotificationEvent;
import com.cobre.notifications.domain.port.in.ReplayNotificationEventUseCase;
import com.cobre.notifications.domain.port.out.NotificationEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReplayNotificationEventUseCaseImpl implements ReplayNotificationEventUseCase {

    private final NotificationEventRepository notificationEventRepository;

    @Override
    @Transactional
    public void replay(String uniqueCode) {
        NotificationEvent event = notificationEventRepository.findByUniqueCode(uniqueCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification event not found: " + uniqueCode));

        if (event.deliveryStatus() == DeliveryStatus.PENDING ||
                event.deliveryStatus() == DeliveryStatus.PROCESSING) {
            throw new ConflictException("Notification is already queued for delivery");
        }

        NotificationEvent reset = new NotificationEvent(
                event.id(), event.uniqueCode(),
                event.eventId(), event.eventType(), event.payload(),
                event.clientUniqueCode(), event.subscriptionUniqueCode(),
                DeliveryStatus.PENDING,
                null, 0, null, null,
                event.version(),
                event.deleted(), event.createdDate(), event.lastModifiedDate()
        );

        try {
            notificationEventRepository.save(reset);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("Concurrent replay detected, please retry");
        }
    }
}
