package com.cobre.notifications.application.usecase;

import com.cobre.notifications.domain.model.NotificationEvent;
import com.cobre.notifications.domain.port.in.ProcessPendingNotificationsUseCase;
import com.cobre.notifications.domain.port.out.NotificationEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProcessPendingNotificationsUseCaseImpl implements ProcessPendingNotificationsUseCase {

    private final NotificationEventRepository notificationEventRepository;

    @Override
    @Transactional
    public List<NotificationEvent> claimBatch(int batchSize, int processingTimeoutMinutes) {
        notificationEventRepository.resetStuckProcessing(processingTimeoutMinutes);

        List<NotificationEvent> batch = notificationEventRepository.findPendingBatch(batchSize);

        if (!batch.isEmpty()) {
            List<Long> ids = batch.stream().map(NotificationEvent::id).toList();
            notificationEventRepository.markAllAsProcessing(ids);
        }

        return batch;
    }
}
