package com.cobre.notifications.infrastructure.persistence;

import com.cobre.notifications.application.service.DeliverNotificationService;
import com.cobre.notifications.domain.model.*;
import com.cobre.notifications.domain.port.out.ClientRepository;
import com.cobre.notifications.domain.port.out.NotificationEventRepository;
import com.cobre.notifications.domain.port.out.SubscriptionRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class WebhookDeliveryTest extends WebIntegrationTestBase {

    static WireMockServer wireMock;

    @Autowired private ClientRepository clientRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private NotificationEventRepository notificationEventRepository;
    @Autowired private DeliverNotificationService deliverNotificationService;

    private NotificationEvent pendingNotification;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();

        Client client = clientRepository.save(
                new Client(null, null, "Delivery Test", "delivery-" + System.nanoTime() + "@test.com",
                        false, null, null));

        Subscription sub = subscriptionRepository.save(new Subscription(
                null, null, client.uniqueCode(),
                "http://localhost:" + wireMock.port() + "/webhook",
                "X-Secret", "test-secret",
                Set.of(EventType.CREDIT_TRANSFER),
                true, false, null, null
        ));

        NotificationEvent notification = notificationEventRepository.save(new NotificationEvent(
                null, null, "EVT-DELIVERY-" + System.nanoTime(),
                EventType.CREDIT_TRANSFER, "Transfer $100",
                client.uniqueCode(), sub.uniqueCode(),
                DeliveryStatus.PROCESSING,
                null, 0, null, null, null, false, null, null
        ));
        pendingNotification = notification;
    }

    @Test
    void successfulDelivery_marksNotificationAsDelivered() {
        wireMock.stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse().withStatus(200)));

        deliverNotificationService.deliver(pendingNotification);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            NotificationEvent updated = notificationEventRepository
                    .findByUniqueCode(pendingNotification.uniqueCode()).orElseThrow();
            assertThat(updated.deliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
            assertThat(updated.deliveredAt()).isNotNull();
        });

        wireMock.verify(postRequestedFor(urlEqualTo("/webhook"))
                .withHeader("X-Secret", equalTo("test-secret"))
                .withHeader("X-Idempotency-Key", equalTo(pendingNotification.uniqueCode())));
    }

    @Test
    void failedDelivery_setsRetryAndIncrementsCount() {
        wireMock.stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse().withStatus(503).withBody("Service Unavailable")));

        deliverNotificationService.deliver(pendingNotification);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            NotificationEvent updated = notificationEventRepository
                    .findByUniqueCode(pendingNotification.uniqueCode()).orElseThrow();
            assertThat(updated.deliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(updated.retryCount()).isEqualTo(1);
            assertThat(updated.nextRetryAt()).isNotNull();
            assertThat(updated.lastError()).contains("503");
        });
    }

    @Test
    void exhaustedRetries_marksAsFailed() {
        wireMock.stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse().withStatus(500)));

        NotificationEvent almostExhausted = notificationEventRepository.save(new NotificationEvent(
                pendingNotification.id(), pendingNotification.uniqueCode(),
                pendingNotification.eventId(), pendingNotification.eventType(),
                pendingNotification.payload(),
                pendingNotification.clientUniqueCode(), pendingNotification.subscriptionUniqueCode(),
                DeliveryStatus.PROCESSING,
                null, 2, null, null,
                pendingNotification.version(), pendingNotification.deleted(),
                pendingNotification.createdDate(), pendingNotification.lastModifiedDate()
        ));

        deliverNotificationService.deliver(almostExhausted);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            NotificationEvent updated = notificationEventRepository
                    .findByUniqueCode(pendingNotification.uniqueCode()).orElseThrow();
            assertThat(updated.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(updated.retryCount()).isEqualTo(3);
        });
    }
}
