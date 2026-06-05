package com.cobre.notifications.domain.port.in;

public interface ReplayNotificationEventUseCase {
    void replay(String uniqueCode);
}
