package com.cobre.notifications.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EventType {
    CREDIT_CARD_PAYMENT, DEBIT_CARD_WITHDRAWAL, CREDIT_TRANSFER,
    DEBIT_AUTOMATIC_PAYMENT, CREDIT_REFUND, DEBIT_TRANSFER,
    CREDIT_DEPOSIT, DEBIT_PURCHASE, CREDIT_CASHBACK, DEBIT_SUBSCRIPTION;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static EventType fromJson(String value) {
        return valueOf(value.toUpperCase());
    }
}
