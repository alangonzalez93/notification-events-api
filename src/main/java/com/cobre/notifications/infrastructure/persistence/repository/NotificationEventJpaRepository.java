package com.cobre.notifications.infrastructure.persistence.repository;

import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.NotificationEvent;
import com.cobre.notifications.domain.model.PageResult;
import com.cobre.notifications.domain.port.out.NotificationEventRepository;
import com.cobre.notifications.infrastructure.persistence.entity.ClientJpaEntity;
import com.cobre.notifications.infrastructure.persistence.entity.NotificationEventJpaEntity;
import com.cobre.notifications.infrastructure.persistence.entity.SubscriptionJpaEntity;
import com.cobre.notifications.infrastructure.persistence.mapper.NotificationEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationEventJpaRepository implements NotificationEventRepository {

    private final NotificationEventSpringDataRepository springData;
    private final ClientSpringDataRepository clientSpringData;
    private final SubscriptionSpringDataRepository subscriptionSpringData;
    private final NotificationEventMapper mapper;

    @Override
    public NotificationEvent save(NotificationEvent event) {
        if (event.id() != null) {
            return update(event);
        }
        return insert(event);
    }

    private NotificationEvent insert(NotificationEvent event) {
        NotificationEventJpaEntity entity = mapper.toEntity(event);

        ClientJpaEntity client = clientSpringData.findByUniqueCode(event.clientUniqueCode())
                .orElseThrow(() -> new com.cobre.notifications.domain.exception.ResourceNotFoundException(
                        "Client not found: " + event.clientUniqueCode()));
        entity.setClient(client);

        SubscriptionJpaEntity subscription = subscriptionSpringData
                .findByUniqueCode(event.subscriptionUniqueCode())
                .orElseThrow(() -> new com.cobre.notifications.domain.exception.ResourceNotFoundException(
                        "Subscription not found: " + event.subscriptionUniqueCode()));
        entity.setSubscription(subscription);

        return mapper.toDomain(springData.save(entity));
    }

    private NotificationEvent update(NotificationEvent event) {
        // Load via findByUniqueCode (JOIN FETCH client + subscription) so associations
        // are plain initialized Java objects, not LAZY proxies.
        NotificationEventJpaEntity entity = springData.findByUniqueCode(event.uniqueCode())
                .orElseThrow(() -> new com.cobre.notifications.domain.exception.ResourceNotFoundException(
                        "NotificationEvent not found: " + event.uniqueCode()));

        entity.setDeliveryStatus(event.deliveryStatus());
        entity.setDeliveredAt(event.deliveredAt());
        entity.setRetryCount(event.retryCount() != null ? event.retryCount() : 0);
        entity.setNextRetryAt(event.nextRetryAt());
        entity.setLastError(event.lastError());

        springData.save(entity);
        // Map the entity we loaded — NOT the return value of save(), which is a merged
        // instance with fresh LAZY proxies that would fail outside a session.
        return mapper.toDomain(entity);
    }

    @Override
    public Optional<NotificationEvent> findByUniqueCode(String uniqueCode) {
        return springData.findByUniqueCode(uniqueCode).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEventIdAndClientUniqueCode(String eventId, String clientUniqueCode) {
        return springData.existsByEventIdAndClient_UniqueCode(eventId, clientUniqueCode);
    }

    @Override
    public List<NotificationEvent> findPendingBatch(int limit) {
        return springData.findPendingBatch(
                OffsetDateTime.now(ZoneOffset.UTC),
                PageRequest.of(0, limit, Sort.by("createdDate").ascending())
        ).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void markAllAsProcessing(List<Long> ids) {
        springData.markAllAsProcessing(ids);
    }

    @Override
    public void resetStuckProcessing(int processingTimeoutMinutes) {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(processingTimeoutMinutes);
        springData.resetStuckProcessing(cutoff);
    }

    @Override
    public void updateStatus(String uniqueCode, DeliveryStatus status, OffsetDateTime deliveredAt,
                              int retryCount, OffsetDateTime nextRetryAt, String lastError) {
        springData.updateStatus(uniqueCode, status, deliveredAt, retryCount, nextRetryAt, lastError);
    }

    @Override
    public void failAllPendingForSubscription(String subscriptionUniqueCode) {
        springData.failAllPendingForSubscription(subscriptionUniqueCode);
    }

    @Override
    public PageResult<NotificationEvent> findByClientUniqueCode(
            String clientUniqueCode, DeliveryStatus status,
            OffsetDateTime from, OffsetDateTime to, int page, int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
        Page<NotificationEventJpaEntity> result;

        OffsetDateTime effectiveFrom = from != null ? from : OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime effectiveTo   = to   != null ? to   : OffsetDateTime.now(ZoneOffset.UTC).plusYears(100);

        if (status != null) {
            result = springData.findByClientCodeAndStatusAndDateRange(
                    clientUniqueCode, status, effectiveFrom, effectiveTo, pageable);
        } else {
            result = springData.findByClientCodeAndDateRange(
                    clientUniqueCode, effectiveFrom, effectiveTo, pageable);
        }

        return new PageResult<>(
                result.getContent().stream().map(mapper::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
