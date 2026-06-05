package com.cobre.notifications.infrastructure.webhook;

import com.cobre.notifications.application.CircuitBreakerProperties;
import com.cobre.notifications.domain.exception.CircuitOpenException;
import com.cobre.notifications.domain.port.out.WebhookPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class WebhookAdapter implements WebhookPort {

    private final RestClient webhookRestClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final CircuitBreakerProperties cbProperties;

    @Override
    public WebhookResult post(String url, String authHeaderName, String authHeaderValue,
                               String idempotencyKey, String clientUniqueCode, String payload) {
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker(clientUniqueCode);
        try {
            return breaker.executeCallable(
                    () -> doPost(url, authHeaderName, authHeaderValue, idempotencyKey, payload));
        } catch (CallNotPermittedException e) {
            throw new CircuitOpenException(cbProperties.waitDurationSeconds());
        } catch (WebhookCallException e) {
            throw e;
        } catch (Exception e) {
            throw new WebhookCallException("Unexpected error: " + e.getMessage());
        }
    }

    private WebhookResult doPost(String url, String authHeaderName, String authHeaderValue,
                                  String idempotencyKey, String payload) {
        try {
            var response = webhookRestClient.post()
                    .uri(url)
                    .header(authHeaderName, authHeaderValue)
                    .header("X-Idempotency-Key", idempotencyKey)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            return new WebhookResult(true, response.getStatusCode().value(), null);

        } catch (HttpStatusCodeException e) {
            throw new WebhookCallException("HTTP " + e.getStatusCode().value() + ": " + e.getMessage());
        } catch (RestClientException e) {
            throw new WebhookCallException("Connection error: " + e.getMessage());
        }
    }
}
