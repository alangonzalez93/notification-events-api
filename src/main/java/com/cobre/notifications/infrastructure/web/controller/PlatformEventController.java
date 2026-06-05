package com.cobre.notifications.infrastructure.web.controller;

import com.cobre.notifications.domain.model.EventType;
import com.cobre.notifications.domain.model.PlatformEvent;
import com.cobre.notifications.domain.port.in.IngestPlatformEventUseCase;
import com.cobre.notifications.infrastructure.web.dto.request.IngestPlatformEventRequest;
import com.cobre.notifications.infrastructure.web.dto.response.IngestResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for platform event ingestion.
 *
 * <p>In production this entry point would be replaced by a message-driven adapter — for example
 * an SQS listener (AWS) or a Pub/Sub subscriber (GCP) — that consumes events published by the
 * payments platform and calls {@link IngestPlatformEventUseCase} directly, without any HTTP
 * layer involved.  The domain use case is intentionally decoupled from the transport, so swapping
 * this controller for a queue listener requires no changes to application or domain code.
 *
 * <p>This HTTP endpoint exists solely to make the flow observable and testable during the
 * challenge evaluation.
 */
@RestController
@RequestMapping("/platform-events")
@RequiredArgsConstructor
public class PlatformEventController {

    private final IngestPlatformEventUseCase ingestUseCase;

    /**
     * Ingests a platform event and enqueues a notification for every active subscription that
     * matches the event type and client.
     *
     * <p>Returns {@code 201 Created} when a notification was created, or {@code 202 Accepted}
     * when the event was received but no matching subscription exists (silently ignored by design).
     * Duplicate {@code eventId} values are rejected with {@code 409 Conflict} to guarantee
     * exactly-once processing.
     */
    @PostMapping
    public ResponseEntity<IngestResponse> ingest(@Valid @RequestBody IngestPlatformEventRequest request) {
        var event = new PlatformEvent(
                request.eventId(),
                EventType.fromJson(request.eventType()),
                request.payload(),
                request.clientUniqueCode()
        );

        IngestPlatformEventUseCase.Result result = ingestUseCase.ingest(event);

        if (!result.created()) {
            return ResponseEntity.accepted().build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new IngestResponse(result.notificationUniqueCode(), "pending"));
    }
}
