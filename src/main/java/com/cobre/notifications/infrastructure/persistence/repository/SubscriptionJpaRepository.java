package com.cobre.notifications.infrastructure.persistence.repository;

import com.cobre.notifications.domain.exception.ResourceNotFoundException;
import com.cobre.notifications.domain.model.EventType;
import com.cobre.notifications.domain.model.Subscription;
import com.cobre.notifications.domain.port.out.SubscriptionRepository;
import com.cobre.notifications.infrastructure.persistence.entity.ClientJpaEntity;
import com.cobre.notifications.infrastructure.persistence.entity.SubscriptionJpaEntity;
import com.cobre.notifications.infrastructure.persistence.mapper.SubscriptionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SubscriptionJpaRepository implements SubscriptionRepository {

    private final SubscriptionSpringDataRepository springData;
    private final ClientSpringDataRepository clientSpringData;
    private final SubscriptionMapper mapper;

    @Override
    public Subscription save(Subscription subscription) {
        ClientJpaEntity client = clientSpringData
                .findByUniqueCode(subscription.clientUniqueCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found: " + subscription.clientUniqueCode()));

        SubscriptionJpaEntity entity;
        if (subscription.id() != null) {
            entity = springData.findById(subscription.id())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Subscription not found: " + subscription.uniqueCode()));
        } else {
            entity = mapper.toEntity(subscription);
        }

        entity.setClient(client);
        entity.setWebhookUrl(subscription.webhookUrl());
        entity.setAuthHeaderName(subscription.authHeaderName());
        entity.setAuthHeaderValue(subscription.authHeaderValue());
        entity.setEventTypes(subscription.eventTypes());
        entity.setActive(subscription.active());

        return mapper.toDomain(springData.save(entity));
    }

    @Override
    public Optional<Subscription> findByUniqueCode(String uniqueCode) {
        return springData.findByUniqueCode(uniqueCode).map(mapper::toDomain);
    }

    @Override
    public Optional<Subscription> findActiveByClientUniqueCode(String clientUniqueCode) {
        return springData.findByClient_UniqueCodeAndActiveTrue(clientUniqueCode)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Subscription> findActiveByClientUniqueCodeAndEventType(
            String clientUniqueCode, EventType eventType) {
        return springData.findByClient_UniqueCodeAndActiveTrueAndEventTypesContaining(
                clientUniqueCode, eventType).map(mapper::toDomain);
    }

    @Override
    public boolean existsActiveByClientUniqueCode(String clientUniqueCode) {
        return springData.existsByClient_UniqueCodeAndActiveTrue(clientUniqueCode);
    }

    @Override
    public void softDelete(String uniqueCode) {
        springData.findByUniqueCode(uniqueCode).ifPresent(entity -> {
            entity.setDeleted(true);
            springData.save(entity);
        });
    }
}
