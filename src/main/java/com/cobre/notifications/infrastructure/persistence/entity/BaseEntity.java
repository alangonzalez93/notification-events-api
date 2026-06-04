package com.cobre.notifications.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unique_code", nullable = false, unique = true, length = 36, updatable = false)
    private String uniqueCode;

    @Column(nullable = false)
    private Boolean deleted = false;

    @Column(name = "created_date", nullable = false, updatable = false)
    private OffsetDateTime createdDate;

    @Column(name = "last_modified_date", nullable = false)
    private OffsetDateTime lastModifiedDate;

    @PrePersist
    void prePersist() {
        if (uniqueCode == null) {
            uniqueCode = UUID.randomUUID().toString();
        }
        createdDate = OffsetDateTime.now(ZoneOffset.UTC);
        lastModifiedDate = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @PreUpdate
    void preUpdate() {
        lastModifiedDate = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
