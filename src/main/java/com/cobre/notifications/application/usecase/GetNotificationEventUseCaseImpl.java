package com.cobre.notifications.application.usecase;

import com.cobre.notifications.domain.exception.ResourceNotFoundException;
import com.cobre.notifications.domain.model.NotificationEvent;
import com.cobre.notifications.domain.port.in.GetNotificationEventUseCase;
import com.cobre.notifications.domain.port.out.NotificationEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetNotificationEventUseCaseImpl implements GetNotificationEventUseCase {

    private final NotificationEventRepository notificationEventRepository;

    @Override
    @Transactional(readOnly = true)
    public NotificationEvent getByUniqueCode(String uniqueCode) {
        return notificationEventRepository.findByUniqueCode(uniqueCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification event not found: " + uniqueCode));
    }
}
