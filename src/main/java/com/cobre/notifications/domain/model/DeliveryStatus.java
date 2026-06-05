package com.cobre.notifications.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DeliveryStatus {
    PENDING, PROCESSING, DELIVERED, FAILED;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static DeliveryStatus fromJson(String value) {
        return valueOf(value.toUpperCase());
    }
}
