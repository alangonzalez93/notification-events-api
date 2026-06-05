package com.cobre.notifications.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IngestPlatformEventRequest(
        @NotBlank String eventId,
        @NotNull String eventType,
        @NotBlank String payload,
        @NotBlank String clientUniqueCode
) {}
