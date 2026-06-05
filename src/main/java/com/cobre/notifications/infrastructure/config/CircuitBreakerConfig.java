package com.cobre.notifications.infrastructure.config;

import com.cobre.notifications.application.CircuitBreakerProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(CircuitBreakerProperties.class)
@RequiredArgsConstructor
public class CircuitBreakerConfig {

    private final CircuitBreakerProperties props;

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig config =
                io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .slidingWindowSize(props.slidingWindowSize())
                        .minimumNumberOfCalls(props.minimumNumberOfCalls())
                        .failureRateThreshold(props.failureRateThreshold())
                        .waitDurationInOpenState(Duration.ofSeconds(props.waitDurationSeconds()))
                        .permittedNumberOfCallsInHalfOpenState(props.permittedCallsInHalfOpen())
                        .build();
        return CircuitBreakerRegistry.of(config);
    }
}
