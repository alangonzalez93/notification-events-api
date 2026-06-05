package com.cobre.notifications.infrastructure.persistence;

import org.testcontainers.containers.MySQLContainer;

public final class SharedMySQLContainer {

    public static final MySQLContainer<?> INSTANCE = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("notification_events_api_test")
            .withUsername("test")
            .withPassword("test")
            .withStartupTimeoutSeconds(300)
            .withConnectTimeoutSeconds(300)
            .withCommand("--innodb-buffer-pool-size=32M", "--skip-name-resolve",
                    "--character-set-server=utf8mb4");

    static {
        INSTANCE.start();
    }

    private SharedMySQLContainer() {}
}
