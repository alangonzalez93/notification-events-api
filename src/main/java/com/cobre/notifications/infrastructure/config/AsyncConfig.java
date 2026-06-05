package com.cobre.notifications.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncConfig {

    private final AsyncProperties props;

    @Bean("webhookDeliveryExecutor")
    public Executor webhookDeliveryExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.corePoolSize());
        executor.setMaxPoolSize(props.maxPoolSize());
        executor.setQueueCapacity(props.queueCapacity());
        executor.setThreadNamePrefix("webhook-");
        // When both the thread pool and the queue are full, we log and discard rather than
        // using CallerRunsPolicy. Here's why:
        //
        // Normal flow: the queue (queueCapacity slots) acts as a buffer — workers pick up
        // tasks as they finish HTTP calls, so the scheduler thread only blocks on enqueue,
        // which is near-instant. Saturation requires ALL threads stuck near read-timeout
        // simultaneously, which is unlikely in practice.
        //
        // The risk of CallerRunsPolicy: if the scheduler thread ends up executing a delivery
        // task itself, it can be blocked for up to read-timeout ms. If this happens enough
        // times that the scheduler is blocked longer than ShedLock's lockAtMostFor window,
        // the lock expires and a second node starts a parallel dispatcher — exactly what
        // ShedLock exists to prevent.
        //
        // With discard: the notification stays PROCESSING and resetStuckProcessing() reclaims
        // it as PENDING on the next scheduler run. No delivery is lost, only delayed one cycle.
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("Webhook executor saturated (active={}, queued={}). " +
                         "Task discarded — notification will be retried in the next batch.",
                        pool.getActiveCount(), pool.getQueue().size()));
        executor.initialize();
        return executor;
    }
}
