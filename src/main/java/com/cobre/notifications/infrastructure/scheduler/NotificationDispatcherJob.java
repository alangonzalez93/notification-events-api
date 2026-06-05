package com.cobre.notifications.infrastructure.scheduler;

import com.cobre.notifications.application.service.DeliverNotificationService;
import com.cobre.notifications.domain.model.NotificationEvent;
import com.cobre.notifications.domain.port.in.ProcessPendingNotificationsUseCase;
import com.cobre.notifications.infrastructure.config.DispatcherProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcherJob {

    private final ProcessPendingNotificationsUseCase processPendingUseCase;
    private final DeliverNotificationService deliverNotificationService;
    private final DispatcherProperties dispatcherProperties;

    @Scheduled(fixedDelayString = "${notifications.dispatcher.delay-ms:5000}")
    @SchedulerLock(name = "notificationDispatcher", lockAtMostFor = "PT5M")
    public void dispatch() {
        List<NotificationEvent> batch = processPendingUseCase.claimBatch(
                dispatcherProperties.batchSize(),
                dispatcherProperties.processingTimeoutMinutes()
        );

        if (batch.isEmpty()) {
            return;
        }

        log.info("Dispatching batch of {} notifications", batch.size());

        // @Async dispatch happens AFTER claimBatch() TX has committed
        batch.forEach(deliverNotificationService::deliver);
    }
}
