package com.mohan.orderflow.event;

import com.mohan.orderflow.domain.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Integration event published to the {@code orders.events} topic.
 * The Kafka record key is the order id, so all events of one order land on the same
 * partition and are consumed in order.
 */
public record OrderEvent(
        UUID eventId,
        String eventType,
        UUID orderId,
        String customerId,
        BigDecimal totalAmount,
        List<Line> items,
        String reason,
        Instant occurredAt
) {

    public record Line(String sku, int quantity) {
    }

    public static OrderEvent of(OrderEventType type, Order order, String reason) {
        return new OrderEvent(
                UUID.randomUUID(),
                type.value(),
                order.getId(),
                order.getCustomerId(),
                order.getTotalAmount(),
                order.getItems().stream().map(i -> new Line(i.getSku(), i.getQuantity())).toList(),
                reason,
                Instant.now());
    }
}
