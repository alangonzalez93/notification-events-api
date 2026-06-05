package com.cobre.notifications.infrastructure.persistence;

import com.cobre.notifications.domain.model.Client;
import com.cobre.notifications.domain.port.out.ClientRepository;
import com.cobre.notifications.infrastructure.web.dto.request.CreateSubscriptionRequest;
import com.cobre.notifications.infrastructure.web.dto.request.IngestPlatformEventRequest;
import com.cobre.notifications.infrastructure.web.dto.response.IngestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformEventControllerTest extends WebIntegrationTestBase {

    @Autowired
    private ClientRepository clientRepository;

    private String clientCode;

    @BeforeEach
    void setUp() {
        Client client = clientRepository.save(
                new Client(null, null, "Ingest Test", "ingest-" + System.nanoTime() + "@test.com",
                        false, null, null));
        clientCode = client.uniqueCode();

        var subRequest = new CreateSubscriptionRequest(
                "https://webhook.example.com/hook", "X-Secret", "val",
                Set.of("credit_transfer", "debit_purchase")
        );
        restTemplate.postForEntity("/clients/" + clientCode + "/subscriptions", subRequest, String.class);
    }

    @Test
    void ingest_withMatchingSubscription_returns201() {
        var request = new IngestPlatformEventRequest("EVT-TEST-1", "credit_transfer",
                "Transfer of $100", clientCode);

        ResponseEntity<IngestResponse> response = restTemplate.postForEntity(
                "/platform-events", request, IngestResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().notificationUniqueCode()).isNotNull();
        assertThat(response.getBody().deliveryStatus()).isEqualTo("pending");
    }

    @Test
    void ingest_withoutMatchingSubscription_returns202() {
        var request = new IngestPlatformEventRequest("EVT-TEST-2", "credit_cashback",
                "Cashback $5", clientCode);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/platform-events", request, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void ingest_duplicateEventId_returns409() {
        var request = new IngestPlatformEventRequest("EVT-DUP-1", "credit_transfer",
                "Transfer $50", clientCode);

        restTemplate.postForEntity("/platform-events", request, String.class);

        ResponseEntity<String> second = restTemplate.postForEntity(
                "/platform-events", request, String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void ingest_unknownClient_returns404() {
        var request = new IngestPlatformEventRequest("EVT-UNKNOWN", "credit_transfer",
                "Transfer $50", "nonexistent-client");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/platform-events", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void ingest_invalidEventType_returns400() {
        var request = new IngestPlatformEventRequest("EVT-BAD-TYPE", "not_a_real_event",
                "Some payload", clientCode);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/platform-events", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
