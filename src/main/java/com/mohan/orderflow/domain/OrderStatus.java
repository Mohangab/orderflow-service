package com.mohan.orderflow.domain;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED;

    public boolean isFinal() {
        return this == REJECTED || this == CANCELLED;
    }
}
