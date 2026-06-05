package com.cobre.notifications.infrastructure.persistence;

import com.cobre.notifications.domain.model.Client;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.NotificationEvent;
import com.cobre.notifications.domain.port.out.ClientRepository;
import com.cobre.notifications.domain.port.out.NotificationEventRepository;
import com.cobre.notifications.infrastructure.web.dto.request.CreateSubscriptionRequest;
import com.cobre.notifications.infrastructure.web.dto.request.IngestPlatformEventRequest;
import com.cobre.notifications.infrastructure.web.dto.response.SubscriptionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionControllerTest extends WebIntegrationTestBase {

    @Autowired private ClientRepository clientRepository;
    @Autowired private NotificationEventRepository notificationEventRepository;

    private String clientCode;

    @BeforeEach
    void setUp() {
        Client client = clientRepository.save(
                new Client(null, null, "Test Client", "sub-test-" + System.nanoTime() + "@test.com",
                        false, null, null));
        clientCode = client.uniqueCode();
    }

    @Test
    void createSubscription_returns201() {
        var request = new CreateSubscriptionRequest(
                "https://webhook.example.com/hook",
                "X-Secret", "secret-value",
                Set.of("credit_transfer", "debit_purchase")
        );

        ResponseEntity<SubscriptionResponse> response = restTemplate.postForEntity(
                "/clients/" + clientCode + "/subscriptions", request, SubscriptionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().uniqueCode()).isNotNull();
        assertThat(response.getBody().webhookUrl()).isEqualTo("https://webhook.example.com/hook");
        assertThat(response.getBody().active()).isTrue();
    }

    @Test
    void createSubscription_duplicate_returns409() {
        var request = new CreateSubscriptionRequest(
                "https://webhook.example.com/hook",
                "X-Secret", "secret-value",
                Set.of("credit_transfer")
        );

        restTemplate.postForEntity("/clients/" + clientCode + "/subscriptions", request, SubscriptionResponse.class);

        ResponseEntity<String> second = restTemplate.postForEntity(
                "/clients/" + clientCode + "/subscriptions", request, String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void getSubscription_returnsActiveSubscription() {
        var request = new CreateSubscriptionRequest(
                "https://webhook.example.com/hook",
                "X-Secret", "val",
                Set.of("credit_transfer")
        );
        restTemplate.postForEntity("/clients/" + clientCode + "/subscriptions", request, SubscriptionResponse.class);

        ResponseEntity<SubscriptionResponse> response = restTemplate.getForEntity(
                "/clients/" + clientCode + "/subscriptions", SubscriptionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().webhookUrl()).isEqualTo("https://webhook.example.com/hook");
    }

    @Test
    void getSubscription_unknownClient_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/clients/nonexistent-code/subscriptions", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteSubscription_returns204_andNotFoundAfter() {
        var request = new CreateSubscriptionRequest(
                "https://webhook.example.com/hook",
                "X-Secret", "val",
                Set.of("credit_transfer")
        );
        ResponseEntity<SubscriptionResponse> created = restTemplate.postForEntity(
                "/clients/" + clientCode + "/subscriptions", request, SubscriptionResponse.class);
        String subCode = created.getBody().uniqueCode();

        restTemplate.delete("/clients/" + clientCode + "/subscriptions/" + subCode);

        ResponseEntity<String> getAfter = restTemplate.getForEntity(
                "/clients/" + clientCode + "/subscriptions", String.class);
        assertThat(getAfter.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteSubscription_failsPendingNotifications() {
        var subRequest = new CreateSubscriptionRequest(
                "https://webhook.example.com/hook", "X-Secret", "val",
                Set.of("credit_transfer")
        );
        ResponseEntity<SubscriptionResponse> created = restTemplate.postForEntity(
                "/clients/" + clientCode + "/subscriptions", subRequest, SubscriptionResponse.class);
        String subCode = created.getBody().uniqueCode();

        var ingestRequest = new IngestPlatformEventRequest(
                "EVT-DEL-" + System.nanoTime(), "credit_transfer", "Transfer $100", clientCode);
        restTemplate.postForEntity("/platform-events", ingestRequest, String.class);

        restTemplate.delete("/clients/" + clientCode + "/subscriptions/" + subCode);

        NotificationEvent notification = notificationEventRepository
                .findByClientUniqueCode(clientCode, null, null, null, 0, 10)
                .content().get(0);

        assertThat(notification.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(notification.lastError()).isEqualTo("Subscription deleted");
    }
}
