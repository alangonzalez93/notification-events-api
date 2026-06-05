package com.cobre.notifications.domain.model;

import java.time.OffsetDateTime;

public record Client(
        Long id,
        String uniqueCode,
        String name,
        String email,
        Boolean deleted,
        OffsetDateTime createdDate,
        OffsetDateTime lastModifiedDate
) {}
