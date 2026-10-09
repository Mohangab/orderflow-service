package com.mohan.orderflow.outbox;

import com.mohan.orderflow.config.AppProperties;
import com.mohan.orderflow.domain.OutboxEvent;
import com.mohan.orderflow.repository.OutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Kafka("orders.events", 1, "group"),
                new AppProperties.Outbox(Duration.ofSeconds(1), Duration.ofSeconds(1)));
        relay = new OutboxRelay(repository, kafkaTemplate, props);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesPendingEventsKeyedByOrderIdAndMarksThem() {
        UUID orderId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), orderId, "OrderCreated", "{}");
        when(repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        assertThat(relay.relayPendingEvents()).isEqualTo(1);

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();
        assertThat(record.topic()).isEqualTo("orders.events");
        assertThat(record.key()).isEqualTo(orderId.toString());
        assertThat(new String(record.headers().lastHeader(OutboxRelay.HEADER_EVENT_TYPE).value(),
                StandardCharsets.UTF_8)).isEqualTo("OrderCreated");
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void stopsAtFirstFailureAndLeavesRemainingEventsPending() {
        OutboxEvent first = new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), "OrderCreated", "{}");
        OutboxEvent second = new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), "OrderCreated", "{}");
        when(repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThat(relay.relayPendingEvents()).isZero();

        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
        assertThat(first.getPublishedAt()).isNull();
        assertThat(second.getPublishedAt()).isNull();
    }
}
