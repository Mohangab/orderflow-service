package com.mohan.orderflow.service;

import com.mohan.orderflow.api.dto.CreateOrderRequest;
import com.mohan.orderflow.api.dto.OrderResponse;
import com.mohan.orderflow.api.dto.PageResponse;
import com.mohan.orderflow.domain.Order;
import com.mohan.orderflow.domain.OrderItem;
import com.mohan.orderflow.domain.OrderStatus;
import com.mohan.orderflow.event.OrderEventType;
import com.mohan.orderflow.outbox.OutboxWriter;
import com.mohan.orderflow.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OutboxWriter outboxWriter;

    public OrderService(OrderRepository orderRepository, OutboxWriter outboxWriter) {
        this.orderRepository = orderRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        List<OrderItem> items = request.items().stream()
                .map(i -> new OrderItem(i.sku(), i.quantity(), i.unitPrice()))
                .toList();
        Order order = orderRepository.save(Order.create(request.customerId(), items));
        outboxWriter.append(OrderEventType.ORDER_CREATED, order, null);
        log.info("Created order {} for customer {} (total {})",
                order.getId(), order.getCustomerId(), order.getTotalAmount());
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID id) {
        return OrderResponse.from(findOrder(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listOrders(String customerId, OrderStatus status, Pageable pageable) {
        Page<Order> page;
        if (customerId != null && status != null) {
            page = orderRepository.findByCustomerIdAndStatus(customerId, status, pageable);
        } else if (customerId != null) {
            page = orderRepository.findByCustomerId(customerId, pageable);
        } else if (status != null) {
            page = orderRepository.findByStatus(status, pageable);
        } else {
            page = orderRepository.findAll(pageable);
        }
        return PageResponse.from(page.map(OrderResponse::from));
    }

    @Transactional
    public OrderResponse cancelOrder(UUID id, String reason) {
        Order order = findOrder(id);
        String cancelReason = reason == null || reason.isBlank() ? "Cancelled by customer" : reason;
        order.cancel(cancelReason);
        outboxWriter.append(OrderEventType.ORDER_CANCELLED, order, cancelReason);
        log.info("Cancelled order {}", id);
        return OrderResponse.from(order);
    }

    private Order findOrder(UUID id) {
        return orderRepository.findWithItemsById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }
}
