package com.cobre.notifications;

import com.cobre.notifications.infrastructure.config.AsyncProperties;
import com.cobre.notifications.application.CircuitBreakerProperties;
import com.cobre.notifications.infrastructure.config.DispatcherProperties;
import com.cobre.notifications.application.RetryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AsyncProperties.class, CircuitBreakerProperties.class,
        DispatcherProperties.class, RetryProperties.class})
public class NotificationEventsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationEventsApiApplication.class, args);
    }
}
