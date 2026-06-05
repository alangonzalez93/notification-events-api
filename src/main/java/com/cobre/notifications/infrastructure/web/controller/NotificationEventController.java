package com.cobre.notifications.infrastructure.web.controller;

import com.cobre.notifications.domain.exception.ForbiddenException;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.NotificationEvent;
import com.cobre.notifications.domain.port.in.GetNotificationEventUseCase;
import com.cobre.notifications.domain.port.in.GetNotificationEventsUseCase;
import com.cobre.notifications.domain.port.in.ReplayNotificationEventUseCase;
import com.cobre.notifications.infrastructure.web.dto.response.NotificationEventDetailResponse;
import com.cobre.notifications.infrastructure.web.dto.response.NotificationEventSummaryResponse;
import com.cobre.notifications.infrastructure.web.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/notification_events")
@RequiredArgsConstructor
public class NotificationEventController {

    private final GetNotificationEventsUseCase listUseCase;
    private final GetNotificationEventUseCase getUseCase;
    private final ReplayNotificationEventUseCase replayUseCase;

    @GetMapping
    public ResponseEntity<PageResponse<NotificationEventSummaryResponse>> list(
            @RequestParam String clientUniqueCode,
            @RequestParam(required = false) String deliveryStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (size > 100) {
            throw new IllegalArgumentException("size must not exceed 100");
        }

        DeliveryStatus status = deliveryStatus != null
                ? DeliveryStatus.fromJson(deliveryStatus) : null;

        var result = listUseCase.list(new GetNotificationEventsUseCase.Query(
                clientUniqueCode, status, from, to, page, size));

        return ResponseEntity.ok(PageResponse.from(result, NotificationEventSummaryResponse::from));
    }

    @GetMapping("/{uniqueCode}")
    public ResponseEntity<NotificationEventDetailResponse> getOne(
            @PathVariable String uniqueCode,
            @RequestParam String clientUniqueCode) {

        NotificationEvent event = getUseCase.getByUniqueCode(uniqueCode);

        if (!event.clientUniqueCode().equals(clientUniqueCode)) {
            throw new ForbiddenException("Access denied");
        }

        return ResponseEntity.ok(NotificationEventDetailResponse.from(event));
    }

    @PostMapping("/{uniqueCode}/replay")
    public ResponseEntity<Void> replay(
            @PathVariable String uniqueCode,
            @RequestParam String clientUniqueCode) {

        NotificationEvent event = getUseCase.getByUniqueCode(uniqueCode);

        if (!event.clientUniqueCode().equals(clientUniqueCode)) {
            throw new ForbiddenException("Access denied");
        }

        replayUseCase.replay(uniqueCode);
        return ResponseEntity.accepted().build();
    }
}
