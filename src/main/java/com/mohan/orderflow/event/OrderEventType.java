package com.mohan.orderflow.event;

public enum OrderEventType {
    ORDER_CREATED("OrderCreated"),
    ORDER_CANCELLED("OrderCancelled");

    private final String value;

    OrderEventType(String value) {
        this.value = value;
    }

    /** Name used on the wire (Kafka header + payload). */
    public String value() {
        return value;
    }
}
