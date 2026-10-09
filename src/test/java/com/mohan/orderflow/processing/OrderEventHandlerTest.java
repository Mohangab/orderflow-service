package com.mohan.orderflow.processing;

import com.mohan.orderflow.TestOrders;
import com.mohan.orderflow.domain.Order;
import com.mohan.orderflow.domain.OrderStatus;
import com.mohan.orderflow.domain.ProcessedEvent;
import com.mohan.orderflow.event.OrderEvent;
import com.mohan.orderflow.event.OrderEventType;
import com.mohan.orderflow.repository.OrderRepository;
import com.mohan.orderflow.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventHandlerTest {

    @Mock
    private ProcessedEventRepository processedEvents;

    @Mock
    private OrderRepository orders;

    @Mock
    private InventoryPaymentSimulator simulator;

    @InjectMocks
    private OrderEventHandler handler;

    @Test
    void confirmsPendingOrderWhenChecksPass() {
        Order order = TestOrders.pendingOrder();
        OrderEvent event = OrderEvent.of(OrderEventType.ORDER_CREATED, order, null);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(simulator.evaluate(event)).thenReturn(ProcessingDecision.approve());

        assertThat(handler.handle(event)).isTrue();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(processedEvents).saveAndFlush(any(ProcessedEvent.class));
    }

    @Test
    void rejectsPendingOrderWithReason() {
        Order order = TestOrders.pendingOrder();
        OrderEvent event = OrderEvent.of(OrderEventType.ORDER_CREATED, order, null);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(simulator.evaluate(event)).thenReturn(ProcessingDecision.reject("Payment: declined"));

        handler.handle(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getStatusReason()).isEqualTo("Payment: declined");
    }

    @Test
    void skipsDuplicateEvent() {
        Order order = TestOrders.pendingOrder();
        OrderEvent event = OrderEvent.of(OrderEventType.ORDER_CREATED, order, null);
        when(processedEvents.existsById(event.eventId())).thenReturn(true);

        assertThat(handler.handle(event)).isFalse();

        verify(processedEvents, never()).saveAndFlush(any());
        verifyNoInteractions(orders, simulator);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void doesNotReprocessOrderCancelledBeforeProcessing() {
        Order order = TestOrders.pendingOrder();
        OrderEvent event = OrderEvent.of(OrderEventType.ORDER_CREATED, order, null);
        order.cancel("changed mind");
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        handler.handle(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verifyNoInteractions(simulator);
    }

    @Test
    void cancelledEventIsRecordedButDoesNotTouchOrder() {
        Order order = TestOrders.pendingOrder();
        OrderEvent event = OrderEvent.of(OrderEventType.ORDER_CANCELLED, order, "bye");

        assertThat(handler.handle(event)).isTrue();

        verify(processedEvents).saveAndFlush(any(ProcessedEvent.class));
        verifyNoInteractions(orders, simulator);
    }
}
