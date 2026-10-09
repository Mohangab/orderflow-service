package com.mohan.orderflow;

import com.mohan.orderflow.domain.Order;
import com.mohan.orderflow.domain.OrderItem;

import java.math.BigDecimal;
import java.util.List;

public final class TestOrders {

    private TestOrders() {
    }

    public static Order pendingOrder() {
        return Order.create("customer-1", List.of(
                new OrderItem("SKU-A", 2, new BigDecimal("10.00")),
                new OrderItem("SKU-B", 1, new BigDecimal("5.50"))));
    }
}
