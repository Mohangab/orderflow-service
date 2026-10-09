package com.mohan.orderflow.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mohan.orderflow.domain.Order;
import com.mohan.orderflow.domain.OutboxEvent;
import com.mohan.orderflow.event.OrderEvent;
import com.mohan.orderflow.event.OrderEventType;
import com.mohan.orderflow.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes integration events into the outbox table. Must run inside the caller's
 * transaction so the event is stored atomically with the order change.
 */
@Component
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OrderEvent append(OrderEventType type, Order order, String reason) {
        OrderEvent event = OrderEvent.of(type, order, reason);
        repository.save(new OutboxEvent(event.eventId(), order.getId(), event.eventType(), toJson(event)));
        return event;
    }

    private String toJson(OrderEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize event " + event.eventId(), e);
        }
    }
}
