package com.cobre.notifications.infrastructure.persistence;

import com.cobre.notifications.domain.model.Client;
import com.cobre.notifications.domain.port.out.ClientRepository;
import com.cobre.notifications.infrastructure.web.dto.request.CreateSubscriptionRequest;
import com.cobre.notifications.infrastructure.web.dto.request.IngestPlatformEventRequest;
import com.cobre.notifications.infrastructure.web.dto.response.IngestResponse;
import com.cobre.notifications.infrastructure.web.dto.response.NotificationEventDetailResponse;
import com.cobre.notifications.infrastructure.web.dto.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEventControllerTest extends WebIntegrationTestBase {

    @Autowired
    private ClientRepository clientRepository;

    private String clientCode;
    private String notificationCode;

    @BeforeEach
    void setUp() {
        Client client = clientRepository.save(
                new Client(null, null, "Query Test", "query-" + System.nanoTime() + "@test.com",
                        false, null, null));
        clientCode = client.uniqueCode();

        restTemplate.postForEntity("/clients/" + clientCode + "/subscriptions",
                new CreateSubscriptionRequest("https://webhook.example.com", "X-Secret", "val",
                        Set.of("credit_transfer")), String.class);

        ResponseEntity<IngestResponse> ingested = restTemplate.postForEntity(
                "/platform-events",
                new IngestPlatformEventRequest("EVT-QUERY-1", "credit_transfer",
                        "Transfer $100", clientCode),
                IngestResponse.class);
        notificationCode = ingested.getBody().notificationUniqueCode();
    }

    @Test
    void list_returnsNotificationsForClient() {
        ResponseEntity<PageResponse> response = restTemplate.exchange(
                "/notification_events?clientUniqueCode=" + clientCode,
                HttpMethod.GET, null, PageResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().content()).isNotEmpty();
    }

    @Test
    void list_withSizeExceeding100_returns400() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/notification_events?clientUniqueCode=" + clientCode + "&size=200",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getOne_returnsDetail() {
        ResponseEntity<NotificationEventDetailResponse> response = restTemplate.getForEntity(
                "/notification_events/" + notificationCode + "?clientUniqueCode=" + clientCode,
                NotificationEventDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().uniqueCode()).isEqualTo(notificationCode);
        assertThat(response.getBody().deliveryStatus()).isEqualTo("pending");
    }

    @Test
    void getOne_wrongClient_returns403() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/notification_events/" + notificationCode + "?clientUniqueCode=other-client",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getOne_notFound_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/notification_events/nonexistent?clientUniqueCode=" + clientCode,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
