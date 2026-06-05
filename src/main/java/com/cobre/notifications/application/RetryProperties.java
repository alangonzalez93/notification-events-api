package com.cobre.notifications.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notifications.retry")
public record RetryProperties(int maxAttempts, long baseDelaySeconds, long maxDelaySeconds, double jitterFactor) {}
