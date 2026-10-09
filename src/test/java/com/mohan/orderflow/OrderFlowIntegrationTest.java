package com.mohan.orderflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mohan.orderflow.api.dto.CancelOrderRequest;
import com.mohan.orderflow.api.dto.CreateOrderRequest;
import com.mohan.orderflow.api.dto.OrderItemRequest;
import com.mohan.orderflow.api.dto.OrderResponse;
import com.mohan.orderflow.domain.OrderStatus;
import com.mohan.orderflow.domain.OutboxEvent;
import com.mohan.orderflow.repository.OrderRepository;
import com.mohan.orderflow.repository.OutboxEventRepository;
import com.mohan.orderflow.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end flow: REST -> DB + outbox (H2 via Flyway) -> relay -> embedded Kafka (KRaft)
 * -> idempotent consumer -> order status update.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = {"orders.events", "orders.events.DLT"})
@ActiveProfiles("test")
class OrderFlowIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void validOrderIsConfirmedAsynchronously() {
        OrderResponse created = create("customer-it-1", new OrderItemRequest("SKU-A", 2, new BigDecimal("19.99")));
        assertThat(created.status()).isEqualTo(OrderStatus.PENDING);

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(fetch(created.id()).status()).isEqualTo(OrderStatus.CONFIRMED));
        assertThat(outboxRepository.findById(eventIdFor(created.id())).orElseThrow().getPublishedAt()).isNotNull();
    }

    @Test
    void outOfStockOrderIsRejected() {
        OrderResponse created = create("customer-it-2",
                new OrderItemRequest("SKU-OUT-OF-STOCK", 1, new BigDecimal("5.00")));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            OrderResponse order = fetch(created.id());
            assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
            assertThat(order.statusReason()).contains("out of stock");
        });
    }

    @Test
    void orderAbovePaymentLimitIsRejected() {
        OrderResponse created = create("customer-it-3", new OrderItemRequest("SKU-B", 10, new BigDecimal("150.00")));

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(fetch(created.id()).statusReason()).startsWith("Payment"));
    }

    @Test
    void confirmedOrderCanBeCancelledAndCancelEventIsConsumed() {
        OrderResponse created = create("customer-it-4", new OrderItemRequest("SKU-C", 1, new BigDecimal("9.00")));
        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(fetch(created.id()).status()).isEqualTo(OrderStatus.CONFIRMED));

        ResponseEntity<OrderResponse> cancelled = rest.postForEntity(
                "/api/v1/orders/{id}/cancel", new CancelOrderRequest("Found a better deal"), OrderResponse.class, created.id());

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getBody().statusReason()).isEqualTo("Found a better deal");
        List<OutboxEvent> events = outboxRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(created.id())).toList();
        assertThat(events).extracting(OutboxEvent::getEventType).containsExactlyInAnyOrder("OrderCreated", "OrderCancelled");
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(events)
                .allSatisfy(e -> assertThat(processedEventRepository.existsById(e.getId())).isTrue()));
    }

    @Test
    void redeliveredEventIsProcessedOnlyOnce() throws Exception {
        OrderResponse created = create("customer-it-5", new OrderItemRequest("SKU-D", 1, new BigDecimal("3.00")));
        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(fetch(created.id()).status()).isEqualTo(OrderStatus.CONFIRMED));
        long versionAfterFirstDelivery = orderRepository.findById(created.id()).orElseThrow().getVersion();
        long processedBefore = processedEventRepository.count();

        // Simulate an at-least-once redelivery of the very same OrderCreated event.
        OutboxEvent original = outboxRepository.findById(eventIdFor(created.id())).orElseThrow();
        kafkaTemplate.send("orders.events", created.id().toString(), original.getPayload()).get();
        // A fresh marker event sent afterwards on the same partition proves the duplicate was consumed.
        OrderResponse marker = create("customer-it-5b", new OrderItemRequest("SKU-E", 1, new BigDecimal("1.00")));
        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(fetch(marker.id()).status()).isEqualTo(OrderStatus.CONFIRMED));

        assertThat(orderRepository.findById(created.id()).orElseThrow().getVersion()).isEqualTo(versionAfterFirstDelivery);
        assertThat(processedEventRepository.count()).isEqualTo(processedBefore + 1);
        assertThat(objectMapper.readTree(original.getPayload()).get("eventType").asText()).isEqualTo("OrderCreated");
    }

    @Test
    void listSupportsFilteringAndPagination() {
        create("customer-it-list", new OrderItemRequest("SKU-L1", 1, new BigDecimal("1.00")));
        create("customer-it-list", new OrderItemRequest("SKU-L2", 1, new BigDecimal("2.00")));
        create("customer-it-list", new OrderItemRequest("SKU-L3", 1, new BigDecimal("3.00")));

        ResponseEntity<String> page = rest.getForEntity(
                "/api/v1/orders?customerId=customer-it-list&page=0&size=2", String.class);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("\"totalElements\":3", "\"totalPages\":2", "\"size\":2");
    }

    @Test
    void actuatorHealthAndOpenApiAreExposed() {
        assertThat(rest.getForEntity("/actuator/health", String.class).getBody()).contains("\"status\":\"UP\"");
        assertThat(rest.getForEntity("/v3/api-docs", String.class).getBody()).contains("/api/v1/orders");
    }

    private OrderResponse create(String customerId, OrderItemRequest item) {
        ResponseEntity<OrderResponse> response = rest.postForEntity("/api/v1/orders",
                new CreateOrderRequest(customerId, List.of(item)), OrderResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private OrderResponse fetch(UUID id) {
        return rest.getForObject("/api/v1/orders/{id}", OrderResponse.class, id);
    }

    private UUID eventIdFor(UUID orderId) {
        return outboxRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(orderId) && e.getEventType().equals("OrderCreated"))
                .map(OutboxEvent::getId)
                .findFirst()
                .orElseThrow();
    }
}
