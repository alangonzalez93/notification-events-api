package com.cobre.notifications.infrastructure.persistence.entity;

import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.EventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@SQLRestriction("deleted = false")
@Table(name = "notification_events", indexes = {
        @Index(name = "idx_ne_dispatch", columnList = "delivery_status, next_retry_at"),
        @Index(name = "idx_ne_query",    columnList = "client_id, delivery_status, created_date"),
        @Index(name = "idx_ne_deleted",  columnList = "deleted")
})
public class NotificationEventJpaEntity extends BaseEntity {

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientJpaEntity client;

    // @NotFound(IGNORE): returns null instead of throwing EntityNotFoundException when the
    // subscription is soft-deleted (filtered by @SQLRestriction). Required because the FK
    // is always physically present — only @SQLRestriction makes the row invisible to Hibernate.
    @ManyToOne(fetch = FetchType.LAZY)
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "subscription_id", nullable = false)
    private SubscriptionJpaEntity subscription;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}
