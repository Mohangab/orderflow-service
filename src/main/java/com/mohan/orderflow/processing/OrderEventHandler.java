package com.mohan.orderflow.processing;

import com.mohan.orderflow.domain.Order;
import com.mohan.orderflow.domain.OrderStatus;
import com.mohan.orderflow.domain.ProcessedEvent;
import com.mohan.orderflow.event.OrderEvent;
import com.mohan.orderflow.event.OrderEventType;
import com.mohan.orderflow.repository.OrderRepository;
import com.mohan.orderflow.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent handler for order events. The processed-event marker and the order status
 * change are committed in one transaction, so a redelivered event is detected and skipped.
 */
@Service
public class OrderEventHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderEventHandler.class);

    private final ProcessedEventRepository processedEvents;
    private final OrderRepository orders;
    private final InventoryPaymentSimulator simulator;

    public OrderEventHandler(ProcessedEventRepository processedEvents,
                             OrderRepository orders,
                             InventoryPaymentSimulator simulator) {
        this.processedEvents = processedEvents;
        this.orders = orders;
        this.simulator = simulator;
    }

    /**
     * @return {@code true} if the event was handled, {@code false} if it was a duplicate
     */
    @Transactional
    public boolean handle(OrderEvent event) {
        if (processedEvents.existsById(event.eventId())) {
            log.info("Skipping duplicate event {} ({})", event.eventId(), event.eventType());
            return false;
        }
        // Insert the marker first: a concurrent duplicate fails on the primary key and rolls back.
        processedEvents.saveAndFlush(new ProcessedEvent(event.eventId(), event.eventType()));

        if (OrderEventType.ORDER_CREATED.value().equals(event.eventType())) {
            processCreated(event);
        } else if (OrderEventType.ORDER_CANCELLED.value().equals(event.eventType())) {
            log.info("Order {} cancelled: releasing simulated stock reservation and refund", event.orderId());
        } else {
            log.warn("Ignoring unknown event type {} for order {}", event.eventType(), event.orderId());
        }
        return true;
    }

    private void processCreated(OrderEvent event) {
        Order order = orders.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.warn("Order {} referenced by event {} does not exist", event.orderId(), event.eventId());
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            // e.g. customer cancelled before processing caught up
            log.info("Order {} is already {}; nothing to process", order.getId(), order.getStatus());
            return;
        }
        ProcessingDecision decision = simulator.evaluate(event);
        if (decision.approved()) {
            order.confirm();
            log.info("Order {} CONFIRMED", order.getId());
        } else {
            order.reject(decision.reason());
            log.info("Order {} REJECTED: {}", order.getId(), decision.reason());
        }
    }
}
