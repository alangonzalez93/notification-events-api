package com.cobre.notifications.infrastructure.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

public record CreateSubscriptionRequest(
        @NotBlank @Pattern(regexp = "https://.*", message = "webhookUrl must start with https://")
        String webhookUrl,
        @NotBlank String authHeaderName,
        @NotBlank String authHeaderValue,
        @NotEmpty Set<String> eventTypes
) {}
