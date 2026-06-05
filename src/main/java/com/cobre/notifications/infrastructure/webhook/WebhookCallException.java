package com.cobre.notifications.infrastructure.webhook;

public class WebhookCallException extends RuntimeException {
    public WebhookCallException(String message) {
        super(message);
    }
}
