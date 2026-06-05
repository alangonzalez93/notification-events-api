package com.cobre.notifications.domain.port.in;

import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.NotificationEvent;
import com.cobre.notifications.domain.model.PageResult;

import java.time.OffsetDateTime;

public interface GetNotificationEventsUseCase {
    record Query(String clientUniqueCode, DeliveryStatus deliveryStatus,
                 OffsetDateTime from, OffsetDateTime to, int page, int size) {}

    PageResult<NotificationEvent> list(Query query);
}
