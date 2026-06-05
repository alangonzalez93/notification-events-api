package com.cobre.notifications.application.usecase;

import com.cobre.notifications.domain.exception.ConflictException;
import com.cobre.notifications.domain.exception.ResourceNotFoundException;
import com.cobre.notifications.domain.model.EventType;
import com.cobre.notifications.domain.model.Subscription;
import com.cobre.notifications.domain.port.in.CreateSubscriptionUseCase;
import com.cobre.notifications.domain.port.out.ClientRepository;
import com.cobre.notifications.domain.port.out.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CreateSubscriptionUseCaseImpl implements CreateSubscriptionUseCase {

    private final SubscriptionRepository subscriptionRepository;
    private final ClientRepository clientRepository;

    @Override
    @Transactional
    public Subscription create(Command command) {
        clientRepository.findByUniqueCode(command.clientUniqueCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found: " + command.clientUniqueCode()));

        if (subscriptionRepository.existsActiveByClientUniqueCode(command.clientUniqueCode())) {
            throw new ConflictException("Client already has an active subscription");
        }

        Set<EventType> eventTypes = command.eventTypes().stream()
                .map(EventType::fromJson)
                .collect(Collectors.toSet());

        Subscription subscription = new Subscription(
                null, null, command.clientUniqueCode(),
                command.webhookUrl(), command.authHeaderName(), command.authHeaderValue(),
                eventTypes, true, false, null, null
        );

        return subscriptionRepository.save(subscription);
    }
}
