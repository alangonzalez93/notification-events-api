package com.cobre.notifications.application.usecase;

import com.cobre.notifications.domain.exception.ConflictException;
import com.cobre.notifications.domain.exception.ResourceNotFoundException;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.NotificationEvent;
import com.cobre.notifications.domain.model.PlatformEvent;
import com.cobre.notifications.domain.port.in.IngestPlatformEventUseCase;
import com.cobre.notifications.domain.port.out.ClientRepository;
import com.cobre.notifications.domain.port.out.NotificationEventRepository;
import com.cobre.notifications.domain.port.out.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IngestPlatformEventUseCaseImpl implements IngestPlatformEventUseCase {

    private final SubscriptionRepository subscriptionRepository;
    private final NotificationEventRepository notificationEventRepository;
    private final ClientRepository clientRepository;

    @Override
    @Transactional
    public Result ingest(PlatformEvent event) {
        clientRepository.findByUniqueCode(event.clientUniqueCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found: " + event.clientUniqueCode()));

        if (notificationEventRepository.existsByEventIdAndClientUniqueCode(
                event.eventId(), event.clientUniqueCode())) {
            throw new ConflictException("Event already ingested: " + event.eventId());
        }

        var subscription = subscriptionRepository.findActiveByClientUniqueCodeAndEventType(
                event.clientUniqueCode(), event.eventType());

        if (subscription.isEmpty()) {
            return new Result(false, null);
        }

        NotificationEvent notification = new NotificationEvent(
                null, null,
                event.eventId(), event.eventType(), event.payload(),
                event.clientUniqueCode(), subscription.get().uniqueCode(),
                DeliveryStatus.PENDING,
                null, 0, null, null, null, false, null, null
        );

        NotificationEvent saved = notificationEventRepository.save(notification);
        return new Result(true, saved.uniqueCode());
    }
}
