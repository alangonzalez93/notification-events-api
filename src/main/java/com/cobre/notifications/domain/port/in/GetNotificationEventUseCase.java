package com.cobre.notifications.domain.port.in;

import com.cobre.notifications.domain.model.NotificationEvent;

public interface GetNotificationEventUseCase {
    NotificationEvent getByUniqueCode(String uniqueCode);
}
