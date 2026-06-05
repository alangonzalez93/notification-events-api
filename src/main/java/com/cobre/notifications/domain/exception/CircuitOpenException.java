package com.cobre.notifications.domain.exception;

public class CircuitOpenException extends RuntimeException {

    private final long waitDurationSeconds;

    public CircuitOpenException(long waitDurationSeconds) {
        super("Circuit breaker open");
        this.waitDurationSeconds = waitDurationSeconds;
    }

    public long waitDurationSeconds() {
        return waitDurationSeconds;
    }
}
