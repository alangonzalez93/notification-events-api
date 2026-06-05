package com.cobre.notifications.infrastructure.persistence.repository;

import com.cobre.notifications.domain.model.EventType;
import com.cobre.notifications.infrastructure.persistence.entity.SubscriptionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SubscriptionSpringDataRepository extends JpaRepository<SubscriptionJpaEntity, Long> {

    Optional<SubscriptionJpaEntity> findByUniqueCode(String uniqueCode);

    Optional<SubscriptionJpaEntity> findByClient_UniqueCodeAndActiveTrue(String clientUniqueCode);

    Optional<SubscriptionJpaEntity> findByClient_UniqueCodeAndActiveTrueAndEventTypesContaining(
            String clientUniqueCode, EventType eventType);

    boolean existsByClient_UniqueCodeAndActiveTrue(String clientUniqueCode);
}
