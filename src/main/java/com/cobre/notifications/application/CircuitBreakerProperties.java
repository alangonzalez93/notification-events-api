package com.cobre.notifications.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notifications.circuitbreaker")
public record CircuitBreakerProperties(
        int slidingWindowSize,
        int minimumNumberOfCalls,
        float failureRateThreshold,
        long waitDurationSeconds,
        int permittedCallsInHalfOpen
) {}
