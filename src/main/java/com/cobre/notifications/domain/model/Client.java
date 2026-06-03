package com.cobre.notifications.domain.model;

import java.time.LocalDateTime;

public record Client(
        Long id,
        String uniqueCode,
        String name,
        String email,
        Boolean deleted,
        LocalDateTime createdDate,
        LocalDateTime lastModifiedDate
) {}
