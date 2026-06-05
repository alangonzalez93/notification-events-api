package com.cobre.notifications.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notifications.dispatcher")
public record DispatcherProperties(long delayMs, int batchSize, int processingTimeoutMinutes) {}
