package com.cobre.notifications.application;

import com.cobre.notifications.application.service.DeliveryTransactionService;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.EventType;
import com.cobre.notifications.domain.model.NotificationEvent;
import com.cobre.notifications.domain.port.out.NotificationEventRepository;
import com.cobre.notifications.domain.port.out.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RetryBackoffTest {

    @Mock private NotificationEventRepository notificationEventRepository;
    @Mock private SubscriptionRepository subscriptionRepository;

    private DeliveryTransactionService txService;

    @BeforeEach
    void setUp() {
        RetryProperties props = new RetryProperties(4, 5L, 60L, 0.2);
        txService = new DeliveryTransactionService(notificationEventRepository, subscriptionRepository, props);
    }

    @Test
    void attempt1_delayIsBetween5and6seconds() {
        assertNextRetryDelay(eventWithRetryCount(0), 5, 6);
    }

    @Test
    void attempt2_delayIsBetween10and12seconds() {
        assertNextRetryDelay(eventWithRetryCount(1), 10, 12);
    }

    @Test
    void attempt3_delayIsBetween20and24seconds() {
        assertNextRetryDelay(eventWithRetryCount(2), 20, 24);
    }

    @Test
    void highAttemptCount_delayIsCappedAt60seconds() {
        var highMaxProps = new RetryProperties(20, 5L, 60L, 0.2);
        var highMaxService = new DeliveryTransactionService(
                notificationEventRepository, subscriptionRepository, highMaxProps);

        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);
        highMaxService.markRetryOrFailed(eventWithRetryCount(10), "simulated error");

        ArgumentCaptor<OffsetDateTime> nextRetryAtCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(notificationEventRepository).updateStatus(
                eq("NOTIF-001"), eq(DeliveryStatus.PENDING), isNull(),
                eq(11), nextRetryAtCaptor.capture(), eq("simulated error")
        );

        long seconds = Duration.between(before, nextRetryAtCaptor.getValue()).getSeconds();
        assertThat(seconds).isBetween(60L, 72L);
    }

    @Test
    void exhaustedRetries_returnsFailedStatus() {
        DeliveryStatus result = txService.markRetryOrFailed(eventWithRetryCount(3), "timeout");
        assertThat(result).isEqualTo(DeliveryStatus.FAILED);
        verify(notificationEventRepository).updateStatus(
                eq("NOTIF-001"), eq(DeliveryStatus.FAILED), isNull(), eq(4), isNull(), eq("timeout")
        );
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void assertNextRetryDelay(NotificationEvent event, long minSeconds, long maxSeconds) {
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

        txService.markRetryOrFailed(event, "simulated error");

        ArgumentCaptor<OffsetDateTime> nextRetryAtCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(notificationEventRepository).updateStatus(
                eq(event.uniqueCode()), eq(DeliveryStatus.PENDING), isNull(),
                eq(event.retryCount() + 1), nextRetryAtCaptor.capture(), eq("simulated error")
        );

        long seconds = Duration.between(before, nextRetryAtCaptor.getValue()).getSeconds();
        assertThat(seconds).isBetween(minSeconds, maxSeconds);
    }

    private NotificationEvent eventWithRetryCount(int retryCount) {
        return new NotificationEvent(
                1L, "NOTIF-001", "EVT-001", EventType.CREDIT_TRANSFER, "payload",
                "CLIENT001", "SUB-001",
                DeliveryStatus.PROCESSING,
                null, retryCount, null, null,
                0L, false, null, null
        );
    }
}
