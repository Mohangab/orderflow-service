package com.mohan.orderflow.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "status_reason")
    private String statusReason;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
        // JPA
    }

    public static Order create(String customerId, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one item");
        }
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.customerId = customerId;
        order.status = OrderStatus.PENDING;
        items.forEach(order::addItem);
        order.totalAmount = items.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        Instant now = Instant.now();
        order.createdAt = now;
        order.updatedAt = now;
        return order;
    }

    private void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    /** Called by the processing pipeline once stock and payment checks pass. */
    public void confirm() {
        transition(OrderStatus.CONFIRMED, null, OrderStatus.PENDING);
    }

    /** Called by the processing pipeline when stock or payment checks fail. */
    public void reject(String reason) {
        transition(OrderStatus.REJECTED, reason, OrderStatus.PENDING);
    }

    /** Customer-initiated cancellation; allowed until the order reaches a final state. */
    public void cancel(String reason) {
        transition(OrderStatus.CANCELLED, reason, OrderStatus.PENDING, OrderStatus.CONFIRMED);
    }

    private void transition(OrderStatus target, String reason, OrderStatus... allowedFrom) {
        for (OrderStatus allowed : allowedFrom) {
            if (status == allowed) {
                this.status = target;
                this.statusReason = reason;
                this.updatedAt = Instant.now();
                return;
            }
        }
        throw new IllegalOrderStateException(id, status, target);
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
