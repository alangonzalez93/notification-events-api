package com.cobre.notifications.domain.port.in;

import com.cobre.notifications.domain.model.NotificationEvent;

import java.util.List;

public interface ProcessPendingNotificationsUseCase {
    List<NotificationEvent> claimBatch(int batchSize, int processingTimeoutMinutes);
}
