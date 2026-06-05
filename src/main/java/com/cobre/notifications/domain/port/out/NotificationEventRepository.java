package com.cobre.notifications.domain.port.out;

import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.NotificationEvent;
import com.cobre.notifications.domain.model.PageResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationEventRepository {
    NotificationEvent save(NotificationEvent event);
    Optional<NotificationEvent> findByUniqueCode(String uniqueCode);
    boolean existsByEventIdAndClientUniqueCode(String eventId, String clientUniqueCode);
    List<NotificationEvent> findPendingBatch(int limit);
    void markAllAsProcessing(List<Long> ids);
    void resetStuckProcessing(int processingTimeoutMinutes);
    void failAllPendingForSubscription(String subscriptionUniqueCode);
    void updateStatus(String uniqueCode, DeliveryStatus status, OffsetDateTime deliveredAt,
                      int retryCount, OffsetDateTime nextRetryAt, String lastError);
    PageResult<NotificationEvent> findByClientUniqueCode(
            String clientUniqueCode, DeliveryStatus status,
            OffsetDateTime from, OffsetDateTime to, int page, int size);
}
