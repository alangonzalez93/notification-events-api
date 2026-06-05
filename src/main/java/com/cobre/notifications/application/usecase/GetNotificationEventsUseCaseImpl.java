package com.cobre.notifications.application.usecase;

import com.cobre.notifications.domain.model.NotificationEvent;
import com.cobre.notifications.domain.model.PageResult;
import com.cobre.notifications.domain.port.in.GetNotificationEventsUseCase;
import com.cobre.notifications.domain.port.out.NotificationEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetNotificationEventsUseCaseImpl implements GetNotificationEventsUseCase {

    private final NotificationEventRepository notificationEventRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResult<NotificationEvent> list(Query query) {
        return notificationEventRepository.findByClientUniqueCode(
                query.clientUniqueCode(),
                query.deliveryStatus(),
                query.from(),
                query.to(),
                query.page(),
                query.size()
        );
    }
}
