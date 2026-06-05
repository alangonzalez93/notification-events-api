package com.cobre.notifications.infrastructure.persistence;

import com.cobre.notifications.domain.model.Client;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.NotificationEvent;
import com.cobre.notifications.domain.port.out.ClientRepository;
import com.cobre.notifications.domain.port.out.NotificationEventRepository;
import com.cobre.notifications.domain.port.out.SubscriptionRepository;
import com.cobre.notifications.domain.model.Subscription;
import com.cobre.notifications.infrastructure.web.dto.response.NotificationEventDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayControllerTest extends WebIntegrationTestBase {

    @Autowired private ClientRepository clientRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private NotificationEventRepository notificationEventRepository;

    private String clientCode;
    private String failedNotificationCode;

    @BeforeEach
    void setUp() {
        Client client = clientRepository.save(
                new Client(null, null, "Replay Test", "replay-" + System.nanoTime() + "@test.com",
                        false, null, null));
        clientCode = client.uniqueCode();

        Subscription sub = subscriptionRepository.save(new Subscription(
                null, null, clientCode,
                "https://webhook.example.com", "X-Secret", "val",
                Set.of(com.cobre.notifications.domain.model.EventType.CREDIT_TRANSFER),
                true, false, null, null
        ));

        NotificationEvent failed = notificationEventRepository.save(new NotificationEvent(
                null, null, "EVT-FAILED-1",
                com.cobre.notifications.domain.model.EventType.CREDIT_TRANSFER,
                "Transfer $100",
                clientCode, sub.uniqueCode(),
                DeliveryStatus.FAILED,
                null, 3, null, "HTTP 503", null, false, null, null
        ));
        failedNotificationCode = failed.uniqueCode();
    }

    @Test
    void replay_failedNotification_resetsToPending() {
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/notification_events/" + failedNotificationCode + "/replay?clientUniqueCode=" + clientCode,
                null, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ResponseEntity<NotificationEventDetailResponse> detail = restTemplate.getForEntity(
                "/notification_events/" + failedNotificationCode + "?clientUniqueCode=" + clientCode,
                NotificationEventDetailResponse.class);
        assertThat(detail.getBody().deliveryStatus()).isEqualTo("pending");
        assertThat(detail.getBody().retryCount()).isEqualTo(0);
    }

    @Test
    void replay_alreadyPending_returns409() {
        restTemplate.postForEntity(
                "/notification_events/" + failedNotificationCode + "/replay?clientUniqueCode=" + clientCode,
                null, Void.class);

        ResponseEntity<String> second = restTemplate.postForEntity(
                "/notification_events/" + failedNotificationCode + "/replay?clientUniqueCode=" + clientCode,
                null, String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
