package com.cobre.notifications.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notifications.async")
public record AsyncProperties(int corePoolSize, int maxPoolSize, int queueCapacity) {}
