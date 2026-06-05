package com.cobre.notifications.domain.port.out;


public interface WebhookPort {
    WebhookResult post(String url, String authHeaderName, String authHeaderValue,
                       String idempotencyKey, String clientUniqueCode, String payload);

    record WebhookResult(boolean success, int httpStatus, String errorMessage) {}
}
