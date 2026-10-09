package com.mohan.orderflow.domain;

import com.mohan.orderflow.TestOrders;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void createComputesTotalAndStartsPending() {
        Order order = TestOrders.pendingOrder();

        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("25.50");
        assertThat(order.getItems()).hasSize(2);
    }

    @Test
    void createRequiresItems() {
        assertThatThrownBy(() -> Order.create("c", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pendingOrderCanBeConfirmedThenCancelled() {
        Order order = TestOrders.pendingOrder();

        order.confirm();
        order.cancel("no longer needed");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getStatusReason()).isEqualTo("no longer needed");
    }

    @Test
    void rejectedOrderCannotBeCancelled() {
        Order order = TestOrders.pendingOrder();
        order.reject("out of stock");

        assertThatThrownBy(() -> order.cancel("too late"))
                .isInstanceOf(IllegalOrderStateException.class)
                .hasMessageContaining("REJECTED to CANCELLED");
    }

    @Test
    void confirmedOrderCannotBeConfirmedAgain() {
        Order order = TestOrders.pendingOrder();
        order.confirm();

        assertThatThrownBy(order::confirm).isInstanceOf(IllegalOrderStateException.class);
    }

    @Test
    void lineTotalMultipliesQuantity() {
        assertThat(new OrderItem("X", 3, new BigDecimal("1.25")).lineTotal()).isEqualByComparingTo("3.75");
    }
}
