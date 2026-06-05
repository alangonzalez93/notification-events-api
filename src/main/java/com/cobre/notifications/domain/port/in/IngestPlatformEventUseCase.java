package com.cobre.notifications.domain.port.in;

import com.cobre.notifications.domain.model.PlatformEvent;

public interface IngestPlatformEventUseCase {
    record Result(boolean created, String notificationUniqueCode) {}

    Result ingest(PlatformEvent event);
}
