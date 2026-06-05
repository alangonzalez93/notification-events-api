package com.cobre.notifications.infrastructure.persistence.entity;

import com.cobre.notifications.domain.model.EventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@SQLRestriction("deleted = false")
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_subscriptions_client_id", columnList = "client_id"),
        @Index(name = "idx_subscriptions_active",    columnList = "active"),
        @Index(name = "idx_subscriptions_deleted",   columnList = "deleted")
})
public class SubscriptionJpaEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientJpaEntity client;

    @Column(name = "webhook_url", nullable = false, length = 500)
    private String webhookUrl;

    @Column(name = "auth_header_name", nullable = false, length = 100)
    private String authHeaderName;

    @Column(name = "auth_header_value", nullable = false, length = 500)
    private String authHeaderValue;

    @Column(nullable = false)
    private Boolean active = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "subscription_event_types",
            joinColumns = @JoinColumn(name = "subscription_id"))
    @Column(name = "event_type", length = 50)
    @Enumerated(EnumType.STRING)
    private Set<EventType> eventTypes = new HashSet<>();
}
