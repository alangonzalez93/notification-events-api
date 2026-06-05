package com.cobre.notifications.application.service;

import com.cobre.notifications.domain.exception.CircuitOpenException;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.NotificationEvent;
import com.cobre.notifications.domain.model.Subscription;
import com.cobre.notifications.domain.port.out.WebhookPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliverNotificationService {

    private final DeliveryTransactionService txService;
    private final WebhookPort webhookPort;
    private final MeterRegistry meterRegistry;

    @Async("webhookDeliveryExecutor")
    public void deliver(NotificationEvent event) {
        MDC.put("notificationId", event.uniqueCode());
        MDC.put("clientId", event.clientUniqueCode());
        MDC.put("eventType", event.eventType() != null ? event.eventType().toJson() : "unknown");

        try {
            Subscription subscription;
            try {
                subscription = txService.fetchSubscription(event);
            } catch (Exception e) {
                log.error("Subscription no longer active for notification {}", event.uniqueCode());
                txService.markRetryOrFailed(event, "Subscription no longer active");
                return;
            }

            Instant start = Instant.now();
            try {
                WebhookPort.WebhookResult result = webhookPort.post(
                        subscription.webhookUrl(),
                        subscription.authHeaderName(),
                        subscription.authHeaderValue(),
                        event.uniqueCode(),
                        event.clientUniqueCode(),
                        event.payload()
                );

                recordWebhookDuration(event, Duration.between(start, Instant.now()));

                if (result.success()) {
                    log.info("Delivered notification {} to client {}", event.uniqueCode(), event.clientUniqueCode());
                    txService.markDelivered(event);
                    meterRegistry.counter("notifications.delivered",
                            "event_type", event.eventType().toJson(),
                            "client_unique_code", event.clientUniqueCode()).increment();
                } else {
                    handleFailure(event, start, "HTTP " + result.httpStatus() + ": " + result.errorMessage());
                }

            } catch (CircuitOpenException e) {
                log.warn("Circuit open for client {}, notification {}", event.clientUniqueCode(), event.uniqueCode());
                txService.markCircuitOpen(event, e.waitDurationSeconds());

            } catch (Exception e) {
                handleFailure(event, start, e.getMessage());
            }

        } finally {
            MDC.clear();
        }
    }

    private void handleFailure(NotificationEvent event, Instant start, String error) {
        recordWebhookDuration(event, Duration.between(start, Instant.now()));
        log.warn("Delivery failed for notification {}: {}", event.uniqueCode(), error);
        DeliveryStatus result = txService.markRetryOrFailed(event, error);
        if (result == DeliveryStatus.FAILED) {
            log.error("Notification {} permanently failed after exhausting retries", event.uniqueCode());
            meterRegistry.counter("notifications.failed",
                    "event_type", event.eventType().toJson(),
                    "client_unique_code", event.clientUniqueCode()).increment();
        } else {
            meterRegistry.counter("notifications.retried",
                    "event_type", event.eventType().toJson(),
                    "client_unique_code", event.clientUniqueCode()).increment();
        }
    }

    private void recordWebhookDuration(NotificationEvent event, Duration duration) {
        Timer.builder("notifications.webhook.duration")
                .tag("event_type", event.eventType().toJson())
                .tag("client_unique_code", event.clientUniqueCode())
                .register(meterRegistry)
                .record(duration);
    }
}
