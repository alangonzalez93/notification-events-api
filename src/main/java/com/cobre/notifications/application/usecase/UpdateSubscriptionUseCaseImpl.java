package com.cobre.notifications.application.usecase;

import com.cobre.notifications.domain.exception.ForbiddenException;
import com.cobre.notifications.domain.exception.ResourceNotFoundException;
import com.cobre.notifications.domain.model.EventType;
import com.cobre.notifications.domain.model.Subscription;
import com.cobre.notifications.domain.port.in.UpdateSubscriptionUseCase;
import com.cobre.notifications.domain.port.out.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UpdateSubscriptionUseCaseImpl implements UpdateSubscriptionUseCase {

    private final SubscriptionRepository subscriptionRepository;

    @Override
    @Transactional
    public Subscription update(Command command) {
        Subscription existing = subscriptionRepository.findByUniqueCode(command.subscriptionUniqueCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Subscription not found: " + command.subscriptionUniqueCode()));

        if (!existing.clientUniqueCode().equals(command.clientUniqueCode())) {
            throw new ForbiddenException("Subscription does not belong to client");
        }

        Set<EventType> eventTypes = command.eventTypes().stream()
                .map(EventType::fromJson)
                .collect(Collectors.toSet());

        Subscription updated = new Subscription(
                existing.id(), existing.uniqueCode(), existing.clientUniqueCode(),
                command.webhookUrl() != null ? command.webhookUrl() : existing.webhookUrl(),
                command.authHeaderName() != null ? command.authHeaderName() : existing.authHeaderName(),
                command.authHeaderValue() != null ? command.authHeaderValue() : existing.authHeaderValue(),
                !eventTypes.isEmpty() ? eventTypes : existing.eventTypes(),
                command.active() != null ? command.active() : existing.active(),
                existing.deleted(), existing.createdDate(), existing.lastModifiedDate()
        );

        return subscriptionRepository.save(updated);
    }
}
