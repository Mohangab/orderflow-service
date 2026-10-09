package com.mohan.orderflow.domain;

import java.util.UUID;

public class IllegalOrderStateException extends RuntimeException {

    public IllegalOrderStateException(UUID orderId, OrderStatus current, OrderStatus target) {
        super("Order %s cannot move from %s to %s".formatted(orderId, current, target));
    }
}
