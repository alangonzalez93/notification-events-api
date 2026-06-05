CREATE TABLE subscriptions (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    unique_code         VARCHAR(36)  NOT NULL,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_date        DATETIME     NOT NULL,
    last_modified_date  DATETIME     NOT NULL,
    client_id           BIGINT       NOT NULL,
    webhook_url         VARCHAR(500) NOT NULL,
    auth_header_name    VARCHAR(100) NOT NULL,
    auth_header_value   VARCHAR(500) NOT NULL,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,

    PRIMARY KEY (id),
    CONSTRAINT uq_subscriptions_unique_code UNIQUE (unique_code),
    CONSTRAINT fk_subscriptions_client FOREIGN KEY (client_id) REFERENCES clients(id)
);

CREATE INDEX idx_subscriptions_client_id ON subscriptions (client_id);
CREATE INDEX idx_subscriptions_deleted   ON subscriptions (deleted);
CREATE INDEX idx_subscriptions_active    ON subscriptions (active);

CREATE TABLE subscription_event_types (
    subscription_id BIGINT      NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    PRIMARY KEY (subscription_id, event_type),
    CONSTRAINT fk_set_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions(id)
);

CREATE TABLE notification_events (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    unique_code         VARCHAR(36)  NOT NULL,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_date        DATETIME     NOT NULL,
    last_modified_date  DATETIME     NOT NULL,
    event_id            VARCHAR(100) NOT NULL,
    event_type          VARCHAR(50)  NOT NULL,
    payload             TEXT         NOT NULL,
    client_id           BIGINT       NOT NULL,
    subscription_id     BIGINT       NOT NULL,
    delivery_status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    delivered_at        DATETIME,
    retry_count         INT          NOT NULL DEFAULT 0,
    next_retry_at       DATETIME,
    last_error          TEXT,
    version             BIGINT       NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uq_notification_events_unique_code  UNIQUE (unique_code),
    CONSTRAINT uq_notification_events_event_client UNIQUE (event_id, client_id),
    CONSTRAINT fk_ne_client       FOREIGN KEY (client_id)       REFERENCES clients(id),
    CONSTRAINT fk_ne_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions(id)
);

CREATE INDEX idx_ne_deleted  ON notification_events (deleted);
CREATE INDEX idx_ne_dispatch ON notification_events (delivery_status, next_retry_at);
CREATE INDEX idx_ne_query    ON notification_events (client_id, delivery_status, created_date);

-- ShedLock uses DB clock as single source of truth for lock expiry across nodes
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
