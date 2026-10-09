package com.mohan.orderflow.service;

import com.mohan.orderflow.TestOrders;
import com.mohan.orderflow.api.dto.CreateOrderRequest;
import com.mohan.orderflow.api.dto.OrderItemRequest;
import com.mohan.orderflow.api.dto.OrderResponse;
import com.mohan.orderflow.domain.IllegalOrderStateException;
import com.mohan.orderflow.domain.Order;
import com.mohan.orderflow.domain.OrderStatus;
import com.mohan.orderflow.event.OrderEventType;
import com.mohan.orderflow.outbox.OutboxWriter;
import com.mohan.orderflow.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxWriter outboxWriter;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrderPersistsPendingOrderAndWritesOutboxEvent() {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateOrderRequest request = new CreateOrderRequest("customer-7",
                List.of(new OrderItemRequest("SKU-1", 3, new BigDecimal("2.00"))));

        OrderResponse response = orderService.createOrder(request);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.totalAmount()).isEqualByComparingTo("6.00");
        assertThat(response.items()).singleElement().satisfies(i -> assertThat(i.sku()).isEqualTo("SKU-1"));
        verify(outboxWriter).append(eq(OrderEventType.ORDER_CREATED), any(Order.class), eq(null));
    }

    @Test
    void getOrderThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findWithItemsById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(id))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void cancelOrderUsesDefaultReasonAndWritesOutboxEvent() {
        Order order = TestOrders.pendingOrder();
        when(orderRepository.findWithItemsById(order.getId())).thenReturn(Optional.of(order));

        OrderResponse response = orderService.cancelOrder(order.getId(), "  ");

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.statusReason()).isEqualTo("Cancelled by customer");
        verify(outboxWriter).append(OrderEventType.ORDER_CANCELLED, order, "Cancelled by customer");
    }

    @Test
    void cancelRejectedOrderFailsWithoutWritingEvent() {
        Order order = TestOrders.pendingOrder();
        order.reject("payment declined");
        when(orderRepository.findWithItemsById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(order.getId(), null))
                .isInstanceOf(IllegalOrderStateException.class);
        verify(outboxWriter, never()).append(any(), any(), any());
    }
}
